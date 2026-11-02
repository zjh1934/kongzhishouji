package com.wangzan101.mika.local

/**
 * 本地模式的开关。
 *
 * 只在**当前主模型配的是本机服务**时才开。开了以后：
 * 1. 系统提示词换成 [LocalPrompt] 那份精简版（原版 9,458 token 塞不进本地上下文）
 * 2. 工具集收敛到核心那一批（79 个全给 4B 模型会挑花眼）
 *
 * 为什么要个全局开关：`SystemPrompt.identity()` 是个无参静态方法，
 * 它在模型层之上、拿不到本轮的配置。用一个进程内的标记最省事，
 * 改动面也只有 `identity()` 那一行。
 */
object LocalMode {

    @Volatile
    private var on: Boolean = false

    fun enable() { on = true }

    fun disable() { on = false }

    val enabled: Boolean get() = on

    /**
     * 按当前主模型的地址判断。
     *
     * 只看 host 是不是本机 —— 用户也可能自己配了 Ollama（11434），
     * 那个同样是本地小模型，也该走精简提示词。
     */
    fun syncFor(baseUrl: String?) {
        val host = baseUrl
            ?.substringAfter("://", "")
            ?.substringBefore("/")
            ?.substringBefore(":")
        on = host == "127.0.0.1" || host == "localhost" || host == "::1"
    }
}
