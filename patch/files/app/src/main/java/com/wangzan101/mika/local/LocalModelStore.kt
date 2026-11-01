package com.wangzan101.mika.local

import android.os.StatFs
import java.io.File
import java.security.MessageDigest

/**
 * 本地模型放在哪儿、装没装好。
 *
 * 位置从 [android.content.Context.getExternalFilesDir] 起算:卸载 App 时系统会一起清掉,
 * 不用我们操心;也不会去占公共下载目录。
 */
class LocalModelStore(private val root: File) {

    private val dir = File(root, "local-model").apply { mkdirs() }

    /** 模型目录。 */
    fun modelDir(): File = dir

    /** 文件下好了没有:目标文件存在且大小对得上才算(摘要在下完时已经验过)。 */
    fun isReady(file: LocalModelCatalog.File): Boolean =
        File(dir, file.name).let { it.isFile && it.length() > 0 }

    /** 一个模型两个文件都在才算装好。 */
    fun isModelReady(model: LocalModelCatalog.Model): Boolean =
        model.files.all { isReady(it) }

    fun pathOf(file: LocalModelCatalog.File): String = File(dir, file.name).absolutePath

    /** 删掉一个模型的全部文件(含没下完的临时文件)。 */
    fun delete(model: LocalModelCatalog.Model) {
        model.files.forEach { f ->
            File(dir, f.name).delete()
            File(dir, "${f.name}.downloading").delete()
        }
    }

    /** 已占空间。 */
    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** 还剩多少空间。 */
    fun availableBytes(): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes
    }.getOrElse { 0L }

    /** 空间够不够下一个模型:按上界再加两成余量算。 */
    fun hasRoomFor(model: LocalModelCatalog.Model): Boolean =
        availableBytes() >= model.requiredBytes

    /**
     * 校验下完的文件:有摘要就比对 SHA-256,没摘要就只比对大小。
     * 摘要才是准的 —— 大小对上但内容坏了的情况(传输出错、CDN 返回错文件)大小拦不住。
     */
    fun verify(temp: File, file: LocalModelCatalog.File): Boolean {
        if (!temp.isFile || temp.length() <= 0) return false
        if (file.sha256.isBlank()) return temp.length() <= file.sizeUpperBound
        return sha256(temp).equals(file.sha256, ignoreCase = true)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        file.inputStream().buffered().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
