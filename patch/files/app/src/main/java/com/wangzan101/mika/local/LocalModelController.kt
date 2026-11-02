package com.wangzan101.mika.local

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 界面上要显示的一切。 */
data class LocalModelUi(
    val downloading: Boolean = false,
    val progress: DownloadProgress? = null,
    /** 当前选中的模型两个文件都在。 */
    val ready: Boolean = false,
    val server: LocalServerState = LocalServerState.Stopped,
    val message: String? = null,
    val importing: Boolean = false,
)

/**
 * 本机模型的下载、导入、启停，以及"用哪个模型"。
 *
 * 不做成 `ViewModel` + `viewModel()`：Mika 自己没引 `lifecycle-viewmodel-compose`，
 * 它是直接构造的（见 `MainActivity` 里的 `MemoryViewModel(...)`）。
 * 跟着它的做法走，就不用给工程加依赖 —— 少动一处 build.gradle，升级时少一处冲突。
 *
 * 生命周期由页面用 `DisposableEffect` 收尾（[close]）。
 */
class LocalModelController(app: Context) {

    private val ctx = app.applicationContext
    private val prefs = ctx.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private val store = LocalModelStore(File(ctx.getExternalFilesDir(null), "models"))
    private val downloader = LocalModelDownloader()
    private val server = LocalServerManager(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(LocalModelUi())
    val ui: StateFlow<LocalModelUi> = _ui.asStateFlow()

    private val _current = MutableStateFlow(loadChoice())
    val current: StateFlow<LocalModelCatalog.Model> = _current.asStateFlow()

    init { refresh() }

    /** 换模型。正在跑的服务要停掉 —— 换模型还占着旧权重没意义。 */
    fun select(model: LocalModelCatalog.Model) {
        if (model.id == _current.value.id) return
        scope.launch {
            server.stop()
            prefs.edit().putString(KEY_CHOICE, model.id).apply()
            _current.value = model
            _ui.value = LocalModelUi(server = LocalServerState.Stopped)
            refresh()
        }
    }

    private fun loadChoice(): LocalModelCatalog.Model {
        val saved = prefs.getString(KEY_CHOICE, null)?.let { LocalModelCatalog.byId(it) }
        // 没存过就按机身内存挑一个
        return saved ?: LocalModelCatalog.recommend(ramGb()) ?: LocalModelCatalog.DEFAULT
    }

    fun refresh() {
        _ui.value = _ui.value.copy(ready = store.isModelReady(_current.value))
    }

    /** App 内下载。支持断点续传，主源失败自动换备选源。 */
    fun download() {
        val m = _current.value
        scope.launch {
            _ui.value = _ui.value.copy(downloading = true, message = null)
            when (val r = downloader.download(store, m) { p ->
                _ui.value = _ui.value.copy(progress = p)
            }) {
                is DownloadResult.Ok -> _ui.value = _ui.value.copy(
                    downloading = false, ready = true, progress = null, message = "下载完成",
                )
                is DownloadResult.Failed -> _ui.value = _ui.value.copy(
                    downloading = false, progress = null,
                    message = r.message + if (r.retryable) "（已保留进度，可继续）" else "",
                )
            }
        }
    }

    /**
     * 导入外部下好的文件（[which]：0 主模型，1 视觉投影）。
     *
     * 几个 G 在 App 内下不一定顺，用户可能用别的工具下或在电脑上下好传过来。
     * 收进来先校验，对不上就不留 —— 免得下错文件到运行时才发现。
     */
    fun import(uri: Uri, which: Int) {
        val m = _current.value
        val file = m.files.getOrNull(which) ?: return
        scope.launch {
            _ui.value = _ui.value.copy(importing = true, message = null)
            val out = File(store.modelDir(), file.name)
            val copied = withContext(Dispatchers.IO) { copyIn(uri, out) }
            val ok = copied && withContext(Dispatchers.IO) { store.verify(out, file) }
            if (copied && !ok) withContext(Dispatchers.IO) { out.delete() }
            _ui.value = _ui.value.copy(
                importing = false,
                ready = store.isModelReady(m),
                message = when {
                    !copied -> "读不到这个文件"
                    !ok -> "文件对不上（不完整或不是这个文件），已丢弃"
                    else -> "导入成功"
                },
            )
        }
    }

    /**
     * 起本机服务。
     *
     * 走 [LocalServerService]（前台服务）而不是直接起子进程：
     * Android 12+ 会杀没有前台服务保护的后台子进程，且是捕获不到的 SIGKILL。
     * 界面这边只发个请求，真正的加载在后台做。
     */
    fun start() {
        _ui.value = _ui.value.copy(server = LocalServerState.Starting(1), message = null)
        LocalServerService.start(ctx)
        // 前台服务起来后要等它就绪，轮询一下告诉用户进展
        scope.launch {
            repeat(120) {
                delay(1000)
                if (server.probe(LocalServerManager.DEFAULT_PORT)) {
                    _ui.value = _ui.value.copy(
                        server = LocalServerState.Running(LocalServerManager.DEFAULT_PORT),
                        message = "已就绪",
                    )
                    return@launch
                }
            }
            _ui.value = _ui.value.copy(
                server = LocalServerState.Failed("等了 2 分钟还没起来，看通知栏有没有报错"),
            )
        }
    }

    fun stop() {
        LocalServerService.stop(ctx)
        _ui.value = _ui.value.copy(server = LocalServerState.Stopped, message = "已停止")
    }

    fun delete() {
        val m = _current.value
        scope.launch {
            server.stop()
            withContext(Dispatchers.IO) { store.delete(m) }
            _ui.value = _ui.value.copy(
                server = LocalServerState.Stopped, ready = false, message = "已删除",
            )
        }
    }

    /** 页面走了一定要调：不然子进程白占几个 G 内存。 */
    fun close() {
        server.stopNow()
        scope.cancel()
    }

    private suspend fun copyIn(uri: Uri, out: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ins = ctx.contentResolver.openInputStream(uri) ?: return@withContext false
            ins.use { FileOutputStream(out).use { os -> it.copyTo(os, 1024 * 1024) } }
            true
        }.getOrDefault(false)
    }

    private fun ramGb(): Int {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        return (mi.totalMem / (1024 * 1024 * 1024)).toInt()
    }

    private companion object { const val KEY_CHOICE = "choice" }
}
