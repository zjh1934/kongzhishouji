package com.wangzan101.mika.local

import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 本地模型放在哪儿、装没装好。
 *
 * 重写要点（参考 PocketPal 的「以文件事实校正状态」、Arm Vision Chat 的
 * 「临时目录 + 全校验通过才原子激活」，见 research/模型导入页设计参考.md）：
 * - 下载与导入共用一个校验管道：GGUF 魔数 → 大小 → SHA-256（有官方摘要才算）
 * - 校验结果**结构化**，不再只给一个 Boolean，界面能说清「为什么不行」
 * - 所有写入先落在 `.downloading` / `.importing`，通过后才 rename 到正式名
 * - 没有官方哈希时如实说「结构有效 · 大小匹配」，绝不声称「已校验哈希」
 */
class LocalModelStore(private val root: File) {

    private val dir = File(root, "local-model").apply { mkdirs() }

    fun modelDir(): File = dir

    fun pathOf(file: LocalModelCatalog.File): String = File(dir, file.name).absolutePath

    /** 下载用的临时文件（保留旧名，续传记录就在它上面）。 */
    fun downloadTemp(file: LocalModelCatalog.File): File = File(dir, "${file.name}.downloading")

    /** 导入用的临时文件，与下载的 .part 分开，互不踩。 */
    fun importTemp(file: LocalModelCatalog.File): File = File(dir, "${file.name}.importing")

    fun finalFile(file: LocalModelCatalog.File): File = File(dir, file.name)

    /** 续传断点。页面重进时靠它显示「已下到 62%，可继续」。 */
    fun partialBytes(file: LocalModelCatalog.File): Long =
        downloadTemp(file).takeIf { it.isFile }?.length() ?: 0L

    fun isReady(file: LocalModelCatalog.File): Boolean =
        File(dir, file.name).let { it.isFile && it.length() == file.size }

    fun isModelReady(model: LocalModelCatalog.Model): Boolean = model.files.all { isReady(it) }

    /** 删掉一个模型的全部文件（含两类临时文件）。 */
    fun delete(model: LocalModelCatalog.Model) {
        model.files.forEach { f ->
            finalFile(f).delete()
            downloadTemp(f).delete()
            importTemp(f).delete()
        }
    }

    /** 清掉所有导入残留（下载断点要留着续传，不动）。 */
    fun cleanupImportTemps() {
        dir.listFiles()?.filter { it.name.endsWith(".importing") }?.forEach { it.delete() }
    }

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun availableBytes(): Long = runCatching {
        StatFs(dir.absolutePath).availableBytes
    }.getOrElse { 0L }

    /**
     * 空间够不够：按模型总大小 + 512MB 缓冲（报告建议 5% 或 512MB 取大者，
     * 这里取固定值更保守）。还要确认目录真能写 —— PokeClaw 的 release note
     * 专门把「目录不仅存在而且可写」列为大文件下载前置条件。
     */
    fun hasRoomFor(model: LocalModelCatalog.Model): Boolean =
        availableBytes() >= model.totalBytes + BUFFER_BYTES && dir.canWrite()

    /** 结构化校验结果。每条都能直接变成一句给用户看的话。 */
    sealed interface Validation {
        /** [byHash] = 用了官方 SHA-256 验过；false 表示只验了魔数 + 大小。 */
        data class Ok(val byHash: Boolean) : Validation
        object NotGguf : Validation
        object Empty : Validation
        data class SizeMismatch(val expected: Long, val actual: Long) : Validation
        data class HashMismatch(val expected: String, val actual: String) : Validation
        data class Unreadable(val why: String) : Validation
    }

    /** 快检：只看魔数和大小，不读整个文件。页面每次刷新走这条，不卡。 */
    fun quickCheck(file: File, spec: LocalModelCatalog.File): Validation = try {
        when {
            !file.isFile -> Validation.Unreadable("文件不存在")
            file.length() == 0L -> Validation.Empty
            !magicOk(file) -> Validation.NotGguf
            file.length() != spec.size -> Validation.SizeMismatch(spec.size, file.length())
            else -> Validation.Ok(byHash = false)
        }
    } catch (e: Exception) {
        Validation.Unreadable(e.message.orEmpty().take(120))
    }

    /** 全检：快检 + 有官方摘要时流式算 SHA-256。只在下载/导入完成时跑一次。 */
    fun fullCheck(file: File, spec: LocalModelCatalog.File): Validation {
        val quick = quickCheck(file, spec)
        if (quick !is Validation.Ok) return quick
        if (spec.sha256.isBlank()) return Validation.Ok(byHash = false)
        val actual = try { sha256(file) } catch (e: Exception) {
            return Validation.Unreadable(e.message.orEmpty().take(120))
        }
        return if (actual.equals(spec.sha256, ignoreCase = true)) Validation.Ok(byHash = true)
        else Validation.HashMismatch(spec.sha256, actual)
    }

    /** GGUF 文件头 4 字节必须是 `GGUF`。 */
    fun magicOk(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { r ->
            if (r.length() < 4) return false
            val b = ByteArray(4)
            r.read(b)
            b[0] == 'G'.code.toByte() && b[1] == 'G'.code.toByte() &&
                b[2] == 'U'.code.toByte() && b[3] == 'F'.code.toByte()
        }
    } catch (e: Exception) {
        false
    }

    /** 原子激活：只有校验通过的文件才允许改名到正式路径。 */
    fun activate(temp: File, file: LocalModelCatalog.File): Boolean {
        val final = finalFile(file)
        if (final.exists()) final.delete()
        return temp.renameTo(final)
    }

    /**
     * 兼容旧调用：下载器里用它判断是否算下完。
     * 注意原来写的是 `length() <= size`，短一截也会当成通过 —— 已修成严格相等。
     */
    fun verify(temp: File, file: LocalModelCatalog.File): Boolean =
        fullCheck(temp, file) is Validation.Ok

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

    private companion object {
        const val BUFFER_BYTES = 512L * 1024 * 1024
    }
}
