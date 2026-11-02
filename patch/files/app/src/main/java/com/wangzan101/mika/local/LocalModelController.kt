package com.wangzan101.mika.local

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    /** 两个文件都在才算装好。 */
    val ready: Boolean = false,
    val server: LocalServerState = LocalServerState.Stopped,
    val availableBytes: Long = 0L,
    val message: String? = null,
    val importing: Boolean = false,
)

/**
 * 本机模型的下载、导入、启停。
 *
 * 不做成 `ViewModel` + `viewModel()`：Mika 自己没引 `lifecycle-viewmodel-compose`，
 * 它是直接构造 ViewModel 的（见 `MainActivity` 里的 `MemoryViewModel(...)`）。
 * 跟着它的做法走，就不用给工程加依赖 —— 少动一处 build.gradle，升级时少一处冲突。
 *
 * 生命周期由页面用 `DisposableEffect` 收尾（[close]）。
 */
class LocalModelController(app: Context) {

    private val ctx = app.applicationContext
    private val store = LocalModelStore(File(ctx.getExternalFilesDir(null), "models"))
    private val downloader = LocalModelDownloader()
    private val server = LocalServerManager(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(LocalModelUi())
    val ui: StateFlow<LocalModelUi> = _ui.asStateFlow()

    /** 按机身内存挑模型；内存不够为 null。 */
    val model: LocalModelCatalog.Model? = LocalModelCatalog.recommend(ramGb())

    init { refresh() }

    fun refresh() {
        val m = model
        _ui.value = _ui.value.copy(
            ready = m?.let { store.isModelReady(it) } ?: false,
            availableBytes = store.availableBytes(),
        )
    }

    /** App 内下载。支持断点续传，主源失败自动换备选源。 */
    fun download() {
        val m = model ?: return
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
     * 3 GB 在 App 内下不一定顺，用户可能用别的工具下或在电脑上下好传过来。
     * 收进来先校验，对不上就不留 —— 免得下错文件到运行时才发现。
     */
    fun import(uri: Uri, which: Int) {
        val m = model ?: return
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
                    else -> "导入成功",
                },
            )
        }
    }

    /** 起本机服务。4B 视觉模型加载慢，界面上要一直有反馈。 */
    fun start() {
        val m = model ?: return
        scope.launch {
            _ui.value = _ui.value.copy(server = LocalServerState.Starting(1), message = null)
            val st = server.start(
                modelPath = store.pathOf(m.main),
                mmprojPath = store.pathOf(m.mmproj),
                nCtx = 4096,
                nThreads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8),
            )
            _ui.value = _ui.value.copy(
                server = st,
                message = if (st is LocalServerState.Running) "已就绪" else (st as? LocalServerState.Failed)?.why,
            )
        }
    }

    fun stop() {
        scope.launch {
            server.stop()
            _ui.value = _ui.value.copy(server = LocalServerState.Stopped, message = "已停止")
        }
    }

    fun delete() {
        val m = model ?: return
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
}
