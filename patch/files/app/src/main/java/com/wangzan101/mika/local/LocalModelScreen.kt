package com.wangzan101.mika.local

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
 * 本机模型：下载、导入、启动。
 *
 * 两条获取路径都在这里：
 * - **App 内下载**：默认走魔搭，能断点续传、失败换源
 * - **导入**：在电脑上或用别的工具下完，通过系统文件选择器挑进来，校验通过才收
 *
 * 启动后就是本机 8080 上一个 OpenAI 兼容的服务，
 * Mika 用连 Ollama 一样的方式连它（见 ProviderPresets 里的「本机模型」）。
 */
@Composable
fun LocalModelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm = remember { LocalModelController(ctx) }
    // 离开页面就收掉子进程，别让它白占内存
    DisposableEffect(vm) { onDispose { vm.close() } }

    val ui by vm.ui.collectAsState()
    val model = vm.model

    PageScaffold(title = "本机模型", onBack = onBack) {
        if (model == null) {
            GroupCard {
                Column(Modifier.padding(16.dp)) {
                    Text("内存不够", fontWeight = FontWeight.SemiBold)
                    FooterNote("这个模型需要更大的机身内存。换一台内存更大的设备再试。")
                }
            }
            return@PageScaffold
        }

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

        GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("获取模型", fontWeight = FontWeight.SemiBold)
                FooterNote("两个文件都要有：主模型和视觉投影（缺了就看不见屏幕）。")
                MikaButton(
                    text = if (ui.ready) "已下载"
                           else if (ui.downloading) "下载中…"
                           else "下载（约 ${mb(model.requiredBytes)}）",
                    onClick = vm::download,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ui.ready && !ui.downloading,
                )
                val pick = rememberFilePicker { uri, which -> vm.import(uri, which) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MikaButton("导入主模型", { pick(0) }, Modifier.weight(1f))
                    MikaButton("导入视觉投影", { pick(1) }, Modifier.weight(1f))
                }
            }
        }

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
                if (ui.ready) MikaButton("删除模型", vm::delete, Modifier.fillMaxWidth())
            }
        }
    }
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

private fun mb(bytes: Long): String = "%.0f MB".format(bytes / 1_000_000.0)
