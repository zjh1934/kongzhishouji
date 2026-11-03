package com.wangzan101.mika.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 从系统文件选择器挑一个文件。
 *
 * 重写要点（参考 Android SAF 官方文档与 ChatterUI 的「导入并复制」模式）：
 * - **一次只选一个文件**：双文件包分两次选，每次明确是「主模型」还是「视觉投影」
 * - 顺手把显示名和大小读出来，导入前就能做「快失败」预检，不用等拷完 2 GB 才说选错了
 * - 申请持久化读取授权（[Intent.FLAG_GRANT_READ_URI_PERMISSION]），
 *   避免大文件复制到一半 URI 失效；复制完成后由 Controller 释放
 */
data class PickedFile(
    val uri: Uri,
    val displayName: String,
    /** 提供器没给就是 -1。 */
    val size: Long,
)

@Composable
fun rememberFilePicker(onPicked: (PickedFile, Int) -> Unit): (Int) -> Unit {
    val ctx = LocalContext.current
    val which = remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onPicked(describe(ctx, uri), which.intValue)
    }
    return { w ->
        which.intValue = w
        runCatching { launcher.launch(arrayOf("*/*")) }
    }
}

private fun describe(ctx: Context, uri: Uri): PickedFile {
    var name = uri.lastPathSegment.orEmpty()
    var size = -1L
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) c.getString(ni)?.let { name = it }
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
    }
    return PickedFile(uri, name, size)
}
