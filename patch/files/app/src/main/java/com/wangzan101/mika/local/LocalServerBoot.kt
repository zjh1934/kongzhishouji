package com.wangzan101.mika.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File

/**
 * App 一启动就把本机服务拉起来。
 *
 * 为什么要这个：服务预设只是让 Mika 去连 `127.0.0.1:8080`，
 * 但那个端口上的 llama-server 得有人去启动。不自动起的话，
 * 用户选了「本机模型」却连不上，会很困惑。
 *
 * 为什么是个空的 ContentProvider：
 * **ContentProvider 是安卓里唯一"不用改 Application 类就能在启动时自动跑"的钩子** ——
 * 系统建进程时会主动实例化它。这是各家 SDK（Firebase、Facebook）的老办法，
 * 好处是**不用给工程加 androidx.startup 依赖**：加依赖 = 改 build.gradle
 * = 升级时多一处冲突。Mika 本身没有 startup 依赖，我们也不该为这点事加一个。
 *
 * 只是**发个请求**让它起来，不阻塞启动：加载 4B 视觉模型要二三十秒，
 * 在主线程等会把 App 卡死。
 */
class LocalServerBoot : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        Thread {
            runCatching {
                val prefs = app.getSharedPreferences("local_model", android.content.Context.MODE_PRIVATE)
                val id = prefs.getString("choice", null) ?: return@Thread
                val model = LocalModelCatalog.byId(id) ?: return@Thread
                val store = LocalModelStore(File(app.getExternalFilesDir(null), "models"))
                // 没下好就别白费劲
                if (!store.isModelReady(model)) return@Thread
                LocalServerManager(app).startAsync(
                    modelPath = store.pathOf(model.main),
                    mmprojPath = store.pathOf(model.mmproj),
                )
            }
        }.start()
        return false
    }

    // 下面这些用不到，它只是借 ContentProvider 的时机跑一下 onCreate
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
