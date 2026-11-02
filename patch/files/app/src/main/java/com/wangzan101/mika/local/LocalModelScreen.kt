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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * 本机模型：选模型、下载、导入、启动。
 *
 * 三个设计点：
 * - **模型可选**：手机操控没有公认的最佳端侧模型，谁更好只有实测知道，让用户自己换
 * - **两条获取路径**：App 内下载 / 复制地址去别的工具下完再导入
 * - **启动后**就是本机 8080 上一个 OpenAI 兼容服务，
 *   Mika 用连 Ollama 一样的方式连它（见 ProviderPresets 里的「本机模型」）
 */
@Composable
fun LocalModelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm = remember { LocalModelController(ctx) }
    DisposableEffect(vm) { onDispose { vm.close() } }

    val ui by vm.ui.collectAsState()
    val model by vm.current.collectAsState()

    PageScaffold(title = "本机模型", onBack = onBack) {
        // ---- 选模型 ----
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("用哪个模型", fontWeight = FontWeight.SemiBold)
                FooterNote("没有公认最好的端侧模型，可以都试试。换模型会停掉当前服务。")
                LocalModelCatalog.ALL.forEach { m ->
                    val selected = m.id == model.id
                    MikaButton(
                        text = (if (selected) "✓ " else "") + m.displayName + "　" + mb(m.totalBytes),
                        onClick = { vm.select(m) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FooterNote(m.blurb)
                }
            }
        }

        // ---- 状态 ----
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(model.displayName, fontWeight = FontWeight.SemiBold)
                FooterNote(stateText(ui))
                ui.progress?.let { p ->
                    if (p.total > 0) {
                        LinearProgressIndicator(
                            progress = { (p.bytes.toFloat() / p.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        FooterNote("第 ${p.fileIndex}/${p.fileCount} 个文件　${mb(p.bytes)} / ${mb(p.total)}")
                    }
                }
                if (ui.importing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                        Text("　导入中…")
                    }
                }
                ui.message?.let { FooterNote(it) }
            }
        }

        // ---- 获取 ----
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("获取模型", fontWeight = FontWeight.SemiBold)
                FooterNote("两个文件都要下。当前本地模式靠无障碍树 + OCR 文字看屏，暂不把截图发给模型；视觉投影先留着，第二阶段上视觉能力时用。")
                MikaButton(
                    text = if (ui.ready) "已下载"
                           else if (ui.downloading) "下载中…"
                           else "下载（约 ${mb(model.totalBytes)}）",
                    onClick = vm::download,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ui.ready && !ui.downloading,
                )
                Text("或者复制地址，用别的工具下完再导入", fontWeight = FontWeight.SemiBold)
                MikaButton(
                    text = "复制主模型地址",
                    onClick = { copyUrl(ctx, LocalModelSources.copyableUrl(model, model.main)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                MikaButton(
                    text = "复制视觉投影地址",
                    onClick = { copyUrl(ctx, LocalModelSources.copyableUrl(model, model.mmproj)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val pick = rememberFilePicker { uri, which -> vm.import(uri, which) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MikaButton("导入主模型", { pick(0) }, Modifier.weight(1f))
                    MikaButton("导入视觉投影", { pick(1) }, Modifier.weight(1f))
                }
            }
        }

        // ---- 启停 ----
        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("本机服务", fontWeight = FontWeight.SemiBold)
                FooterNote("启动后在 127.0.0.1:8080，Mika 按 OpenAI 兼容接口连它。")
                when (ui.server) {
                    is LocalServerState.Running ->
                        MikaButton("停止", vm::stop, Modifier.fillMaxWidth())
                    is LocalServerState.Starting ->
                        MikaButton("正在启动…", {}, Modifier.fillMaxWidth(), enabled = false)
                    else ->
                        MikaButton("启动", vm::start, Modifier.fillMaxWidth(), enabled = ui.ready)
                }
                // 诊断用：不带图、不带工具，只问一句话。
                // 秒回 ⇒ 服务没问题，是 Mika 的请求体把它卡住；也卡 ⇒ 看进程输出。
                MikaButton(
                    text = "测试一句话（诊断）",
                    onClick = vm::probe,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ui.server is LocalServerState.Running,
                )
                if (ui.server is LocalServerState.Running) {
                    FooterNote("如果这里秒回、但聊天框里卡住，就是请求体里的图片或工具定义有问题。")
                }
                if (ui.ready) MikaButton("删除模型", vm::delete, Modifier.fillMaxWidth())
            }
        }
    }
}

/** 把地址放到剪贴板，好拿去迅雷/IDM/电脑上下载。 */
private fun copyUrl(ctx: Context, url: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("model url", url))
    Toast.makeText(ctx, "地址已复制", Toast.LENGTH_SHORT).show()
}

private fun stateText(ui: LocalModelUi): String = when {
    ui.downloading -> "下载中"
    ui.importing -> "导入中"
    ui.server is LocalServerState.Running -> "已就绪，正在本机 8080 端口运行"
    ui.server is LocalServerState.Starting -> "正在启动"
    ui.server is LocalServerState.Failed -> "启动失败：${(ui.server as LocalServerState.Failed).why}"
    ui.ready -> "已下载，还没启动"
    else -> "还没下载"
}

private fun mb(bytes: Long): String = "%.1f GB".format(bytes / 1_073_741_824.0)
