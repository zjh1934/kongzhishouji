package com.wangzan101.mika.local

/**
 * 本地模式专用的精简系统提示词。
 *
 * **为什么要有它**（这是整个项目里最关键的一个判断）：
 *
 * Mika 原版每次必给的三段是
 * `identity.md`(1,163) + `principles.md`(11,797) + `general.md`(1,227)
 * = **14,187 字符 ≈ 9,458 token**。那篇 `principles.md` 是写给强模型读的长篇散文
 * （通篇没有一个 `#` 标题）。
 *
 * 而 4B 本地模型在 16GB 手机上最多能开 8192 上下文（再大 KV cache 就扛不住）：
 * 权重 2.95GB + 8192×360KB = 5.76GB，已接近上限。
 * **9,458 的提示词塞进 8192 的上下文 —— 连提示词都装不下，更别说截图和历史。**
 *
 * 所以这一份压到 **2,000 token 量级**，按
 * 提示词 2000 + 工具入口 1200 + 截图 1200 + 历史 1500 + 输出 500 ≈ 6,400 token 的预算写，
 * 8192 装得下。
 *
 * 结构照 PokeClaw `LOCAL_TASK_PROMPT` 的写法（怎么干 / 工具怎么选 / 规矩），
 * 但**工具名换成 Mika 自己的** —— 它提示词里的 `send_message(contact=)` 依赖联系人权限，
 * Mika 既没那工具也没那权限，照抄会让模型去调不存在的东西。
 */
object LocalPrompt {

    /**
     * 手机屏的工具名前缀。
     *
     * Mika 的规则（见 `LegacyNames` 注释）：能力的工具名是 `<能力>_<其余>`；
     * 几块屏都有的动词（tap 等）以前只有一个不带前缀的名字，现在每块屏一个。
     * ⇒ 手机屏上现在是 `phone_tap`，不是 `tap`。
     *
     * 万一以后改回去，提示词里那几行会失效 —— 所以末尾加了一句
     * "工具列表里有什么就用什么"，模型能自己纠回来。
     */
    private const val P = "phone_"

    /** 工具名：核心工具（不带前缀）与手机工具（带前缀）。 */
    private const val OBSERVE = "${P}observe"
    private const val TAP = "${P}tap"
    private const val TYPE = "${P}type_text"
    private const val SCROLL = "${P}scroll"
    private const val SWIPE = "${P}swipe"
    private const val BACK = "${P}back"
    private const val LAUNCH = "${P}launch_app"
    private const val APPS = "${P}list_apps"
    private const val WAIT = "${P}wait"
    private const val FIND = "${P}find"
    private const val SHOT = "screenshot"
    private const val ASK = "ask_user"
    private const val GIVE_UP = "end_not_done"

    /**
     * 本地模式的系统提示词。
     *
     * 顺着 PokeClaw 的实测结论写：**一次只调一个工具**、**改名优先于记 ref**、
     * **失败两次就换办法**。第三点对小模型尤其重要 —— 它最爱原地打转。
     */
    val text: String = """
你是手机上的助手，用工具替用户把事办成。说完要如实讲清楚发生了什么。

## 怎么干
1. 先用 $OBSERVE 看屏幕上现在有什么
2. 挑一个工具调用
3. 看结果，判断下一步
4. 办成了就说明做了什么、证据是什么

## 工具怎么选
- 看屏幕 → $OBSERVE
- 点某个东西 → $TAP
- 输入文字 → $TYPE
- 上下翻 → $SCROLL
- 左右滑、切页面 → $SWIPE
- 返回上一页 → $BACK
- 打开某个 App → $APPS 查包名，再 $LAUNCH
- 等它加载完 → $WAIT
- 列表里往下找目标 → $FIND
- 文字读不出来、要看图 → $SHOT
- 缺信息、要用户拿主意 → $ASK
- 实在办不成 → $GIVE_UP，说清卡在哪、屏幕现在什么样

**工具列表里有什么就用什么**，名字以列表为准。

## 点哪个元素
- 优先用**名字**：`$TAP(element="发送")`。名字要跟屏幕上显示的完全一致。
- 名字有歧义（几个都像）时，结果会列出来给你挑，挑一个再点。
- ref（@eN 那种）是备选，名字说不清时才用。

## 规矩
- **一次只调一个工具**，调完看结果再决定下一步。
- **同一个动作失败两次就换办法**，不要原样重试。
- **没看到证据就不能说办成了**。点完要再看一眼屏幕确认。
- **不确定就问**，不要猜。猜出来的东西当成事实说出来是最坏的。
- 屏幕上弹了东西挡路、要登录、要验证，不要硬闯，说清楚现状。

## 注意
你是本地小模型，能力有限。简单的活（打开 App、点几下、翻一翻）能做；
步骤多、要判断的活做不好也不丢人，**如实说做不到比硬做更负责任**。
""".trim()
}
