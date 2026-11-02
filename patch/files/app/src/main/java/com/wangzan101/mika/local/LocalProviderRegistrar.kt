package com.wangzan101.mika.local

import android.content.Context

/**
 * 把本机服务注册成 Mika 里的一个可用模型。
 *
 * **这是"做出来怎么用"的关键一步**。
 *
 * 光加一个预设是不够的：预设只是「添加服务商」表单里的**预填模板**，
 * 用户还是得手动走一遍流程，而那个流程会**要求模型调用一次工具做验证** ——
 * 4B 级别的小模型很可能过不了这一关，于是永远"连不上"。
 *
 * 所以这里直接调 `ProviderStore.connect()`（它是静态方法，
 * `ProviderStore.get(context)` 拿得到 App 共用的那份），
 * 把本机服务**注册成一个已经配好的服务商**，用户直接在模型列表里选它就行。
 *
 * 这也正好贴合 Mika 原本的设计：它对 Ollama / LM Studio 这类"自己跑的服务"
 * 就是当成一个填了地址、不用 Key 的服务商来处理的。
 */
object LocalProviderRegistrar {

    /** 注册进 Mika 的模型名。llama-server 不看这个字段，取个能认出来的就行。 */
    const val MODEL_ID = "local"

    /** 和 ProviderPresets 里那条预设保持一致，免得出现两个入口。 */
    const val BASE_URL = "http://127.0.0.1:8080/v1"

    /**
     * 服务就绪后调一次。
     *
     * 反复调是安全的：地址和名字相同的服务商，`connect` 会更新那一个而不是重复添加。
     */
    fun register(ctx: Context, model: LocalModelCatalog.Model) {
        runCatching {
            val store = com.wangzan101.mika.settings.ProviderStore.get(ctx)
            store.connect(
                name = "本机模型",
                kind = com.wangzan101.mika.settings.ProviderConfig.Kind.OpenAiCompat,
                baseUrl = BASE_URL,
                // 本机服务不要 Key
                apiKey = null,
                models = listOf(
                    com.wangzan101.mika.core.model.ListedModel(
                        id = MODEL_ID,
                        displayName = model.displayName,
                        // 这几个能力要照实说，Mika 会按它决定要不要发图、要不要发 tools
                        toolCalls = true,
                        vision = true,
                        chat = true,
                        contextTokens = LocalMemoryBudget.contextFor(ctx),
                    )
                ),
                // 标成已核实，省掉"选之前先试调用一次工具"那一步
                verified = setOf(MODEL_ID),
                untested = false,
            )
        }.onFailure {
            // 注册失败不影响服务本身在跑，只是用户得手动去添加
            android.util.Log.w("LocalProvider", "注册本机模型失败", it)
        }
    }
}
