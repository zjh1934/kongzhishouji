package com.wangzan101.mika.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 界面上要显示的一切。 */
data class LocalModelUi(
    /** 正在下哪个文件（1 起），没在下为 0。 */
    val downloading: Boolean = false,
    val progress: DownloadProgress? = null,
    /** 装好了没有（两个文件都在）。 */
    val ready: Boolean = false,
    val server: LocalServerState = LocalServerState.Stopped,
    /** 可用空间 / 需要空间，字节。 */
    val availableBytes: Long = 0L,
    val message: String? = null,
    /** 正在导入（读入外部文件）时转圈。 */
    val importing: Boolean = false,
)

class LocalModelViewModel(private val app: Context) : ViewModel() {

    private val store = LocalModelStore(File(app.getExternalFilesDir(null), "models"))
    private val downloader = LocalModelDownloader()
    private val server = LocalServerManager(app)

    private val _ui = MutableStateFlow(LocalModelUi())
    val ui: StateFlow<LocalModelUi> = _ui.asStateFlow()

    private var job: Job? = null

    /** 推荐用哪个模型：按机身内存挑。 */
    val model: LocalModelCatalog.Model? get() = LocalModelCatalog.recommend(ramGb())

    init {
        refresh()
    }

    fun refresh() {
        val m = model
        _ui.value = _ui.value.copy(
            ready = m?.let { store.isModelReady(it) } ?: false,
            availableBytes = store.availableBytes(),
            server = if (server.isRunning()) LocalServerState.Running(LocalServerManager.DEFAULT_PORT)
                     else LocalServerState.Stopped,
        )
    }

    /** 在 App 内下载。断点续传，失败会自动换源。 */
    fun download() {
        val m = model ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = _ui.value.copy(downloading = true, message = null)
            when (val r = downloader.download(store, m) { p ->
                _ui.value = _ui.value.copy(progress = p)
            }) {
                is DownloadResult.Ok -> {
                    _ui.value = _ui.value.copy(
                        downloading = false, ready = true, progress = null,
                        message = "下载完成，可以启动了",
                    )
                }
                is DownloadResult.Failed -> {
                    _ui.value = _ui.value.copy(
                        downloading = false, progress = null,
                        message = r.message + if (r.retryable) "（已保留进度，可继续）" else "",
                    )
                }
            }
        }
    }

    /**
     * 导入外部下好的文件。
     *
     * 用户在电脑上或用别的工具下完，通过系统的文件选择器挑进来。
     * 校验通过才收 —— 免得下错了文件到运行时才发现。
     */
    fun import(uri: Uri, which: Int) {
        val m = model ?: return
        val file = m.files.getOrNull(which) ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(importing = true, message = null)
            val ok = withContext(Dispatchers.IO) { copyIn(uri, File(store.modelDir(), file.name)) }
            val verified = ok && store.verify(File(store.modelDir(), file.name), file)
            _ui.value = _ui.value.copy(
                importing = false,
                ready = store.isModelReady(m),
                message = when {
                    !ok -> "读不到这个文件"
                    !verified -> "文件对不上（不完整或不是这个文件），已丢弃"
                    else -> "导入成功"
                },
            )
        }
    }

    /** 起服务。4B 视觉模型加载慢，界面上要一直有反馈。 */
    fun start() {
        val m = model ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
            server.stop()
            _ui.value = _ui.value.copy(server = LocalServerState.Stopped, message = "已停止")
        }
    }

    fun delete() {
        val m = model ?: return
        viewModelScope.launch {
            stop()
            store.delete(m)
            refresh()
            _ui.value = _ui.value.copy(message = "已删除")
        }
    }

    private suspend fun copyIn(uri: Uri, out: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { ins ->
                FileOutputStream(out).use { os -> ins.copyTo(os, 1024 * 1024) }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    private fun ramGb(): Int {
        val mi = android.app.ActivityManager.MemoryInfo()
        (app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.getMemoryInfo(mi)
        return (mi.totalMem / (1024 * 1024 * 1024)).toInt()
    }

    override fun onCleared() {
        // 页面走了就把子进程收掉，别让它白占几个 G 内存
        viewModelScope.launch { server.stop() }
        super.onCleared()
    }
}
