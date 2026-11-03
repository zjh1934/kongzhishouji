package com.wangzan101.mika.local

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 双文件包里的角色。 */
enum class FileRole { MAIN, MMPROJ }

/** 文件层状态：报告第 6 节状态机的精简版（够用、可观测，不引入新依赖）。 */
enum class FileState { Missing, Partial, Acquiring, Validating, Ready, Failed }

/** 包层状态由两个文件聚合而来，绝不用一个 boolean 糊过去。 */
enum class PackageStatus { NotAcquired, Partial, NeedsProjection, Ready, Failed }

enum class BusyKind { Download, Import }
enum class BusyPhase { Transfer, Validating }

data class FileUi(
    val role: FileRole,
    val spec: LocalModelCatalog.File,
    val state: FileState,
    /** 是否用官方 SHA-256 验过。false 时只说「结构有效 · 大小匹配」。 */
    val hashVerified: Boolean,
    val bytesDone: Long = 0L,
    val total: Long = 0L,
    val speed: Long = 0L,
    val error: String? = null,
)

data class ModelCardUi(
    val model: LocalModelCatalog.Model,
    val isActive: Boolean,
    val status: PackageStatus,
    val files: List<FileUi>,
)

/** 同一时刻只允许一个文件写入任务，避免两个 3GB 流互相踩。 */
data class Busy(
    val kind: BusyKind,
    val phase: BusyPhase,
    val modelId: String,
    val fileIndex: Int,
    val bytes: Long,
    val total: Long,
    val speed: Long,
)

/** 错误三段式：原因 / 影响 / 动作。不再只写「失败」两个字。 */
data class Notice(val title: String, val detail: String, val action: String)

sealed interface Confirm {
    data class Switch(val targetId: String, val serverRunning: Boolean) : Confirm
    data class Delete(val modelId: String, val freeBytes: Long, val active: Boolean) : Confirm
}

data class LocalModelUi(
    val cards: List<ModelCardUi> = emptyList(),
    val activeId: String? = null,
    val busy: Busy? = null,
    val server: LocalServerState = LocalServerState.Stopped,
    val notice: Notice? = null,
    val confirm: Confirm? = null,
    val probeResult: String? = null,
)

/**
 * 本机模型页的状态容器 —— 整块重写。
 *
 * 参考 research/模型导入页设计参考.md（PocketPal / MNN / Arm Vision Chat 等五个实现）：
 * - **包与文件两层状态**，下载与 SAF 导入共用「临时文件 → 结构化校验 → 原子激活」管道
 * - **以文件事实校正状态**：每次刷新重新扫磁盘，不信任内存里的 Ready
 * - 失败**不静默删文件**：保留原因、影响、下一步动作
 * - 单任务串行写入，取消保留断点
 * - 启动前检查两个文件都就绪 —— 修掉「切到本地不检查模型就起服务」那个缺陷
 *
 * 不引 Room / WorkManager / ViewModel：Mika 没这些依赖，加一个就多一处升级冲突。
 * 用 SharedPreferences 记「校验通过标记」+ 每次扫磁盘代替（PocketPal
 * 「以文件事实校正状态库」的轻量版）。
 *
 * 生命周期由页面用 DisposableEffect 调 [close] 收尾。
 */
class LocalModelController(app: Context) {

    private val ctx = app.applicationContext
    private val prefs = ctx.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private val store = LocalModelStore(File(ctx.getExternalFilesDir(null), "models"))
    private val downloader = LocalModelDownloader()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(LocalModelUi(activeId = loadChoice().id))
    val ui: StateFlow<LocalModelUi> = _ui.asStateFlow()

    private var job: Job? = null

    init {
        store.cleanupImportTemps()
        refresh()
        // 服务状态看总线那一份，页面不另起一套去猜
        scope.launch {
            LocalServerStateBus.state.collect { st ->
                _ui.value = _ui.value.copy(server = st)
            }
        }
    }

    private fun loadChoice(): LocalModelCatalog.Model {
        val saved = prefs.getString(KEY_CHOICE, null)?.let { LocalModelCatalog.byId(it) }
        return saved ?: LocalModelCatalog.recommend(LocalMemoryBudget.ramGb(ctx))
            ?: LocalModelCatalog.DEFAULT
    }

    /** 当前选中的模型（BackendSwitch 也会读同一个 prefs key）。 */
    private fun activeModel(): LocalModelCatalog.Model = loadChoice()

