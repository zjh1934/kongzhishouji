package com.wangzan101.mika.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 最小探针：**不带 tools、不带图片、十几个字**地对本机 8080 发一次请求。
 *
 * 为什么要有它（research/模型调用链路对照.md 的结论）：
 * Mika 的每次请求都带 `tools` 数组，且 `capabilities.vision` 打开时还会带
 * base64 截图。端侧 CPU 上，视觉塔 prefill / 一大坨 JSON schema 可能让
 * llama-server 在几分钟内一个 SSE delta 都不发，触发 OkHttp 120s 读超时
 * × RetryPolicy 3 次重试 —— 表现就是「挂 6 分钟、0 Token、0 ModelReply」。
 *
 * 这个探针把变量全去掉，只留"模型能不能回一句话"：
 * - **秒回** → 服务和模型本身没问题，是 Mika 发过去的请求体里有东西把它卡住
 *   （按报告结论：vision / tools）
 * - **也卡** → 问题在 llama-server 或模型本身，去看进程输出和启动参数
 *
 * 用独立 OkHttpClient，不走 Mika 的 NetProxy，避免代理配置干扰 localhost。
 * 用 `stream:false` 整段返回，省掉 SSE 解析这层变量。
 */
object LocalProbe {

    private const val URL = "http://127.0.0.1:8080/v1/chat/completions"

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** 给用户看的一行结果：耗时 + 回复（或错误）。 */
    suspend fun run(): String {
        val body = """{"model":"local","messages":[{"role":"user","content":"只回复一个字：好"}],"stream":false,"max_tokens":16}"""
        val req = Request.Builder().url(URL)
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val t0 = System.currentTimeMillis()
        return try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - t0
                if (!resp.isSuccessful) {
                    "HTTP ${resp.code}（${ms}ms）：${text.take(200)}"
                } else {
                    val content = runCatching {
                        Json.parseToJsonElement(text).jsonObject["choices"]
                            ?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("message")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.content
                    }.getOrNull()
                    "✅ ${ms}ms　回复：${content?.take(120) ?: "（空）"}"
                }
            }
        } catch (e: Exception) {
            "❌ ${System.currentTimeMillis() - t0}ms　${e.javaClass.simpleName}: ${e.message?.take(160)}"
        }
    }
}
