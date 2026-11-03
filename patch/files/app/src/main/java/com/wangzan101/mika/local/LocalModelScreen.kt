package com.wangzan101.mika.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.PageScaffold

/**
 * 本机模型页 —— 整块重写。
 *
 * 信息架构按调研报告第 2 节：单页三段
 *  ① 顶部：当前使用 + 服务健康（含失败原因，不再只写「失败」）
 *  ② 模型包卡片：每张卡展开后，主模型 / 视觉投影**各自一行**，
 *     各自显示期望名、大小、状态、进度、失败原因与可执行动作
 *  ③ 诊断：错误按「原因 / 影响 / 动作」三段呈现
 *
 * 下载、复制地址、从设备导入是同一件事的三个入口，共用校验管道；
 * 绝不再用一个「已下载」按钮把两个文件的状态糊在一起。
 */
@Composable
fun LocalModelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm = remember { LocalModelController(ctx) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    val ui by vm.ui.collectAsState()

    // 文件选择器是页面级的：先记下「给哪个模型的哪个文件选」，再拉起选择器
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val pick = rememberFilePicker { picked, _ ->
        val target = pending
        if (target != null) vm.import(picked, target.first, target.second)
        pending = null
    }
    fun startPick(modelId: String, which: Int) {
        pending = modelId to which
        pick(which)
    }

    PageScaffold(title = "本机模型", onBack = onBack) {

        // ── ① 顶部：当前使用与服务健康 ────────────────────────
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val active = ui.cards.firstOrNull { it.isActive }
                Text(
                    "当前使用：" + (active?.model?.displayName ?: "—"),
                    fontWeight = FontWeight.SemiBold,
                )
                FooterNote(serverText(ui.server))
                if (ui.server is LocalServerState.Failed) {
                    FooterNote("启动失败原因：" + (ui.server as LocalServerState.Failed).why)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (ui.server) {
                        is LocalServerState.Running ->
                            MikaButton("停止服务", vm::stop, Modifier.weight(1f))
                        is LocalServerState.Starting ->
                            MikaButton("正在启动…", {}, Modifier.weight(1f), enabled = false)
                        else ->
                            MikaButton("启动", vm::start, Modifier.weight(1f))
                    }
                    MikaButton(
                        text = "测试一句话",
                        onClick = vm::probe,
                        modifier = Modifier.weight(1f),
                        enabled = ui.server is LocalServerState.Running,
                    )
                }
                ui.probeResult?.let { FooterNote(it) }
            }
        }

        // ── ③ 错误三段式（放上面，别让用户翻到最底下才看见）────
        ui.notice?.let { n ->
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(n.title, fontWeight = FontWeight.SemiBold)
                    FooterNote(n.detail)
                    FooterNote("下一步：${n.action}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton("知道了", vm::clearNotice, Modifier.weight(1f))
                        ui.busy?.let {
                            MikaButton("取消当前任务", vm::cancelWork, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 正在进行的任务：全局唯一，显示清楚在干哪个文件
        ui.busy?.let { b ->
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val model = LocalModelCatalog.byId(b.modelId)
                    val spec = model?.files?.getOrNull(b.fileIndex)
                    Text(
                        if (b.phase == BusyPhase.Validating) "正在校验文件完整性…"
                        else if (b.kind == BusyKind.Import) "正在导入 ${spec?.name ?: ""}"
                        else "正在下载 ${spec?.name ?: ""}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (b.phase == BusyPhase.Transfer && b.total > 0) {
                        LinearProgressIndicator(
                            progress = { (b.bytes.toFloat() / b.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FooterNote("${pct(b.bytes, b.total)}　${gb(b.bytes)} / ${gb(b.total)}　${speed(b.speed)}")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp))
                            Text("　处理中…")
                        }
                    }
                    MikaButton("取消（保留断点）", vm::cancelWork, Modifier.fillMaxWidth())
                }
            }
        }

        // ── ② 模型包卡片 ──────────────────────────────────────
        ui.cards.forEach { card ->
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            card.model.displayName,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (card.isActive) Text("当前使用")
                    }
                    FooterNote(card.model.blurb)
                    FooterNote(statusText(card.status) + "　共 ${gb(card.model.totalBytes)}")

                    card.files.forEachIndexed { idx, f ->
                        FileRow(
                            file = f,
                            enabled = ui.busy == null,
                            onDownload = { vm.download(card.model.id) },
                            onCopy = {
                                val role = if (idx == 0) "主模型" else "视觉投影"
                                copyText(
                                    ctx,
                                    LocalModelSources.importHint(card.model, f.spec, role),
                                )
                            },
                            onImport = { startPick(card.model.id, idx) },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton(
                            text = if (card.status == PackageStatus.Ready) "两个文件都就绪"
                                   else "下载两个文件",
                            onClick = { vm.download(card.model.id) },
                            modifier = Modifier.weight(1f),
                            enabled = ui.busy == null && card.status != PackageStatus.Ready,
                        )
                        if (!card.isActive) {
                            MikaButton(
                                text = "设为当前",
                                onClick = { vm.select(card.model.id) },
                                modifier = Modifier.weight(1f),
                                enabled = ui.busy == null,
                            )
                        }
                    }
                    MikaButton(
                        text = "删除这个模型的文件",
                        onClick = { vm.requestDelete(card.model.id) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.busy == null,
                    )
                }
            }
        }

        FooterNote("两个文件必须先落到 App 私有目录并各自通过校验，才能启动。导入不会改动你选的原文件。")
    }

    // ── 危险操作确认 ──────────────────────────────────────────
    ui.confirm?.let { c ->
        when (c) {
            is Confirm.Switch -> AlertDialog(
                onDismissRequest = vm::dismissConfirm,
                title = { Text("切换模型？") },
                text = { Text("本机服务正在运行。切换会停掉当前模型并重新加载，正在执行的任务会被打断。") },
                confirmButton = {
                    TextButton(onClick = vm::confirmSwitch) { Text("仍要切换") }
                },
                dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("取消") } },
            )
            is Confirm.Delete -> AlertDialog(
                onDismissRequest = vm::dismissConfirm,
                title = { Text("删除模型文件？") },
                text = {
                    Text("将释放 ${gb(c.freeBytes)}。" +
                        if (c.active) "这是当前使用的模型，会先停止服务。" else "")
                },
                confirmButton = { TextButton(onClick = vm::confirmDelete) { Text("删除") } },
                dismissButton = { TextButton(onClick = vm::dismissConfirm) { Text("取消") } },
            )
        }
    }
}