    /**
     * 重新扫磁盘。这是状态的唯一真相来源：
     * 文件在不在、多大、GGUF 头对不对、断点下到哪儿，全部以实际文件为准。
     */
    fun refresh() {
        val activeId = loadChoice().id
        val busy = _ui.value.busy
        val cards = LocalModelCatalog.ALL.map { model ->
            val files = model.files.mapIndexed { idx, spec ->
                val final = store.finalFile(spec)
                val isBusy = busy != null && busy.modelId == model.id && busy.fileIndex == idx
                when {
                    isBusy -> FileUi(
                        role = roleOf(idx), spec = spec,
                        state = if (busy!!.phase == BusyPhase.Validating) FileState.Validating else FileState.Acquiring,
                        hashVerified = false,
                        bytesDone = busy.bytes, total = busy.total, speed = busy.speed,
                    )
                    final.isFile -> when (val v = store.quickCheck(final, spec)) {
                        is LocalModelStore.Validation.Ok -> FileUi(
                            role = roleOf(idx), spec = spec, state = FileState.Ready,
                            hashVerified = hashMarked(spec),
                        )
                        else -> FileUi(
                            role = roleOf(idx), spec = spec, state = FileState.Failed,
                            hashVerified = false, error = explain(v, spec),
                        )
                    }
                    store.partialBytes(spec) > 0L -> FileUi(
                        role = roleOf(idx), spec = spec, state = FileState.Partial,
                        hashVerified = false,
                        bytesDone = store.partialBytes(spec), total = spec.size,
                    )
                    else -> FileUi(
                        role = roleOf(idx), spec = spec,
                        state = FileState.Missing, hashVerified = false,
                    )
                }
            }
            ModelCardUi(
                model = model,
                isActive = model.id == activeId,
                status = packageStatus(files),
                files = files,
            )
        }
        _ui.value = _ui.value.copy(cards = cards, activeId = activeId)
    }

    private fun roleOf(index: Int): FileRole = if (index == 0) FileRole.MAIN else FileRole.MMPROJ

    private fun packageStatus(files: List<FileUi>): PackageStatus = when {
        files.any { it.state == FileState.Failed } -> PackageStatus.Failed
        files.all { it.state == FileState.Ready } -> PackageStatus.Ready
        files[0].state == FileState.Ready -> PackageStatus.NeedsProjection
        files.any { it.state != FileState.Missing } -> PackageStatus.Partial
        else -> PackageStatus.NotAcquired
    }

    private fun hashMarked(spec: LocalModelCatalog.File): Boolean =
        spec.sha256.isNotBlank() && prefs.getString("verified:${spec.name}", null) == "hash"

    private fun markHash(spec: LocalModelCatalog.File, byHash: Boolean) {
        if (byHash) prefs.edit().putString("verified:${spec.name}", "hash").apply()
    }

    private fun explain(v: LocalModelStore.Validation, spec: LocalModelCatalog.File): String = when (v) {
        is LocalModelStore.Validation.NotGguf -> "文件头不是 GGUF，可能下成了网页或已截断"
        is LocalModelStore.Validation.Empty -> "文件是空的"
        is LocalModelStore.Validation.SizeMismatch ->
            "大小不符：期望 ${gb(v.expected)}，实际 ${gb(v.actual)}。文件可能没下完"
        is LocalModelStore.Validation.HashMismatch ->
            "SHA-256 对不上（期望 ${v.expected.take(12)}…，实际 ${v.actual.take(12)}…）"
        is LocalModelStore.Validation.Unreadable -> "读不到文件：${v.why}"
        is LocalModelStore.Validation.Ok -> ""
    }

    // ── 下载 ──────────────────────────────────────────────────

