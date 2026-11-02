# 补丁失败 run 20
日期: 2026-11-02

```
== P1: 换身份 + 固定签名 ==
  已放入新增源码：
    app/src/main/java/com/wangzan101/mika/local/LocalModelSources.kt
    app/src/main/java/com/wangzan101/mika/local/LocalServerBoot.kt
    app/src/main/java/com/wangzan101/mika/local/LocalServerService.kt
    app/src/main/java/com/wangzan101/mika/local/LocalMode.kt
    app/src/main/java/com/wangzan101/mika/local/LocalModelController.kt
    app/src/main/java/com/wangzan101/mika/local/FilePicker.kt
    app/src/main/java/com/wangzan101/mika/local/LocalModelScreen.kt
    app/src/main/java/com/wangzan101/mika/local/LocalMemoryBudget.kt
    app/src/main/java/com/wangzan101/mika/local/NativeBridge.kt
    app/src/main/java/com/wangzan101/mika/local/LocalPrompt.kt
    app/src/main/java/com/wangzan101/mika/local/LocalModelDownloader.kt
    app/src/main/java/com/wangzan101/mika/local/LocalServerManager.kt
    app/src/main/java/com/wangzan101/mika/local/LocalModelCatalog.kt
    app/src/main/java/com/wangzan101/mika/local/LocalModelStore.kt
    app/src/main/cpp/CMakeLists.txt
    app/src/main/cpp/native-bridge.cpp
  applicationId -> com.kongzhishouji.mika ; versionCode -> 54
  app_name -> Mika Local
  MikaLinks -> zjh1934/kongzhishouji (2 处)
  native 构建已接入（CMake + arm64）
  拉取 llama.cpp b12032 ...
    解包目录: x/llama-b12032
  运行时就位：
total 28840
drwxr-xr-x 2 runner runner    4096 Nov  2 11:26 .
drwxr-xr-x 3 runner runner    4096 Nov  2 11:25 ..
-rwxr-xr-x 1 runner runner 1079880 Nov  2 11:26 libggml-base.so
-rwxr-xr-x 1 runner runner  935424 Nov  2 11:26 libggml-cpu-android_armv8.0_1.so
-rwxr-xr-x 1 runner runner  969744 Nov  2 11:26 libggml-cpu-android_armv8.2_1.so
-rwxr-xr-x 1 runner runner  973488 Nov  2 11:26 libggml-cpu-android_armv8.2_2.so
-rwxr-xr-x 1 runner runner  985080 Nov  2 11:26 libggml-cpu-android_armv8.6_1.so
-rwxr-xr-x 1 runner runner  989304 Nov  2 11:26 libggml-cpu-android_armv9.0_1.so
-rwxr-xr-x 1 runner runner  989304 Nov  2 11:26 libggml-cpu-android_armv9.2_1.so
-rwxr-xr-x 1 runner runner  989304 Nov  2 11:26 libggml-cpu-android_armv9.2_2.so
-rwxr-xr-x 1 runner runner  834816 Nov  2 11:26 libggml-rpc.so
-rwxr-xr-x 1 runner runner  666424 Nov  2 11:26 libggml.so
-rwxr-xr-x 1 runner runner 6113544 Nov  2 11:26 libllama-common.so
-rwxr-xr-x 1 runner runner 8187072 Nov  2 11:26 libllama-server-impl.so
-rwxr-xr-x 1 runner runner 3935952 Nov  2 11:26 libllama.so
-rwxr-xr-x 1 runner runner    4608 Nov  2 11:26 libllamaserver.so
-rwxr-xr-x 1 runner runner   16472 Nov  2 11:25 libmikarun.so
-rwxr-xr-x 1 runner runner 1619528 Nov  2 11:26 libmtmd.so
-rwxr-xr-x 1 runner runner  181824 Nov  2 11:25 libproot.so
29M	app/src/main/jniLibs/arm64-v8a/
  已加「本机模型」预设
  Route 已加 LocalModel
  MainActivity 已加 LocalModel 分支
  设置页已加入口（SettingsScreen -> SettingsContent 透传）
  调用处已传 onOpenLocalModel
  前台服务权限已存在，跳过
  启动钩子已注册
== 本地模式补丁 ==
python3: can't open file '/home/runner/work/kongzhishouji/kongzhishouji/mika/x/llama-b12032/scripts/patch_local_mode.py': [Errno 2] No such file or directory
```
