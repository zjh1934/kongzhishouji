package com.wangzan101.mika.local

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

/**
 * 从系统文件选择器挑一个文件进来，返回 `pick(0)` = 挑主模型、`pick(1)` = 挑视觉投影。
 *
 * 用它是因为 3 GB 的模型在 App 内下不一定顺：用户可能想用别的工具下，
 * 或者在电脑上下好传到手机。挑进来我们再校验，对不上就不收。
 */
@Composable
fun rememberFilePicker(onPicked: (Uri, Int) -> Unit): (Int) -> Unit {
    val which = remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(uri, which.intValue)
    }
    return { w ->
        which.intValue = w
        runCatching { launcher.launch(arrayOf("*/*")) }
    }
}