    /** App 内下载（会自动跳过已就绪文件、从 .downloading 断点续传）。 */
    fun download(modelId: String) {
        if (_ui.value.busy != null) return
        val model = LocalModelCatalog.byId(modelId) ?: return
        if (!store.hasRoomFor(model)) {
            notify(
                "空间不够",
                "还差约 ${gb(model.totalBytes + 512L * 1024 * 1024 - store.availableBytes())} 可用空间；" +
                    "模型目录可写：${store.modelDir().canWrite()}",
                "清理空间，或改用「复制地址 → 别的工具下载 → 从设备导入」",
            )
            return
        }
        clearNotice()
        job = scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    downloader.download(store, model) { p ->
                        _ui.value = _ui.value.copy(
                            busy = Busy(
                                BusyKind.Download, BusyPhase.Transfer, modelId,
                                (p.fileIndex - 1).coerceAtLeast(0), p.bytes, p.total, p.bytesPerSecond,
                            ),
                        )
                    }
                }
                // 下载器内部已做全检 + 原子激活；这里补上「官方哈希验过」标记
                model.files.forEach { spec -> markHash(spec, spec.sha256.isNotBlank()) }
                when (r) {
                    is DownloadResult.Ok -> notify(
                        "下载完成", "${model.displayName} 的两个文件都已就位", "可以点「启动」加载模型",
                    )
                    is DownloadResult.Failed -> notify(
                        "下载没完成", r.message,
                        if (r.retryable) "已保留断点，点「继续下载」接着下；或换用外部下载再导入"
                        else "检查空间与目录权限后再试",
                    )
                }
            } catch (e: CancellationException) {
                notify("已暂停", "已下的部分保留在 .downloading 里，不会从头来", "点「继续下载」接着下")
            } finally {
                _ui.value = _ui.value.copy(busy = null)
                refresh()
            }
        }
    }

    /** 取消当前任务：只停协程，断点文件留着。 */
    fun cancelWork() {
        job?.cancel()
        job = null
    }

    // ── 导入 ──────────────────────────────────────────────────

    /**
     * 从设备导入一个文件。
     *
     * 与下载共用同一条校验管道：先写临时文件 → 全检（魔数/大小/SHA）→ 通过才原子激活。
     * 失败**只删临时文件**，绝不碰用户选的源文件，也不静默吞掉原因。
     */
    fun import(picked: PickedFile, modelId: String, which: Int) {
        if (_ui.value.busy != null) return
        val model = LocalModelCatalog.byId(modelId) ?: return
        val spec = model.files.getOrNull(which) ?: return
        val role = if (which == 0) "主模型" else "视觉投影"

        // 快失败：提供器给了大小就直接比，不用等拷完 2 GB 才说选错了
        if (picked.size >= 0 && picked.size != spec.size) {
            notify(
                "选错文件或没下完",
                "你选的是 ${picked.displayName}（${gb(picked.size)}），" +
                    "但$role 期望 ${spec.name}（${gb(spec.size)}）",
                "确认下载完整后重选；两个文件的地址可从对应文件行复制",
            )
            releaseUri(picked)
            return
        }
        if (store.finalFile(spec).isFile) {
            notify("这个文件已经就位", "${spec.name} 已通过校验", "如要替换，先点「删除模型」再导入")
            releaseUri(picked)
            return
        }
        if (store.availableBytes() < spec.size + 512L * 1024 * 1024) {
            notify("空间不够", "导入需要额外 ${gb(spec.size)} 的临时空间", "清理空间后再试")
            releaseUri(picked)
            return
        }

        clearNotice()
        val temp = store.importTemp(spec).also { it.delete() }
        job = scope.launch {
            try {
                _ui.value = _ui.value.copy(
                    busy = Busy(BusyKind.Import, BusyPhase.Transfer, modelId, which, 0L, spec.size, 0L),
                    probeResult = null,
                )
                val copied = withContext(Dispatchers.IO) {
                    val ins = ctx.contentResolver.openInputStream(picked.uri)
                        ?: return@withContext false
                    ins.use { input ->
                        FileOutputStream(temp).use { os ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                os.write(buf, 0, n)
                                done += n
                                _ui.value = _ui.value.copy(
                                    busy = Busy(
                                        BusyKind.Import, BusyPhase.Transfer, modelId, which,
                                        done, spec.size, 0L,
                                    ),
                                )
                            }
                        }
                    }
                    true
                }
                if (!copied) {
                    notify("读不到所选文件", "系统文件选择器返回的文件打不开", "把文件复制到手机内部存储后重试")
                    temp.delete()
                    return@launch
                }

                _ui.value = _ui.value.copy(
                    busy = Busy(BusyKind.Import, BusyPhase.Validating, modelId, which, spec.size, spec.size, 0L),
                )
                val v = withContext(Dispatchers.IO) { store.fullCheck(temp, spec) }
                when (v) {
                    is LocalModelStore.Validation.Ok -> {
                        val ok = withContext(Dispatchers.IO) { store.activate(temp, spec) }
                        if (!ok) {
                            notify("写入失败", "校验通过但无法把文件放到模型目录", "检查目录权限或剩余空间")
                        } else {
                            markHash(spec, v.byHash)
                            notify(
                                "$role 已导入",
                                if (v.byHash) "SHA-256 校验通过" else "结构有效 · 大小匹配（该模型未提供官方哈希）",
                                if (store.isModelReady(model)) "两个文件都齐了，可以启动" else "还差另一个文件，继续导入或下载",
                            )
                        }
                    }
                    else -> {
                        temp.delete()
                        notify("导入未通过校验", explain(v, spec), "原文件没动，请重新下载或重选文件")
                    }
                }
            } catch (e: CancellationException) {
                temp.delete()
                notify("导入已取消", "临时文件已清理，原文件没动", "随时可以重新导入")
            } catch (e: Exception) {
                temp.delete()
                notify("导入中断", e.message.orEmpty().take(160), "原文件没动，可重新导入")
            } finally {
                releaseUri(picked)
                _ui.value = _ui.value.copy(busy = null)
                refresh()
            }
        }
    }

    private fun releaseUri(picked: PickedFile) {
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                picked.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    // ── 选择 / 删除 / 启停 ────────────────────────────────────

    /** 设为当前。服务在跑时先确认 —— 停模型会打断正在执行的 Agent 任务。 */
    fun select(modelId: String) {
        if (modelId == loadChoice().id) return
        if (_ui.value.server is LocalServerState.Running) {
            _ui.value = _ui.value.copy(
                confirm = Confirm.Switch(modelId, serverRunning = true),
            )
        } else doSwitch(modelId)
    }

    /** 确认框里点「仍要切换」走这里，别再调 [select] —— 否则服务还 Running 会又弹一次。 */
    fun confirmSwitch() {
        val c = _ui.value.confirm as? Confirm.Switch ?: return
        doSwitch(c.targetId)
    }

    private fun doSwitch(modelId: String) {
        LocalServerService.stop(ctx)
        prefs.edit().putString(KEY_CHOICE, modelId).apply()
        _ui.value = _ui.value.copy(confirm = null)
        refresh()
    }

    fun requestDelete(modelId: String) {
        val model = LocalModelCatalog.byId(modelId) ?: return
        val present = model.files.sumOf { store.finalFile(it).takeIf { f -> f.isFile }?.length() ?: 0L }
        _ui.value = _ui.value.copy(
            confirm = Confirm.Delete(modelId, present, model.id == loadChoice().id),
        )
    }

    fun confirmDelete() {
        val c = _ui.value.confirm as? Confirm.Delete ?: return
        val model = LocalModelCatalog.byId(c.modelId) ?: return
        cancelWork()
        LocalServerService.stop(ctx)
        store.delete(model)
        val ed = prefs.edit()
        model.files.forEach { ed.remove("verified:${it.name}") }
        ed.apply()
        _ui.value = _ui.value.copy(confirm = null)
        notify("已删除", "释放 ${gb(c.freeBytes)}", "需要时重新下载或导入")
        refresh()
    }

    fun dismissConfirm() {
        _ui.value = _ui.value.copy(confirm = null)
    }

    /** 启动前必须两个文件都就绪 —— 不再出现「切过去就失败」。 */
    fun start() {
        val model = activeModel()
        val missing = model.files.filterNot { store.isReady(it) }
        if (missing.isNotEmpty()) {
            notify(
                "还不能直接启动",
                "缺 ${missing.joinToString("、") { it.name }}；" +
                    "本机模型必须由主模型 + 视觉投影两个文件组成",
                "先把缺的文件下载或导入进来",
            )
            return
        }
        clearNotice()
        LocalServerService.start(ctx)
    }

    fun stop() {
        LocalServerService.stop(ctx)
    }

    /** 最小探针：不带 tools、不带图片，只问一句话。 */
    fun probe() {
        scope.launch {
            _ui.value = _ui.value.copy(probeResult = "探针：正在请求…")
            val result = withContext(Dispatchers.IO) { LocalProbe.run() }
            _ui.value = _ui.value.copy(probeResult = result)
        }
    }

    fun clearNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    private fun notify(title: String, detail: String, action: String) {
        _ui.value = _ui.value.copy(notice = Notice(title, detail, action))
    }

    fun close() {
        job?.cancel()
        scope.cancel()
    }

    private fun gb(bytes: Long): String = "%.2f GB".format(bytes / 1_073_741_824.0)

    private companion object { const val KEY_CHOICE = "choice" }
}
