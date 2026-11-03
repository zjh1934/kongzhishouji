# 千问 LiteRT 模型链接核实
日期: 2026-11-03

## A. LiquidAgent 官方 14 预设里三条千问（逐个 HEAD，不准就明说）
```
200  1597922856  https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
200  1597922856  https://hf-mirror.com/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
200  ?  https://www.modelscope.cn/litert-community/Qwen2.5-1.5B-Instruct/resolve/master/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
200  1783424544  https://huggingface.co/litert-community/Qwen2-VL-2B/resolve/main/Qwen2-VL-2B.litertlm
200  1783424544  https://hf-mirror.com/litert-community/Qwen2-VL-2B/resolve/main/Qwen2-VL-2B.litertlm
200  ?  https://www.modelscope.cn/litert-community/Qwen2-VL-2B/resolve/master/Qwen2-VL-2B.litertlm
200  1833451520  https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm
200  1833451520  https://hf-mirror.com/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm
200  ?  https://www.modelscope.cn/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/master/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm
```

## B. litert-community/Qwen3-4B 文件清单（HF API）
```
.gitattributes 1858
README.md 11531
qwen3_4b_channelwise_int8_float32kv.litertlm 5672367472
qwen3_4b_mixed_int4.litertlm 2272935936
```

## C. Qwen3-4B-Instruct-2507 文件清单（HF API）
```
.gitattributes 1715
README.md 4776
qwen3_4b_instruct_2507_mixed_int4.litertlm 2659057664
```

