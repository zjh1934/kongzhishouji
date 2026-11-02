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

/** 单个文件的状态。两个文件分别显示，别再合成一个"已下载"。 */
enum class FileState { Missing, Ready, Bad }

/** 界面上要显示的一切。 */
data class LocalModelUi(
    val downloading: Boolean = false,
    val progress: DownloadProgress? = null,
    /** 主模型、视觉投影各自的状态。 */
    val fileStates: List<FileState> = listOf(FileState.Missing, FileState.Missing),
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(LocalModelUi())
    val ui: StateFlow<LocalModelUi> = _ui.asStateFlow()

    private val _current = MutableStateFlow(loadChoice())
    val current: StateFlow<LocalModelCatalog.Model> = _current.asStateFlow()

    init {
        refresh()
        // 服务状态看总线那一份：服务是自己起的、也可能已经起来了，
        // 页面不该另起一套去猜（猜就会不一致）
        scope.launch {
            LocalServerStateBus.state.collect { st ->
                _ui.value = _ui.value.copy(server = st)
            }
        }
    }

    /** 换模型。正在跑的服务要停掉 —— 换模型还占着旧权重没意义。 */
    fun select(model: LocalModelCatalog.Model) {
        if (model.id == _current.value.id) return
        LocalServerService.stop(ctx)
        prefs.edit().putString(KEY_CHOICE, model.id).apply()
        _current.value = model
        _ui.value = LocalModelUi()
        refresh()
    }

    private fun loadChoice(): LocalModelCatalog.Model {
        val saved = prefs.getString(KEY_CHOICE, null)?.let { LocalModelCatalog.byId(it) }
        // 没存过就按机身内存挑一个
        return saved ?: LocalModelCatalog.recommend(ramGb()) ?: LocalModelCatalog.DEFAULT
    }

    /**
     * 刷新两个文件各自的状态。
     *
     * **必须分开显示**：只给一个汇总的"已下载"，用户不知道哪个文件缺、
     * 也可能因为看到"下载"按钮把已经导入的 2 GB 又下一遍。
     */
    fun refresh() {
        val m = _current.value
        val states = m.files.map { f ->
            val file = File(store.modelDir(), f.name)
            when {
                !file.exists() -> FileState.Missing
                store.verify(file, f) -> FileState.Ready
                else -> FileState.Bad
            }
        }
        _ui.value = _ui.value.copy(
            fileStates = states,
            ready = states.all { it == FileState.Ready },
        )
    }

    /** App 内下载。支持断点续传，主源失败自动换备选源。 */
    fun download() {
        val m = _current.value
        scope.launch {
            _ui.value = _ui.value.copy(downloading = true, message = null)
            when (val r = downloader.download(store, m) { p ->
                _ui.value = _ui.value.copy(progress = p)
            }) {
                is DownloadResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        downloading = false, progress = null, message = "下载完成",
                    )
                    refresh()
                }
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
                message = when {
                    !copied -> "读不到这个文件"
                    !ok -> "文件对不上（不完整或不是这个文件），已丢弃"
                    else -> "导入成功"
                },
            )
            refresh()
        }
    }

    /**
     * 起本机服务。
     *
     * 走 [LocalServerService]（前台服务）而不是直接起子进程：
     * Android 12+ 会杀没有前台服务保护的后台子进程，且是捕获不到的 SIGKILL。
     * **不在这里轮询等待** —— 服务那边会通过 [LocalServerStateBus] 把状态推过来，
     * 页面实时显示，包括失败原因。
     */
    fun start() {
        _ui.value = _ui.value.copy(message = null)
        LocalServerService.start(ctx)
    }

    fun stop() {
        LocalServerService.stop(ctx)
    }

    fun delete() {
        val m = _current.value
        scope.launch {
            LocalServerService.stop(ctx)
            withContext(Dispatchers.IO) { store.delete(m) }
            _ui.value = _ui.value.copy(message = "已删除")
            refresh()
        }
    }

    /** 页面走了要调（收协程）；**不要**在这里停服务 —— 服务该继续活着。 */
    fun close() {
        scope.cancel()
    }

    private suspend fun copyIn(uri: Uri, out: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ins = ctx.contentResolver.openInputStream(uri) ?: return@withContext false
            ins.use { FileOutputStream(out).use { os -> it.copyTo(os, 1024 * 1024) } }
            true
        }.getOrDefault(false)
    }

    private fun ramGb(): Int = LocalMemoryBudget.ramGb(ctx)

    private companion object { const val KEY_CHOICE = "choice" }
}
