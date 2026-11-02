#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地模式的补丁。

分开写成 .py 而不是塞在 apply.sh 的 heredoc 里，是因为嵌套转义太容易出错
（刚才就因为 \\\\n 的层数不对，整段脚本语法错）。

改三处，都是最小侵入：
1. `SystemPrompt.identity()` —— 本地模式下换精简提示词，其余走原路径
2. `SystemPrompt.assemble()` —— 本地模式**丢掉能力段落**（见下方说明，这是
   真机实测 8358 token 超 6144 的根因）
3. `ProviderStore.save()` —— 存服务商时按地址同步本地模式开关（兜底）

## 为什么要动 assemble()
`AgentLoop.kt` 里是 `SystemPrompt.assemble(basePrompt, tools.promptSections(specs))`。
只改 identity() 砍掉的是 basePrompt，但后面还拼了**一大堆能力段落**
（capabilities/ 目录 37KB，按工具组注入）。真机实测请求 8358 token，
而我们按"精简提示词 498 + 工具 1194 + 截图 1200 + 历史 1500"算的是 4893 ——
**漏算了 sections**。本地模式必须连 sections 一起丢掉。
"""
import io
import os
import sys

OK, MISS, SKIP = "  ", "  ", "  "


def edit(path, fn):
    if not os.path.exists(path):
        print("  ! 文件不存在: %s" % path)
        return False
    with io.open(path, encoding="utf-8") as f:
        s = f.read()
    out = fn(s)
    if out is None:
        return False
    if out != s:
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(out)
    return True


# ── 1. 系统提示词 ────────────────────────────────────────────
SP = "core/src/main/kotlin/com/wangzan101/mika/core/agent/SystemPrompt.kt"
OLD = 'fun identity(): String = listOf(Prompts.system("identity"), Prompts.system("principles"), read(GENERAL)).joinToString("\\n\\n")'
NEW = (
    'fun identity(): String = com.wangzan101.mika.local.LocalPrompt\n'
    '        .takeIf { com.wangzan101.mika.local.LocalMode.enabled }?.text\n'
    '        ?: listOf(Prompts.system("identity"), Prompts.system("principles"), read(GENERAL)).joinToString("\\n\\n")'
)


def patch_prompt(s):
    if "LocalPrompt" in s:
        print("  提示词 hook 已存在，跳过")
        return None
    if OLD not in s:
        print("  ! identity() 原文没找到 —— 上游可能改过，需人工确认")
        return None
    print("  提示词 hook 已加（本地模式走精简版）")
    return s.replace(OLD, NEW, 1)


# ── 2. 组装时丢掉能力段落 ────────────────────────────────────
ASM_OLD = 'fun assemble(identity: String, sections: List<String>): String = (listOf(identity) + sections).joinToString("\\n\\n")'
ASM_NEW = (
    'fun assemble(identity: String, sections: List<String>): String =\n'
    '        // 本地小模型上下文装不下能力段落（37KB），只给精简的 identity\n'
    '        if (com.wangzan101.mika.local.LocalMode.enabled) identity\n'
    '        else (listOf(identity) + sections).joinToString("\\n\\n")'
)


def patch_assemble(s):
    if "LocalMode.enabled" in s and "fun assemble" in s and "else (listOf(identity)" in s:
        print("  assemble hook 已存在，跳过")
        return None
    if ASM_OLD not in s:
        print("  ! assemble() 原文没找到 —— 上游可能改过，需人工确认")
        return None
    print("  assemble hook 已加（本地模式丢掉能力段落）")
    return s.replace(ASM_OLD, ASM_NEW, 1)


# ── 3. 开关同步 ──────────────────────────────────────────────
PS = "app/src/main/java/com/wangzan101/mika/settings/ProviderStore.kt"
ANCHOR = "    fun save(cfg: ProviderConfig, apiKey: String?) {"
CALL = "        com.wangzan101.mika.local.LocalMode.syncFor(cfg.baseUrl)"


def patch_switch(s):
    if "LocalMode" in s:
        print("  开关同步已存在，跳过")
        return None
    if ANCHOR not in s:
        print("  ! save() 没找到 —— 需人工确认")
        return None
    print("  开关同步已加（存服务商时按地址判断）")
    return s.replace(ANCHOR, ANCHOR + "\n" + CALL, 1)


if __name__ == "__main__":
    edit(SP, patch_prompt)
    edit(SP, patch_assemble)
    edit(PS, patch_switch)