## D. 两张模型卡里关于运行时版本的原话
```
--- Qwen3-4B ---
This repository contains LiteRT-LM variants of [Qwen/Qwen3-4B](https://huggingface.co/Qwen/Qwen3-4B) optimized for on-device text generation. Since 2026-10-31 its `qwen3_4b_mixed_int4.litertlm` is a new LiteRT Torch export with dynamic INT4 (block-32) weights and GPU composite ops, and its GPU path needs LiteRT-LM 0.19.0 or newer. On a Galaxy S26 with LiteRT-LM 0.19.0 on the GPU (OpenCL), this export prefilled 1,024 tokens at 526 tok/s and decoded at 19.5 tok/s, against 369 and 18.9 tok/s for the file it replaced (measured 2026-10-29; the Mac and the CPU are in that day's table below).
| File | Quantization | Context | Size |
| `qwen3_4b_channelwise_int8_float32kv.litertlm` | channel-wise INT8 weights, float32 KV | 4096 | 5.28 GB |
| `qwen3_4b_mixed_int4.litertlm` | dynamic INT4 (block-32) weights, float KV, GPU composite ops, external weights | 4096 | 2167.64 MiB |
## Conversion Notes
Until 2026-10-31, `qwen3_4b_mixed_int4.litertlm` was a different file, kept at revision `a45912eb`: a TorchAO-based quantize-first export of the original Hugging Face checkpoint with a mixed quantization layout (eligible linear projection weights as blockwise INT4 with group size 32 and floating-point scales, token embedding weights as weight-only INT8, normalization/reduction paths and KV cache tensors in floating point) and LiteRT-LM StableHLO composite ops for attention/cache execution, including `odml.runtime_bmm` and `odml.cache_update`.
`qwen3_4b_mixed_int4.litertlm` and `qwen3_4b_channelwise_int8_float32kv.litertlm` need litert-lm 0.18.0 or newer and carry LiteRT-LM's official qwen3 configuration, so thinking is on unless `enable_thinking` is false, the reasoning arrives on the thought channel, and tool calls are parsed by the runtime. The context is 4,096 tokens in both. (The earlier `qwen3_4b_mixed_int4.litertlm` had a 2,048-token context, in which the thought could use up the context before the answer with thinking on and no budget.)
Since 2026-10-31, `qwen3_4b_mixed_int4.litertlm` is a new export; the name stays from the earlier TorchAO mixed INT4 file, so apps and commands that fetch the file by name get this one. **Its GPU path needs LiteRT-LM 0.19.0 or newer** (its CPU path 0.18.0 or newer); with LiteRT-LM 0.18.x on the GPU, use the earlier file: `hf download litert-community/Qwen3-4B qwen3_4b_mixed_int4.litertlm --revision a45912eb8a7b33d6cb92aac6a08524b9463faec0`. The new file is a dynamic INT4 variant (block-32 weights, FP32 activations) exported with LiteRT Torch's `export_hf` at commit `0a81bf6f3` with `--apply_gpu_composites=True`: RMSNorm, the QK-norm and RoPE step, attention, SwiGLU and the KV-cache update are composite ops for the LiteRT-LM GPU path, and the QKV and Gate/Up projections are fused. It was exported with prefill lengths 128 and 1024 and a 32,768-token cache (`--cache_length=32768`); the default context in its metadata is 4,096 tokens. Its weights sit in a TFLiteWeights section at the end of the file (LiteRT's external weights) instead of inside the model graph. It carries the same LiteRT-LM qwen3 configuration as `qwen3_4b_channelwise_int8_float32kv.litertlm` (the same chat template, so the Known runtime issues below apply to it) and Qwen/Qwen3-4B's `tokenizer.json` byte for byte. On LiteRT-LM 0.18.1 the GPU path of this export fails on the Galaxy S26 and on a Mac: its composite kernels do not compile (measured with the weights inside the graph). The v0.18.0 and v0.18.1 Android builds also crash on a file with external weights (see Known runtime issues).
Hardware benchmark disclosure: Results were measured by us on retail devices purchased through normal channels. These results are not affiliated with, sponsored by, endorsed by, or verified by Samsung, vivo, Qualcomm, MediaTek, Google, MLCommons, or Hugging Face. Results depend on device SKU, OS build, thermal state, battery mode, backend, model quantization, runtime version, and benchmark settings.
| Desktop GPU WebGPU | 1327.33 | 87.52 | 0.20 | 1697 MB |
### `qwen3_4b_mixed_int4.litertlm` against the file it replaced (measured 2026-10-29, LiteRT-LM 0.19.0)
Since 2026-10-31 the file under this name is the export measured below with its weights moved to a TFLiteWeights section. On LiteRT-LM 0.19.0 it gave the same replies as the measured file (`Qwen3-4B_dynamic_wi4b32_afp32_gpu_composites.litertlm`, sha256 `a11d1b4c…`, weights inside the graph) and ran within 3 % of it or faster on the Galaxy S26 and the Mac, GPU and CPU (measured 2026-10-30; the Galaxy S26 ran both files in two GPU speed modes, and they were compared within each mode); "this file" below means the measured file. Measured by us on our own devices with LiteRT-LM 0.19.0 from PyPI, this file and the earlier file of the same name in the same sitting on each device: 1,024 prefill tokens and 256 decode tokens with a 2,048-token KV cache. Android (Galaxy S26): the `litert-lm-api` 0.19.0 wheel for Android (arm64), run on the phone by CPython 3.14 through the wheel's `Benchmark` class (the class `litert-lm benchmark` runs); on the GPU three processes per file, the first with no GPU program cache, each reporting the median of 3 runs, and a cell is the median of the three processes; on the CPU one process per file. Mac: `litert-lm benchmark --backend gpu|cpu -p 1024 -d 256 --max-num-tokens 2048 --cache no`, three processes per file on the GPU (a cell is their median), one on the CPU. No Pixel 8a rows: on an 8 GB Pixel 8a (a device-lab unit, 2026-10-16) this file's GPU engine built, but during the first inference the phone's free memory fell to 243 MB and the run was stopped; the earlier file was not tried there.
On litert-lm 0.18.x and 0.19.0, a blocking `send_message` that is still generating after 10 minutes fails with `DEADLINE_EXCEEDED` (a fixed timeout with no setting), and the generation it leaves running can crash the process. For long replies on a slow backend (long thinking in particular), lower `max_output_tokens` or use the streaming `send_message_async`, which has no such limit.
## Known runtime issues
**External weights (since 2026-10-31).** In `qwen3_4b_mixed_int4.litertlm`, the weights sit in a TFLiteWeights section at the end of the file (LiteRT's external weights) instead of inside the model graph. On Android, the GPU path needs LiteRT-LM 0.19.0 or later: the GPU engine of the v0.18.0 and v0.18.1 Android builds crashes (SIGSEGV) on such a file ([LiteRT-LM#3986](https://github.com/google-ai-edge/LiteRT-LM/issues/3986)). The same export with the weights inside the graph stays at revision [`a45912eb`](https://huggingface.co/litert-community/Qwen3-4B/tree/a45912eb8a7b33d6cb92aac6a08524b9463faec0) as `Qwen3-4B_dynamic_wi4b32_afp32_gpu_composites.litertlm`.
**iPhone.** With the v0.19.0 `CLiteRTLM.xcframework` on an iPhone 18 Pro, `qwen3_4b_mixed_int4.litertlm` builds its Metal GPU engine and works out 17 + 25 = 42 (one run, checked 2026-10-31); with the v0.18.1 package it gives no GPU answer. The Swift package at tag v0.19.0 points its binary targets at the v0.18.1 release zips; to run 0.19.0, edit them to the v0.19.0 release's zips (URL and checksum).
From a conversation preface (the system message, initial messages, tools and extra context a conversation is created with) that has tools and no messages, LiteRT-LM renders these files' chat template with an empty message list, and the template reads `messages[0]` without checking that the list is non-empty, so the render fails with `Failed to apply template: undefined value`. This happens in two places: when the preface is prefilled at creation (`prefillPrefaceOnInit = true` in Kotlin, `prefillPrefaceOnInit: true` on the web, `SetPrefillPrefaceOnInit(true)` in C++; the default is false), where creating the conversation fails with that error, and in the preface render (`renderPrefaceIntoString()` in Kotlin and Swift, `RenderPrefaceIntoString()` in C++, `litert_lm_conversation_render_preface_to_string` in the C API). The same happens with extra context (such as `enable_thinking`) and no messages, and in the preface render with an empty preface (run in the runtime on one Qwen3 template; the other templates rendered with minijinja 2.14 and jinja2). Read in the source of LiteRT-LM 0.17.0, 0.18.1 and main; measured with LiteRT-LM 0.18.1 (the litert-lm package from PyPI) through its C API on these files' template text. The default path, where the first message is rendered together with the preface, is not affected: the Python API and the CLI of 0.18.1 expose neither the flag nor the preface render, and Google AI Edge Gallery sets neither (its source as read on 2026-10-25). A system message or any initial message in the conversation config avoids it.
--- Qwen3-4B-Instruct-2507 ---
| File | Quantization | Context | Size |
| `qwen3_4b_instruct_2507_mixed_int4.litertlm` | TorchAO mixed INT4, float KV | 2048 | 2535.88 MiB |
## Conversion Notes
The mixed INT4 bundle also uses LiteRT-LM StableHLO composite ops for attention/cache execution, including `odml.runtime_bmm` and `odml.cache_update`.
Hardware benchmark disclosure: Results were measured by us on retail devices purchased through normal channels. These results are not affiliated with, sponsored by, endorsed by, or verified by Samsung, vivo, Qualcomm, MediaTek, Google, MLCommons, or Hugging Face. Results depend on device SKU, OS build, thermal state, battery mode, backend, model quantization, runtime version, and benchmark settings.
| Desktop GPU WebGPU | 1324.75 | 100.75 | 0.20 | 1697 MB |
A different runtime release and device generation than the table above (v0.13.1), so compare within one table, not across. Physical Samsung Galaxy S26 (SM-S942Q, Snapdragon 8 Elite Gen 5 / SM8850, Android 16), `litert_lm_advanced_main` from the litert-lm v0.16.0 release, GPU backend OpenCL (`LITERT_CL`), one fixed 205-token prompt text (203 tokens under this tokenizer), `--benchmark`. Two runs per backend taken back-to-back — cells show the range. Peak RSS is the process VmHWM. Before quoting, the file was run on each backend with a real prompt and both produced a correct text answer; the GPU rows are full delegation (1753/1753 and 1680/1680 ops on `LITERT_CL`, nothing on the CPU fallback).
- **At 4B the GPU leads every runtime column by a wide margin**: prefill 5.5–6.6×, decode 2.2–2.4× (19.9 against 8.4–9.2 tok/s), time-to-first-token 0.71 s against ~4 s.
```
