package com.wangzan101.mika.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** 下载进度。 */
data class DownloadProgress(
    /** 当前文件已下的字节。 */
    val bytes: Long,
    /** 当前文件总字节;服务端没给就是 0。 */
    val total: Long,
    /** 瞬时速度,字节/秒。 */
    val bytesPerSecond: Long,
    /** 正在下第几个文件(从 1 起)。 */
    val fileIndex: Int,
    val fileCount: Int,
) {
    /** 当前文件的百分比;总大小未知时给 -1。 */
    val percent: Int get() = if (total > 0) (bytes * 100 / total).toInt() else -1
}

/** 下载完了的结果。 */
sealed interface DownloadResult {
    data class Ok(val paths: List<String>) : DownloadResult
    /** [retryable] 为 true 时(断网、源抽风)值得再试一次;false 是硬错(空间不够、文件不对)。 */
    data class Failed(val message: String, val retryable: Boolean) : DownloadResult
}

/**
 * 下本地模型。
 *
 * 三件事和别处不一样,所以单独写一套而没复用别的下载器:
 *
 * 1. **魔搭的直链会过期**(10-15 分钟)。3 GB 的文件一次下不完,
 *    所以每次开始/重连都重新取一次直链,不缓存。
 * 2. **必须能续传**。手机上下载动不动就断,从头再来 3 GB 谁也受不了。
 *    靠 HTTP Range:先看下到哪儿了,从那儿接着要。
 * 3. **换源**。主源失败两次就换 hf-mirror,反之亦然;不是一上来就并发打两个源。
 *
 * 下完先算 SHA-256 对摘要,对不上就当没下过(临时文件留着,下次续传)。
 */
class LocalModelDownloader {

    /** 单源连续失败几次就换源。 */
    private val maxAttemptsPerSource = 2

    /** 每次尝试内部,连接中断后最多重连几次。 */
    private val maxResumePerAttempt = 20

    private fun client() = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // 自己处理 302:魔搭要靠 302 的响应体拿直链,跟过去了就看不见了
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun download(
        store: LocalModelStore,
        model: LocalModelCatalog.Model,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val http = client()
        val out = ArrayList<String>(model.files.size)
        model.files.forEachIndexed { index, file ->
            val r = downloadOne(http, store, model, file, index, model.files.size, onProgress)
            if (r != null) return@withContext r
            out += store.pathOf(file)
        }
        DownloadResult.Ok(out)
    }

    /** 下一个文件。返回 null 表示成功。 */
    private suspend fun downloadOne(
        http: OkHttpClient,
        store: LocalModelStore,
        model: LocalModelCatalog.Model,
        file: LocalModelCatalog.File,
        fileIndex: Int,
        fileCount: Int,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult.Failed? {
        val final = File(store.modelDir(), file.name)
        val temp = File(store.modelDir(), "${file.name}.downloading")
        if (final.isFile && final.length() > 0) return null

        for (source in 0..1) {
            for (attempt in 1..maxAttemptsPerSource) {
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                    return DownloadResult.Failed("已取消", retryable = true)
                }
                var resumes = 0
                while (resumes <= maxResumePerAttempt) {
                    val how = writeOnce(
                        http, model, file, temp, source,
                    ) { bytes, total, speed ->
                        onProgress(DownloadProgress(bytes, total, speed, fileIndex + 1, fileCount))
                    }
                    when (how) {
                        is Step.Done -> {
                            if (!store.verify(temp, file)) {
                                // 摘要对不上:留着临时文件,下次续传从断点继续,不从头来
                                return DownloadResult.Failed(
                                    "文件校验未通过,已保留进度", retryable = true,
                                )
                            }
                            if (!temp.renameTo(final)) {
                                return DownloadResult.Failed("无法写入模型文件", retryable = false)
                            }
                            return null
                        }
                        is Step.Broken -> {
                            // 传输中断:原地重连接着下,不跳出当前尝试
                            resumes++
                        }
                        is Step.Dead -> {
                            // 源不可用:跳出,换源或换下一次尝试
                            break
                        }
                    }
                }
            }
        }
        return DownloadResult.Failed(
            "两个源都下不下来:${file.name}", retryable = true,
        )
    }

    /** 一次"从当前断点往下写"的过程。 */
    private fun writeOnce(
        http: OkHttpClient,
        model: LocalModelCatalog.Model,
        file: LocalModelCatalog.File,
        temp: File,
        source: Int,
        onProgress: (bytes: Long, total: Long, speed: Long) -> Unit,
    ): Step {
        val url = resolveUrl(http, model, file, source) ?: return Step.Dead
        val existing = if (temp.isFile) temp.length() else 0L

        val builder = Request.Builder().url(url)
        if (existing > 0) builder.addHeader("Range", "bytes=$existing-")
        val resp = try {
            http.newCall(builder.build()).execute()
        } catch (e: Exception) {
            return Step.Broken
        }

        resp.use { r ->
            val resumed = existing > 0 && r.code == 206
            if (!r.isSuccessful && r.code != 206) return Step.Dead
            if (existing > 0 && !resumed) temp.delete() // 服务端不认 Range,只能从头来
            val body = r.body ?: return Step.Dead

            val start = if (resumed) existing else 0L
            val total = when {
                resumed -> r.header("Content-Range")
                    ?.substringAfterLast("/")?.toLongOrNull()
                    ?: (start + body.contentLength()).coerceAtLeast(file.sizeUpperBound)
                else -> body.contentLength().takeIf { it > 0 } ?: file.sizeUpperBound
            }

            return try {
                val buf = ByteArray(64 * 1024)
                var written = start
                var lastT = System.currentTimeMillis()
                var lastB = start
                FileOutputStream(temp, resumed).use { os ->
                    body.byteStream().use { ins ->
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            val dt = now - lastT
                            if (dt >= 300) {
                                val speed = ((written - lastB) * 1000L) / dt
                                onProgress(written, total, speed)
                                lastT = now
                                lastB = written
                            }
                        }
                    }
                }
                Step.Done
            } catch (e: Exception) {
                Step.Broken
            }
        }
    }

    /** 拿真实下载地址。魔搭要先问一次拿直链;hf-mirror 直接就是直链。 */
    private fun resolveUrl(
        http: OkHttpClient,
        model: LocalModelCatalog.Model,
        file: LocalModelCatalog.File,
        source: Int,
    ): String? {
        if (source == 1) return LocalModelSources.mirror(model.hfRepo, file.name)
        // 主源每次都重新问:直链 10-15 分钟过期,缓存了必然在下大文件中途失效
        val q = LocalModelSources.modelScopeQuery(model.msRepo, file.name)
        return try {
            http.newCall(Request.Builder().url(q).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                LocalModelSources.extractCdnLink(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private sealed interface Step {
        /** 这个文件写完了(还没验摘要)。 */
        object Done : Step
        /** 传输断了,可以原地重连接着下。 */
        object Broken : Step
        /** 这个源不行,换源。 */
        object Dead : Step
    }
}
