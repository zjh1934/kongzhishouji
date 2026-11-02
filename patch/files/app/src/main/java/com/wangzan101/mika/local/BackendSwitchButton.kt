package com.wangzan101.mika.local

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

/**
 * 聊天框顶栏的「云端 / 本地」切换按钮 —— 脚手架版，只求看得见、点得动。
 *
 * 放在 TopBar 的 right 里，跟「新建会话」并排。
 * 标签顺带把服务状态说出来（加载中 / 失败），不用另开页面看。
 */
@Composable
fun BackendSwitchButton() {
    val ctx = LocalContext.current
    val mode by BackendSwitch.mode.collectAsState()
    val server by LocalServerStateBus.state.collectAsState()

    val label = when {
        mode == BackendSwitch.Mode.Cloud -> "云端"
        server is LocalServerState.Running -> "本地"
        server is LocalServerState.Failed -> "本地·失败"
        server is LocalServerState.Starting -> "本地·加载中"
        else -> "本地·加载中"
    }

    TextButton(onClick = { BackendSwitch.toggle(ctx) }) {
        Text(label)
    }
}
