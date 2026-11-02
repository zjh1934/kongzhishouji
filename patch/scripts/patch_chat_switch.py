#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""聊天框顶栏加「云端/本地」切换按钮（脚手架版，只改一处）。"""
import io

PATH = "app/src/main/java/com/wangzan101/mika/ui/ChatScreen.kt"

# 用全限定名调按钮，省得动 import 区（上游改 import 顺序时补丁不易失效）
OLD = ('                right = { BarIconButton(MikaIcons.New, '
       'stringResource(R.string.chat_new), { vm.newConversation() }, '
       'enabled = vm.canSwitch()) },')
NEW = ('                right = {\n'
       '                    com.wangzan101.mika.local.BackendSwitchButton()\n'
       '                    BarIconButton(MikaIcons.New, '
       'stringResource(R.string.chat_new), { vm.newConversation() }, '
       'enabled = vm.canSwitch())\n'
       '                },')

with io.open(PATH, encoding="utf-8") as f:
    s = f.read()

if "BackendSwitchButton" in s:
    print("  聊天框切换按钮已存在，跳过")
elif OLD not in s:
    print("  ! TopBar 的 right 那一行没找到 —— 上游可能改过，需人工确认")
else:
    with io.open(PATH, "w", encoding="utf-8") as f:
        f.write(s.replace(OLD, NEW, 1))
    print("  聊天框切换按钮已加")
