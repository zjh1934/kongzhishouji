# 本次构建的 APK 内容（run 22, 2026-11-02）

APK 体积: 104669198 字节

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

体积: 104669198 字节

### zip 完整性
```
OK：全部条目 CRC 校验通过
    testing: META-INF/CERT.RSA        OK
    testing: META-INF/MANIFEST.MF     OK
No errors detected in compressed data of app-debug.apk.
```

### aapt 能否解析（packageInfo 就是这里出的）
```
ERROR: Unknown command '/usr/local/lib/android/sdk/build-tools/35.0.0/aapt'
Android Asset Packaging Tool

Usage:
 aapt l[ist] [-v] [-a] file.{zip,jar,apk}
   List contents of Zip-compatible archive.

 aapt d[ump] [--values] [--include-meta-data] WHAT file.{apk} [asset [asset ...]]
   strings          Print the contents of the resource table string pool in the APK.
   badging          Print the label and icon for the app declared in APK.
   permissions      Print the permissions from the APK.
   resources        Print the resource table from the APK.
```

### 签名是否完整
```
Unsupported command: /usr/local/lib/android/sdk/build-tools/35.0.0/apksigner. See --help for supported commands
```
