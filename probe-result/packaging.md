# llama.cpp android-arm64 打包可行性（tag b12032）
查询日期: 2026-11-02

## 体积：原始 vs strip

| 文件 | 原始 | strip 后 |
|---|---:|---:|
| llama-server | 7192 | 0 |
| libllama-server-impl.so | 68342392 | 0 |
| libllama-common.so | 89154256 | 0 |
| libllama.so | 39276456 | 0 |
| libmtmd.so | 20807456 | 0 |
| libggml.so | 4930232 | 0 |
| libggml-base.so | 8509808 | 0 |
| libggml-rpc.so | 7143336 | 0 |
| libggml-cpu-android_armv8.0_1.so | 4853296 | 0 |
| libggml-cpu-android_armv8.2_1.so | 5146288 | 0 |
| libggml-cpu-android_armv8.2_2.so | 5130472 | 0 |
| libggml-cpu-android_armv8.6_1.so | 5240848 | 0 |
| libggml-cpu-android_armv9.0_1.so | 5296464 | 0 |
| libggml-cpu-android_armv9.2_1.so | 5296464 | 0 |
| libggml-cpu-android_armv9.2_2.so | 5296464 | 0 |

**合计：原始  → strip 后 **

## 文件类型
```
--- llama-server ---
x/llama-b12032/llama-server: ELF 64-bit LSB pie executable, ARM aarch64, version 1 (SYSV), dynamically linked, interpreter /system/bin/linker64, BuildID[sha1]=ce969d9149de474b3eae57a9faf758fc4933a28c,
--- libllama.so ---
x/llama-b12032/libllama.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[sha1]=d0090e0c46a95da5e50557e3bf96a3a6fa2fa5c6, with debug_info, not stripped
--- libmtmd.so ---
x/llama-b12032/libmtmd.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[sha1]=0c347314348e61762d90fd7b85f8362b53b2a2fb, with debug_info, not stripped
```
