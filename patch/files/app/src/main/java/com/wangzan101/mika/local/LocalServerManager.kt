package com.wangzan101.mika.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 服务当前状态。 */
sealed interface LocalServerState {
    object Stopped : LocalServerState
    data class Starting(val attempt: Int) : LocalServerState
    /** 已经能应答了。 */
    data class Running(val port: Int) : LocalServerState
    /** 退了或起不来。 */
    data class Failed(val why: String) : LocalServerState
}

/**
 * 在本机跑一个 llama-server，让 Mika 用连 Ollama 一样的方式连它。
 *
 * 为什么是「起子进程」而不是写 JNI：
 * - llama.cpp 官方就发 android-arm64 预编译包，拿来即用，不用自己配 CMake/NDK 编它
 * - 它自带 OpenAI 兼容接口，Mika 现成的 `OpenAiCompatModel` 直接能连，
 *   工具调用由 llama.cpp 自己的 parser 处理 —— 省掉"让小模型吐结构化工具 JSON"这个最麻烦的环节
 * - Mika 本身就有跑原生二进制的基建（沙箱的 proot 就是这么起的）
 *
 * 两个安卓上的硬约束，不照做就跑不起来：
 * 1. **可执行文件必须是 `lib*.so` 的名字**打进 APK，才会被解到 `nativeLibraryDir`。
 *    安卓 10 起 app 数据目录里的文件不能 `execve`，只有这个目录能执行。
 * 2. **要设 `LD_LIBRARY_PATH`** 指向同一目录，否则 `libllama.so` 这些依赖 dlopen 不到。
 */
class LocalServerManager(private val app: Context) {

    @Volatile private var proc: Process? = null
    @Volatile private var port: Int = DEFAULT_PORT

    /** 可执行文件所在目录：APK 里 `lib/arm64-v8a/` 解出来的地方。 */
    private val nativeDir: File get() = File(app.applicationInfo.nativeLibraryDir)

    /** 启动器。打进 APK 时改名为 `libllamaserver.so`，但它是个可执行程序，不是动态库。 */
    private val exe: File get() = File(nativeDir, EXE_NAME)

    fun isRunning(): Boolean = proc?.isAlive == true

    /**
     * 起服务并等它就绪。
     *
     * 启动本身不给取消：**进程要在启动线程上起，且起来后必须记住 pid**，
     * 否则协程一取消就丢掉句柄、留下孤儿进程白占几个 G 内存。
     * （这个坑 Mika 自己的 `AndroidSandbox.launch` 也处理了，写法照它。）
     */
    suspend fun start(modelPath: String, mmprojPath: String?, nCtx: Int, nThreads: Int): LocalServerState =
        withContext(Dispatchers.IO) {
            if (isRunning() && probe(port)) return@withContext LocalServerState.Running(port)
            if (!exe.canExecute()) {
                return@withContext LocalServerState.Failed("找不到可执行程序：${exe.name}（设备可能不是 arm64）")
            }

            val usePort = pickPort()
            val cmd = buildList {
                add(exe.absolutePath)
                add("-m"); add(modelPath)
                mmprojPath?.let { add("--mmproj"); add(it) }
                add("--host"); add("127.0.0.1")
                add("--port"); add(usePort.toString())
                add("-c"); add(nCtx.toString())
                add("-t"); add(nThreads.toString())
                // 只开接口，不加载网页界面（省资源）
                add("--no-webui")
            }

            val started = try {
                ProcessBuilder(cmd).apply {
                    redirectErrorStream(true)
                    environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
                    environment()["HOME"] = app.filesDir.absolutePath
                }.start()
            } catch (e: Exception) {
                return@withContext LocalServerState.Failed("起不来：${e.message}")
            }
            proc = started
            port = usePort

            // 4B 视觉模型加载慢，给足时间；每 500ms 探一次
            repeat(HEALTH_ATTEMPTS) { i ->
                delay(HEALTH_INTERVAL_MS)
                if (started.isAlive.not()) {
                    return@withContext LocalServerState.Failed("进程起来就退了：" + readTail(started))
                }
                if (probe(usePort)) return@withContext LocalServerState.Running(usePort)
            }
            LocalServerState.Failed("等了 ${HEALTH_ATTEMPTS * HEALTH_INTERVAL_MS / 1000}s 还没就绪：" + readTail(started))
        }

    /** 停掉。先 SIGTERM 给体面退出的机会，再 SIGKILL。 */
    suspend fun stop() = withContext(Dispatchers.IO) {
        val p = proc ?: return@withContext
        proc = null
        try {
            p.destroy()
            // 给 3 秒；模型还在卸载时不强杀，避免留下坏的临时文件
            repeat(30) { if (!p.isAlive) return@repeat; delay(100) }
            if (p.isAlive) p.destroyForcibly()
        } catch (e: Exception) {
            // 已经死了，忽略
        }
    }

    /**
     * 开个子线程启动，不等它就绪。
     *
     * 给 App 启动时用：加载 4B 视觉模型要二三十秒，
     * 在主线程等会把 App 卡死。起完就完，成败由界面那边轮询得知。
     */
    fun startAsync(modelPath: String, mmprojPath: String?) {
        Thread {
            runCatching {
                runBlocking {
                    start(
                        modelPath = modelPath,
                        mmprojPath = mmprojPath,
                        nCtx = 8192,
                        nThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8),
                    )
                }
            }
        }.start()
    }

    /**
     * 同步版停止：给不能在协程里收尾的场合用（页面销毁、进程要退了）。
     * 只发 SIGTERM，不在这里等 —— 收尾路径不该阻塞。
     */
    fun stopNow() {
        val p = proc ?: return
        proc = null
        runCatching { p.destroy() }
    }

    /** 探一下能不能应答。 */
    private fun probe(port: Int): Boolean = try {
        http.newCall(Request.Builder().url("http://127.0.0.1:$port/health").build())
            .execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    /** 端口被占就往后找；只是尽量，真冲突了由 start 的失败路径兜住。 */
    private fun pickPort(): Int {
        for (p in DEFAULT_PORT until DEFAULT_PORT + 20) {
            if (!probe(p)) return p
        }
        return DEFAULT_PORT
    }

    /** 进程失败时把输出尾巴捞出来，不然只看到"退了"没法排查。 */
    private fun readTail(p: Process): String = try {
        val s = p.inputStream.bufferedReader().readText()
        s.takeLast(300).replace('\n', ' ')
    } catch (e: Exception) {
        ""
    }

    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()
    }

    companion object {
        /** 打进 APK 时的名字。原始文件名是 `llama-server`，为了被解出来改成了这个。 */
        const val EXE_NAME = "libllamaserver.so"
        const val DEFAULT_PORT = 8080
        private const val HEALTH_ATTEMPTS = 120      // 120 × 500ms = 60s
        private const val HEALTH_INTERVAL_MS = 500L
    }
}
