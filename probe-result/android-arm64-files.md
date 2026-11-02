# llama.cpp android-arm64 预编译包清单
tag: b12032
查询日期: 2026-11-02

压缩包体积: 79002991 字节

## 解包后各文件体积（降序）
```
89154256	llama-b12032/libllama-common.so
68342392	llama-b12032/libllama-server-impl.so
39276456	llama-b12032/libllama.so
20807456	llama-b12032/libmtmd.so
8509808	llama-b12032/libggml-base.so
7143336	llama-b12032/libggml-rpc.so
5296464	llama-b12032/libggml-cpu-android_armv9.2_2.so
5296464	llama-b12032/libggml-cpu-android_armv9.2_1.so
5296464	llama-b12032/libggml-cpu-android_armv9.0_1.so
5240848	llama-b12032/libggml-cpu-android_armv8.6_1.so
5146288	llama-b12032/libggml-cpu-android_armv8.2_1.so
5130472	llama-b12032/libggml-cpu-android_armv8.2_2.so
4930232	llama-b12032/libggml.so
4853296	llama-b12032/libggml-cpu-android_armv8.0_1.so
3974040	llama-b12032/libllama-bench-impl.so
2865016	llama-b12032/libllama-cli-impl.so
2645872	llama-b12032/llama-imatrix
2231104	llama-b12032/llama-qwen2vl-cli
2231104	llama-b12032/llama-minicpmv-cli
2231104	llama-b12032/llama-llava-cli
2231104	llama-b12032/llama-gemma3-cli
1690800	llama-b12032/libllama-perplexity-impl.so
1340664	llama-b12032/libllama-completion-impl.so
1240080	llama-b12032/ggml-rpc-server
1122280	llama-b12032/llama-mtmd-cli
1024168	llama-b12032/libllama-quantize-impl.so
797824	llama-b12032/llama-mtmd-debug
743624	llama-b12032/llama
685664	llama-b12032/llama-results
655304	llama-b12032/libllama-batched-bench-impl.so
645688	llama-b12032/llama-tts
629008	llama-b12032/llama-tokenize
595120	llama-b12032/libllama-fit-params-impl.so
304304	llama-b12032/llama-gguf-split
7224	llama-b12032/llama-batched-bench
7216	llama-b12032/llama-cli
7200	llama-b12032/llama-perplexity
7200	llama-b12032/llama-fit-params
7200	llama-b12032/llama-completion
7192	llama-b12032/llama-server
7184	llama-b12032/llama-quantize
7184	llama-b12032/llama-bench
1078	llama-b12032/LICENSE
```

## 只带服务端所需文件的合计
```
39276456	llama-b12032/libllama.so
20807456	llama-b12032/libmtmd.so
8509808	llama-b12032/libggml-base.so
7143336	llama-b12032/libggml-rpc.so
5296464	llama-b12032/libggml-cpu-android_armv9.2_2.so
5296464	llama-b12032/libggml-cpu-android_armv9.2_1.so
5296464	llama-b12032/libggml-cpu-android_armv9.0_1.so
5240848	llama-b12032/libggml-cpu-android_armv8.6_1.so
5146288	llama-b12032/libggml-cpu-android_armv8.2_1.so
5130472	llama-b12032/libggml-cpu-android_armv8.2_2.so
4930232	llama-b12032/libggml.so
4853296	llama-b12032/libggml-cpu-android_armv8.0_1.so
7192	llama-b12032/llama-server
---合计---
116934776
```

## 视觉/多模态证据
```
--- 文件名含 mtmd ---
llama-b12032/libmtmd.so
llama-b12032/llama-mtmd-debug
llama-b12032/llama-mtmd-cli
--- llama-server 里的 mmproj 相关字符串 ---
--- libmtmd.so 导出符号（前 30）---
mtmd_audio_cache
mtmd_audio_log_mel_frame
mtmd_audio_mel
mtmd_audio_mel_filters
mtmd_audio_preprocessor
mtmd_audio_preprocessor_conformer
mtmd_audio_preprocessor_d1omni
mtmd_audio_preprocessor_dots3note
mtmd_audio_preprocessor_gemma4a
mtmd_audio_preprocessor_gemma4ua
mtmd_audio_preprocessor_granite_speech
mtmd_audio_preprocessor_lfm3a
mtmd_audio_preprocessor_mimo_audio
mtmd_audio_preprocessor_muse_spark_audio
mtmd_audio_preprocessor_parakeet
mtmd_audio_preprocessor_pockettts
mtmd_audio_preprocessor_qwen3a
mtmd_audio_preprocessor_qwen3tts_spk
mtmd_audio_preprocessor_whisper
mtmd_audio_stream
mtmd_audio_stream_add_rows
mtmd_audio_stream_compute_mel
mtmd_audio_stream_encode
mtmd_audio_stream_flush
mtmd_audio_stream_free
mtmd_audio_stream_get_n_rows
mtmd_audio_stream_get_n_rows_flushed
mtmd_audio_stream_get_output_embd
mtmd_audio_stream_init
mtmd_audio_stream_is_seq_start
```
