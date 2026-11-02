#!/usr/bin/env bash
# P1 身份改造补丁。cwd 必须是 mika/（CI 里 repo/ 与 mika/ 平级）
set -e
echo "== P1: 换身份 + 固定签名 =="

# ── 0. 新增源码文件（本地模型模块）─────────────────────────────
if [ -d ../repo/patch/files ]; then
    cp -r ../repo/patch/files/. .
    echo "  已放入新增源码："
    find ../repo/patch/files -type f | sed 's|.*patch/files/|    |'
fi

# ── 1. applicationId ───────────────────────────────────────────
# 只改安装身份，namespace 保持 com.wangzan101.mika 不动：
# 这样源码包名和 R 类都不用动，改动面最小。
python3 - <<'PY'
import re, os
p = 'app/build.gradle.kts'
s = open(p, encoding='utf-8').read()
assert 'applicationId = "com.wangzan101.mika"' in s, 'applicationId 未找到'
s = s.replace('applicationId = "com.wangzan101.mika"', 'applicationId = "com.kongzhishouji.mika"')
# versionCode：原版是 34，用 34 + run_number 保证严格递增
vc = 34 + int(os.environ.get('BUILD_NUMBER', '1'))
s2 = re.sub(r'versionCode = \d+', 'versionCode = %d' % vc, s)
assert s2 != s or True
s = s2
open(p, 'w', encoding='utf-8').write(s)
print('  applicationId -> com.kongzhishouji.mika ; versionCode -> %d' % vc)
PY

# ── 2. 应用名 ─────────────────────────────────────────────────
python3 - <<'PY'
import re
p = 'app/src/main/res/values/strings.xml'
s = open(p, encoding='utf-8').read()
s2 = re.sub(r'(<string name="app_name"[^>]*>)Mika(</string>)', r'\1Mika Local\2', s)
assert s2 != s, 'app_name 未替换'
open(p, 'w', encoding='utf-8').write(s2)
print('  app_name -> Mika Local')
PY

# ── 3. 更新检查改指到我们仓库 ─────────────────────────────────
# MikaLinks 是唯一地址常量：RELEASES / LATEST_RELEASE_API /
# ApkIntegrity.requireOfficialUrl 都由它派生，改这一处全部跟着走。
python3 - <<'PY'
p = 'app/src/main/java/com/wangzan101/mika/about/MikaLinks.kt'
s = open(p, encoding='utf-8').read()
n = 0
for old, new in [
    ('const val REPO = "https://github.com/wangzan101/Mika"',
     'const val REPO = "https://github.com/zjh1934/kongzhishouji"'),
    ('const val REPO_LABEL = "github.com/wangzan101/Mika"',
     'const val REPO_LABEL = "github.com/zjh1934/kongzhishouji"'),
]:
    assert old in s, old[:40] + ' 未找到'
    s = s.replace(old, new); n += 1
open(p, 'w', encoding='utf-8').write(s)
print('  MikaLinks -> zjh1934/kongzhishouji (%d 处)' % n)
PY

# ── 3.5 引入 native 构建（P3a 空壳）────────────────────────────
# Mika 原本没有任何 CMake/NDK 配置，这是第一次加。
# 先只编一个空壳 .so，验证链路；真编 llama.cpp 是下一步。
python3 - <<'PY'
p = 'app/build.gradle.kts'
s = open(p, encoding='utf-8').read()

if 'externalNativeBuild' not in s:
    # defaultConfig 内：只留 arm64（本地模型推理和原来的沙箱都是 arm64-only）
    anchor = '        applicationId = "com.kongzhishouji.mika"'
    assert anchor in s, 'applicationId 未找到（P1 是否已应用？）'
    s = s.replace(anchor, anchor + '''
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }''', 1)

    # android {} 层：CMake 入口
    a2 = '    buildFeatures {'
    assert a2 in s, 'buildFeatures 未找到'
    s = s.replace(a2, '''    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

''' + a2, 1)

    open(p, 'w', encoding='utf-8').write(s)
    print('  native 构建已接入（CMake + arm64）')
else:
    print('  native 构建已存在，跳过')
PY

# ── 3.7 放入 llama.cpp 运行时（P3b）────────────────────────────
# 用官方 android-arm64 预编译包，不自己编。
# 两个关键点：
#   1. 可执行文件改名为 lib*.so —— 安卓 10 起 app 数据目录不能 execve，
#      只有叫这个名字才会被解到 nativeLibraryDir
#   2. 必须 strip —— 原始 274MB，strip 后 29MB（实测 10%）
LLAMA_TAG="${LLAMA_TAG:-b12032}"
if [ -f app/src/main/jniLibs/arm64-v8a/libllamaserver.so ]; then
    echo "  运行时已存在，跳过"
