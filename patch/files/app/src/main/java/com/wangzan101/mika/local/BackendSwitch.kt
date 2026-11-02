package com.wangzan101.mika.local

import android.content.Context
import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.RoleChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 聊天框里「云端 / 本地」两条路的切换 —— **脚手架版**。
 *
 * 只做三件最小的事，细节后面调：
 * 1. 把 Mika 的主模型用途（[ModelRole.Main]）指向本机服务 / 恢复云端
 * 2. 切到本地才起服务，切走就停（不再一开 App 就白占几个 G）
 * 3. 状态给聊天框顶栏显示
 *
 * **历史天然不丢**：切换只改 ProviderStore 的"用途→配置"映射，
 * 对话内容按 conversationId 存在 ConversationStore，跟这个映射无关。
 * 所以脚手架阶段不用额外做历史迁移。
 *
 * **精简提示词也不用手动切**：`use()` 内部会走 `save()`，
 * 而 `save()` 里挂了 `LocalMode.syncFor(baseUrl)`，按地址自动开/关。
 */
object BackendSwitch {

    enum class Mode { Cloud, Local }

    private val _mode = MutableStateFlow(Mode.Cloud)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /** 切不动时给用户的一句话（比如还没下模型）。 */
    private val _hint = MutableStateFlow<String?>(null)
    val hint: StateFlow<String?> = _hint.asStateFlow()

    /** 切走之前云端主模型是谁，切回来照原样恢复。 */
    private var cloudProviderId: String? = null
    private var cloudModelName: String? = null
    private var cloudCaps: Capabilities? = null

    private const val LOCAL_PREFIX = "http://127.0.0.1:8080"

    fun isLocal(cfgBaseUrl: String) = cfgBaseUrl.startsWith(LOCAL_PREFIX)

    fun toggle(ctx: Context) {
        if (_mode.value == Mode.Cloud) toLocal(ctx) else toCloud(ctx)
    }

    private fun toLocal(ctx: Context) {
        val store = ProviderStore.get(ctx)
        _hint.value = null
        // 显式开开关，不依赖 save() 的副作用：
        // `use()` 只在配置不存在时才调 save()，第二次切换就不经过它了；
        // 冷启动后 roles 从磁盘恢复，更是一次 save 都没有。
        LocalMode.enable()

        // 记住当前云端主模型（只记非本地的，避免反复切换把自己覆盖掉）
        val curId = store.roles.value.resolve(ModelRole.Main)
        val cur = curId?.let { id -> store.configs.value.firstOrNull { it.id == id } }
        if (cur != null && !isLocal(cur.baseUrl)) {
            cloudProviderId = cur.providerId
            cloudModelName = cur.model
            cloudCaps = cur.capabilities
        }

        // 先把本机服务商注册进去（原来只在服务就绪后注册，但脚手架里
        // 服务是切过去才起的，所以这里得先建，不然找不到可指向的配置）
        val model = currentModel(ctx)
        LocalProviderRegistrar.register(ctx, model)

        val provider = store.providers.value.firstOrNull { isLocal(it.baseUrl) }
        if (provider == null) {
            _hint.value = "本机模型还没准备好，先去设置里下载"
            return
        }

        val caps = Capabilities(
            toolCalls = true,
            vision = true,
            contextTokens = LocalMemoryBudget.contextFor(ctx),
            reasoning = false,
        )
        if (!store.use(ModelRole.Main, provider.id, LocalProviderRegistrar.MODEL_ID, caps)) {
            _hint.value = "切不过去：本机服务商登记失败"
            return
        }

        LocalServerService.start(ctx)
        _mode.value = Mode.Local
    }

    private fun toCloud(ctx: Context) {
        val store = ProviderStore.get(ctx)
        _hint.value = null
        // 云端必须用回原版完整提示词，不能留着精简版
        LocalMode.disable()
        val pid = cloudProviderId
        val name = cloudModelName
        val caps = cloudCaps
        val restored = if (pid != null && name != null && caps != null) {
            store.use(ModelRole.Main, pid, name, caps)
        } else {
            // 没记住（比如上次进来主模型已经是本地）就挑第一个非本地配置
            val c = store.configs.value.firstOrNull { !isLocal(it.baseUrl) }
            c?.let { store.use(ModelRole.Main, it.providerId, it.model, it.capabilities) } ?: false
        }
        if (!restored) _hint.value = "没找到云端模型，去设置里添加一个"

        // 真分开：切走就把模型卸了
        LocalServerService.stop(ctx)
        _mode.value = Mode.Cloud
    }

    private fun currentModel(ctx: Context): LocalModelCatalog.Model {
        val prefs = ctx.getSharedPreferences("local_model", Context.MODE_PRIVATE)
        return prefs.getString("choice", null)?.let { LocalModelCatalog.byId(it) }
            ?: LocalModelCatalog.recommend(LocalMemoryBudget.ramGb(ctx))
            ?: LocalModelCatalog.DEFAULT
    }
}
