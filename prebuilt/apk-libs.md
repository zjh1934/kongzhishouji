# 本次构建的 APK 内容（run 33, 2026-11-03）

APK 体积: 105179207 字节

## lib/ 下的原生库
```
    10096  1981-01-01 01:01   lib/arm64-v8a/libandroidx.graphics.path.so
  1079880  1981-01-01 01:01   lib/arm64-v8a/libggml-base.so
   935424  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv8.0_1.so
   969744  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv8.2_1.so
   973488  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv8.2_2.so
   985080  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv8.6_1.so
   989304  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv9.0_1.so
   989304  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv9.2_1.so
   989304  1981-01-01 01:01   lib/arm64-v8a/libggml-cpu-android_armv9.2_2.so
   834816  1981-01-01 01:01   lib/arm64-v8a/libggml-rpc.so
   666424  1981-01-01 01:01   lib/arm64-v8a/libggml.so
  6113544  1981-01-01 01:01   lib/arm64-v8a/libllama-common.so
  8187072  1981-01-01 01:01   lib/arm64-v8a/libllama-server-impl.so
  3935952  1981-01-01 01:01   lib/arm64-v8a/libllama.so
     4608  1981-01-01 01:01   lib/arm64-v8a/libllamaserver.so
     4336  1981-01-01 01:01   lib/arm64-v8a/libmikalocal.so
    16472  1981-01-01 01:01   lib/arm64-v8a/libmikarun.so
 11064544  1981-01-01 01:01   lib/arm64-v8a/libmlkit_google_ocr_pipeline.so
  1619528  1981-01-01 01:01   lib/arm64-v8a/libmtmd.so
   181824  1981-01-01 01:01   lib/arm64-v8a/libproot.so
    17672  1981-01-01 01:01   lib/arm64-v8a/libproot_loader.so
```
## APK 自检

体积: 105179207 字节

### zip 完整性
```
OK：全部条目 CRC 校验通过
    testing: META-INF/CERT.RSA        OK
    testing: META-INF/MANIFEST.MF     OK
No errors detected in compressed data of app-debug.apk.
```

### aapt 能否解析（packageInfo 就是这里出的）
```
package: name='com.kongzhishouji.mika' versionCode='67' versionName='0.1.34' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' compileSdkVersionCodename='16'
sdkVersion:'30'
targetSdkVersion:'35'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.RECORD_AUDIO'
uses-permission: name='android.permission.REQUEST_INSTALL_PACKAGES'
uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
uses-permission: name='android.permission.FOREGROUND_SERVICE_SPECIAL_USE'
uses-permission: name='android.permission.FOREGROUND_SERVICE_DATA_SYNC'
uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.WAKE_LOCK'
```

### 签名是否完整
```
V2 Signer: certificate DN: CN=Mika Local, OU=Dev, O=KongZhiShouJi, C=CN
V2 Signer: certificate SHA-256 digest: a6d5ad1a94c642a5a8cf7dbff1611eb35b44740252b2599105b3684cb45d41e0
V2 Signer: certificate SHA-1 digest: 7195889f3c861d6e800b790e173250fcc147fd4b
V2 Signer: certificate MD5 digest: 3e421c402d72662ab098509c5bf98299
```