/** 一个文件一行：状态、期望名、大小、进度、错误、动作，全都看得见。 */
@Composable
private fun FileRow(
    file: FileUi,
    enabled: Boolean,
    onDownload: () -> Unit,
    onCopy: () -> Unit,
    onImport: () -> Unit,
) {
    val role = if (file.role == FileRole.MAIN) "主模型" else "视觉投影"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$role · ${file.spec.name}", fontWeight = FontWeight.SemiBold)
        FooterNote(fileStateText(file))
        file.error?.let { FooterNote("问题：$it") }
        if (file.state == FileState.Acquiring && file.total > 0) {
            LinearProgressIndicator(
                progress = { (file.bytesDone.toFloat() / file.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val primary = when (file.state) {
                FileState.Ready -> null
                FileState.Partial -> "继续下载" to onDownload
                FileState.Failed -> "重新下载" to onDownload
                else -> "下载" to onDownload
            }
            primary?.let { (label, action) ->
                MikaButton(label, action, Modifier.weight(1f), enabled = enabled)
            }
            MikaButton("复制地址", onCopy, Modifier.weight(1f), enabled = enabled)
            MikaButton("从设备导入", onImport, Modifier.weight(1f), enabled = enabled)
        }
    }
}

private fun fileStateText(f: FileUi): String = when (f.state) {
    FileState.Missing -> "未获取 · 期望 ${gb(f.spec.size)}"
    FileState.Partial -> "已下到 ${pct(f.bytesDone, f.total)}，可继续（${gb(f.bytesDone)} / ${gb(f.spec.size)}）"
    FileState.Acquiring -> "传输中 ${pct(f.bytesDone, f.total)}　${gb(f.bytesDone)} / ${gb(f.total)}"
    FileState.Validating -> "正在校验魔数 / 大小 / SHA-256"
    FileState.Ready -> if (f.hashVerified) "已就绪 · SHA-256 校验通过 · ${gb(f.spec.size)}"
                       else "已就绪 · 结构有效、大小匹配（无官方哈希）· ${gb(f.spec.size)}"
    FileState.Failed -> "校验未通过（${f.error ?: "原因未知"}）"
}

private fun statusText(s: PackageStatus): String = when (s) {
    PackageStatus.NotAcquired -> "未获取"
    PackageStatus.Partial -> "部分就绪"
    PackageStatus.NeedsProjection -> "缺视觉投影"
    PackageStatus.Ready -> "两个文件都就绪"
    PackageStatus.Failed -> "有文件校验失败"
}

private fun serverText(s: LocalServerState): String = when (s) {
    is LocalServerState.Running -> "已就绪，正在本机 ${s.port} 端口运行"
    is LocalServerState.Starting -> "正在加载模型…"
    is LocalServerState.Failed -> "启动失败"
    is LocalServerState.Stopped -> "服务未启动"
}

private fun pct(done: Long, total: Long): String =
    if (total > 0) "${(done * 100 / total).coerceIn(0, 100)}%" else "总大小未知"

private fun gb(bytes: Long): String = "%.2f GB".format(bytes / 1_073_741_824.0)

private fun speed(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0) "" else "%.1f MB/s".format(bytesPerSecond / 1_048_576.0)

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("model info", text))
    Toast.makeText(ctx, "地址与导入信息已复制", Toast.LENGTH_SHORT).show()
}
