package com.wangzan101.mika.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * 保留这个 ContentProvider 的注册位，但**不再自动加载模型**。
 *
 * 原来这里一开 App 就把几个 G 的模型拉起来 —— 哪怕用户正在用云端。
 * 需求定的是「云端 / 本地两条路真分开」，所以改成由
 * [BackendSwitch]（聊天框里切到本地）来驱动启停，切走就卸掉。
 *
 * 保留空壳是因为 Manifest 里已经注册了，删注册又要多改一处；
 * 留着不干活，将来要加启动时的轻量检查（比如扫孤儿进程）也有个落点。
 */
class LocalServerBoot : ContentProvider() {

    override fun onCreate(): Boolean = false

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
