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