else
    echo "  拉取 llama.cpp $LLAMA_TAG ..."
    mkdir -p /tmp/llama && cd /tmp/llama
    curl -sSL -o a.tar.gz \
      "https://github.com/ggml-org/llama.cpp/releases/download/$LLAMA_TAG/llama-$LLAMA_TAG-bin-android-arm64.tar.gz"
    mkdir -p x && tar xzf a.tar.gz -C x
    SRC=$(find x -maxdepth 1 -type d -name 'llama-b*' | head -1)
    echo "    解包目录: $SRC"
    sudo apt-get install -y -qq llvm >/dev/null 2>&1 || true
    STRIP=$(which llvm-strip || which strip)
    mkdir -p out
    # 服务端要用的：启动器 + 实现 + 推理核心 + 视觉 + ggml 全家（含各 CPU 变体）
    NEEDED="llama-server libllama-server-impl.so libllama-common.so libllama.so libmtmd.so \
            libggml.so libggml-base.so libggml-rpc.so \
            libggml-cpu-android_armv8.0_1.so libggml-cpu-android_armv8.2_1.so libggml-cpu-android_armv8.2_2.so \
            libggml-cpu-android_armv8.6_1.so libggml-cpu-android_armv9.0_1.so libggml-cpu-android_armv9.2_1.so \
            libggml-cpu-android_armv9.2_2.so"
    for f in $NEEDED; do
        [ -f "$SRC/$f" ] || { echo "    缺 $f，跳过"; continue; }
        out_name="$f"
        # 可执行启动器改名，才会被解出来
        [ "$f" = "llama-server" ] && out_name="libllamaserver.so"
        cp "$SRC/$f" "out/$out_name"
        "$STRIP" --strip-unneeded "out/$out_name" 2>/dev/null || true
    done
    mkdir -p "$OLDPWD/app/src/main/jniLibs/arm64-v8a"
    cp out/*.so "$OLDPWD/app/src/main/jniLibs/arm64-v8a/"
    cd "$OLDPWD"
    echo "  运行时就位："
    ls -la app/src/main/jniLibs/arm64-v8a/ | head -20
    du -sh app/src/main/jniLibs/arm64-v8a/
fi

# ── 3.8 服务预设 + 设置页入口（P4）────────────────────────────
# 核心一行：把本机服务当成一个"填地址的服务商"。
# Mika 本来就有 Ollama / LM Studio 两个这样的预设，我们照它的范式加一个。
# 这样 buildModel() 一个字都不用改，直接走现成的 OpenAiCompatModel。
python3 - <<'PY'
p = 'app/src/main/java/com/wangzan101/mika/settings/ProviderPresets.kt'
s = open(p, encoding='utf-8').read()
anchor = '        ProviderPreset("自定义", ProviderConfig.Kind.OpenAiCompat, "", caps()),'
assert anchor in s, '自定义 preset 未找到'
if '本机模型' not in s:
    s = s.replace(anchor,
        '        // 手机上自己跑的模型:地址是本机的服务,不要 Key;能看图(视觉由 mmproj 提供)\n'
        '        ProviderPreset("本机模型", ProviderConfig.Kind.OpenAiCompat, "http://127.0.0.1:8080/v1", caps(vision = true), selfHosted = true),\n'
        + anchor, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('  已加「本机模型」预设')
else:
    print('  预设已存在，跳过')
PY

# 导航：加一个页面。Route 是 enum，when 是语句不是表达式，加枚举不会破坏穷举性。
python3 - <<'PY'
p = 'app/src/main/java/com/wangzan101/mika/ui/Navigation.kt'
s = open(p, encoding='utf-8').read()
old = 'enum class Route { Chat, Settings, Memory, Plans, Browser, Run, Source, PhoneSetup, Models, SpeechModel, Welcome, ModelSetup, About, License, Screens }'
assert old in s, 'Route 枚举未找到'
if 'LocalModel' not in s:
    s = s.replace(old, old.replace(', Screens }', ', Screens, LocalModel }'), 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('  Route 已加 LocalModel')
else:
    print('  Route 已有 LocalModel，跳过')
PY

# when 分支
python3 - <<'PY'
p = 'app/src/main/java/com/wangzan101/mika/ui/MainActivity.kt'
s = open(p, encoding='utf-8').read()
anchor = '        Route.Models -> ModelsScreen(store, onBack = back, onOpenSpeech = { nav.push(Route.SpeechModel) })'
assert anchor in s, 'Route.Models 分支未找到'
if 'Route.LocalModel ->' not in s:
    s = s.replace(anchor, anchor +
        '\n        Route.LocalModel -> com.wangzan101.mika.local.LocalModelScreen(onBack = back)', 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('  MainActivity 已加 LocalModel 分支')
else:
    print('  MainActivity 分支已存在，跳过')
PY

# 设置页入口：在「模型」那张卡片里加一行
#
# 教训（已经踩过两次，别再改回去）：
# 1. 那行 ListRow 是多行的（结尾带 lambda），不能"插在它后面"，会插进 lambda 体内
# 2. 那行**不在** `SettingsScreen` 里，而在 `SettingsContent` 里 ——
#    SettingsScreen 只是个壳，按页面状态分派给 SettingsContent。
#    所以参数要加到 SettingsContent，再从 SettingsScreen 透传下去
python3 - <<'PYEOF'
import re
p = 'app/src/main/java/com/wangzan101/mika/ui/SettingsScreen.kt'
s = open(p, encoding='utf-8').read()
if 'onOpenLocalModel' in s:
    print('  设置页入口已存在，跳过')
else:
    # 1) 找 SettingsContent 的签名
    mc = re.search(r'^fun SettingsContent\(', s, re.M)
    assert mc, 'SettingsContent 定义未找到'

    # 2) 在它体内（不是全文件）找「模型」那一行
    row = re.compile(r'^(\s*)ListRow\(MikaIcons\.Spark, stringResource\(R\.string\.settings_model\).*$', re.M)
    m2 = row.search(s, mc.start())
    assert m2, '模型行未找到'

    # 3) 插在它前面，并把它的 first = true 挪给新行
    indent = m2.group(1)
    s = s[:m2.start()] + (
        indent + 'ListRow(MikaIcons.Spark, "本机模型", "不联网，在手机上跑",\n'
        + indent + '    first = true, onClick = onOpenLocalModel) { RowChevron() }\n'
        + m2.group(0).replace('first = true, ', '')
    ) + s[m2.end():]

    # 4) SettingsContent 签名加参数（放在 onOpenModels 后面，跟着其它 onOpenX）
    mc = re.search(r'^fun SettingsContent\(', s, re.M)
    i = s.index('    onOpenModels: () -> Unit,', mc.start())
    s = s[:i] + '    onOpenModels: () -> Unit,\n    onOpenLocalModel: () -> Unit,\n' + s[i + len('    onOpenModels: () -> Unit,\n'):]

    # 5) SettingsScreen 签名也加一个，好从外面传进来
    ms = re.search(r'^fun SettingsScreen\(', s, re.M)
    j = s.index('    onOpenModels: () -> Unit,', ms.start())
    s = s[:j] + '    onOpenModels: () -> Unit,\n    onOpenLocalModel: () -> Unit,\n' + s[j + len('    onOpenModels: () -> Unit,\n'):]

    # 6) 调用处透传：SettingsContent(...) 里的 onOpenModels = onOpenModels
    a = 'onOpenModels = onOpenModels,'
    assert a in s, 'SettingsContent 调用处的 onOpenModels 未找到'
    s = s.replace(a, a + '\n            onOpenLocalModel = onOpenLocalModel,', 1)

    open(p, 'w', encoding='utf-8').write(s)
    # 4 处：两个签名各一个参数、调用处一行里出现两次、使用处一个
    assert s.count('onOpenLocalModel') == 5, '入口数不对: %d' % s.count('onOpenLocalModel')
    print('  设置页已加入口（SettingsScreen -> SettingsContent 透传）')
PYEOF

# MainActivity 里 SettingsScreen(...) 的实参
python3 - <<'PYEOF'
p = 'app/src/main/java/com/wangzan101/mika/ui/MainActivity.kt'
s = open(p, encoding='utf-8').read()
if 'onOpenLocalModel = ' in s:
    print('  调用处已传参，跳过')
else:
    a = 'onOpenModels = { nav.push(Route.Models) }'
    assert a in s, 'onOpenModels 调用处未找到'
    s = s.replace(a, a + ',\n                onOpenLocalModel = { nav.push(Route.LocalModel) }', 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('  调用处已传 onOpenLocalModel')
PYEOF

# ── 4. 固定签名 ───────────────────────────────────────────────
# Actions 每次是新 VM，AGP 自动生成的 debug.keystore 每次都换，
# 签名不一致就装不上。所以自己造一把、提交回仓库复用。
if [ -f ../repo/keystore.jks ]; then
    cp ../repo/keystore.jks app/keystore.jks
    echo "  复用仓库里的 keystore"
else
    keytool -genkeypair -v -keystore app/keystore.jks -alias mikalocal \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass mikalocal -keypass mikalocal \
        -dname "CN=Mika Local, OU=Dev, O=KongZhiShouJi, C=CN" 2>&1 | tail -3
    echo "  已生成新 keystore"
fi
mkdir -p ../repo && cp app/keystore.jks ../repo/keystore.jks

python3 - <<'PY'
p = 'app/build.gradle.kts'
s = open(p, encoding='utf-8').read()
anchor = '    defaultConfig {'
assert anchor in s and 'create("fixed")' not in s
inject = '''    signingConfigs {
        create("fixed") {
            storeFile = file("keystore.jks")
            storePassword = "mikalocal"
            keyAlias = "mikalocal"
            keyPassword = "mikalocal"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
        }
    }

'''
s = s.replace(anchor, inject + anchor, 1)
bt = '    buildTypes {\n'
assert bt in s
s = s.replace(bt, bt +
    '        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }\n'
    '        getByName("release") { signingConfig = signingConfigs.getByName("fixed") }\n', 1)
open(p, 'w', encoding='utf-8').write(s)
print('  签名配置已注入（fixed / v1+v2 / v3 off）')
PY

echo "== P1 补丁应用完成 =="
