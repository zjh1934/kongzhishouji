# llama.cpp android-arm64 打包可行性（b12032）
日期: 2026-11-02

| 文件 | 原始 | strip后 |
|---|---:|---:|
| llama-server | 7192 | 4608 |
| libllama-server-impl.so | 68342392 | 8187072 |
| libllama-common.so | 89154256 | 6113544 |
| libllama.so | 39276456 | 3935952 |
| libmtmd.so | 20807456 | 1619528 |
| libggml.so | 4930232 | 666424 |
| libggml-base.so | 8509808 | 1079880 |
| libggml-rpc.so | 7143336 | 834816 |
| libggml-cpu-android_armv8.0_1.so | 4853296 | 935424 |
| libggml-cpu-android_armv8.2_1.so | 5146288 | 969744 |
| libggml-cpu-android_armv8.2_2.so | 5130472 | 973488 |
| libggml-cpu-android_armv8.6_1.so | 5240848 | 985080 |
| libggml-cpu-android_armv9.0_1.so | 5296464 | 989304 |
| libggml-cpu-android_armv9.2_1.so | 5296464 | 989304 |
| libggml-cpu-android_armv9.2_2.so | 5296464 | 989304 |

**服务端最小集合计：原始 274431424 → strip 后 29273472（10%）**
