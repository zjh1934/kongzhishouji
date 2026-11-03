# b12032 启动参数核实

日期: 2026-11-03　二进制: /tmp/lx/llama-b12032/llama-server

## 1. --help 里 jinja / webui 相关行
```
--context-shift, --no-context-shift     whether to use context shift on infinite text generation (default:
--ui-config, --webui-config JSON        JSON that provides default UI settings (overrides UI defaults)
--ui-config-file, --webui-config-file PATH
--ui-mcp-proxy, --webui-mcp-proxy, --no-ui-mcp-proxy, --no-webui-mcp-proxy
--ui,  --webui, --no-ui, --no-webui     whether to enable the Web UI (default: enabled)
--chat-template-kwargs STRING           sets additional params for the json template parser, must be a valid
                                        (env: LLAMA_ARG_CHAT_TEMPLATE_KWARGS)
--jinja, --no-jinja                     whether to use jinja template engine for chat (default: enabled)
                                        (env: LLAMA_ARG_JINJA)
--chat-template JINJA_TEMPLATE          set custom jinja chat template (default: template taken from model's
                                        only commonly used templates are accepted (unless --jinja is set
                                        (env: LLAMA_ARG_CHAT_TEMPLATE)
--chat-template-file JINJA_TEMPLATE_FILE
                                        set custom jinja chat template file (default: template taken from
                                        only commonly used templates are accepted (unless --jinja is set
                                        (env: LLAMA_ARG_CHAT_TEMPLATE_FILE)
                                        force a pure content parser, even if a Jinja template is specified;
```

## 2. 完整 --help 行数与前后片段
```
总行数: 753
----- common params -----

-h,    --help, --usage                  print usage and exit
--version                               show version and build info
-cl,   --cache-list                     show list of models in cache
--completion-bash                       print source-able bash completion script for llama.cpp
-t,    --threads N                      number of CPU threads to use during generation (default: -1)
                                        (env: LLAMA_ARG_THREADS)
-tb,   --threads-batch N                number of threads to use during batch and prompt processing (default:
                                        same as --threads)
-C,    --cpu-mask M                     CPU affinity mask: arbitrarily long hex. Complements cpu-range
                                        (default: "")
...
```

## 3. 实测：我们真实启动参数（模型路径故意不存在）
```
--- 带 --jinja ---
0.00.000.774 I srv  llama_server: initializing ...
0.00.007.674 I cmn  common_param: common_params_print_info: verbosity = 3 (adjust with the `-lv N` CLI arg)
0.00.007.768 I srv  init_listene: The UI is disabled
0.00.007.771 I srv  init_listene: Use --ui/--no-ui (or deprecated --webui/--no-webui) to enable/disable
0.00.007.838 W srv  server_setup: security: no API key is set and CORS allows all origins (see https://github.com/ggml-org/llama.cpp/pull/25655)
0.00.009.274 E gguf_init_from_file: failed to open GGUF file '/nope-mmproj.gguf' (No such file or directory)
0.00.009.329 E mtmd_get_cap_from_file: failed to get capabilities from file '/nope-mmproj.gguf': clip_model_loader: failed to load CLIP model from /nope-mmproj.gguf. Does this file exist?

0.00.009.354 I srv    load_model: loading model '/nope.gguf'
0.00.009.389 E gguf_init_from_file: failed to open GGUF file '/nope-mmproj.gguf' (No such file or directory)
0.00.009.408 E mtmd_get_memory_usage: error: Failed to load CLIP model from /nope-mmproj.gguf

0.00.009.413 E srv    load_model: [mtmd] failed to get memory usage of mmproj
0.00.009.478 E gguf_init_from_file: failed to open GGUF file '/nope.gguf' (No such file or directory)
0.00.009.520 E llama_model_load: error loading model: llama_model_loader: failed to load model from /nope.gguf
0.00.009.524 E llama_model_load_from_file_impl: failed to load model
0.00.009.551 E common_fit_params: encountered an error while trying to fit params to free device memory: failed to load model
0.00.009.558 E gguf_init_from_file: failed to open GGUF file '/nope.gguf' (No such file or directory)
0.00.009.564 E llama_model_load: error loading model: llama_model_loader: failed to load model from /nope.gguf
0.00.009.564 E llama_model_load_from_file_impl: failed to load model
0.00.009.566 E cmn  common_init_: failed to load model '/nope.gguf'
0.00.009.567 E srv    load_model: failed to load model, '/nope.gguf'
0.00.009.568 I srv    operator(): operator(): cleaning up before exit...
0.00.009.831 E srv  server_run_i: exiting due to model loading error

--- 不带 --jinja（对照）---
0.00.000.795 I srv  llama_server: initializing ...
0.00.007.268 I cmn  common_param: common_params_print_info: verbosity = 3 (adjust with the `-lv N` CLI arg)
0.00.007.358 I srv  init_listene: The UI is disabled
0.00.007.362 I srv  init_listene: Use --ui/--no-ui (or deprecated --webui/--no-webui) to enable/disable
0.00.007.434 W srv  server_setup: security: no API key is set and CORS allows all origins (see https://github.com/ggml-org/llama.cpp/pull/25655)
0.00.008.830 E gguf_init_from_file: failed to open GGUF file '/nope-mmproj.gguf' (No such file or directory)
0.00.008.878 E mtmd_get_cap_from_file: failed to get capabilities from file '/nope-mmproj.gguf': clip_model_loader: failed to load CLIP model from /nope-mmproj.gguf. Does this file exist?

0.00.008.899 I srv    load_model: loading model '/nope.gguf'
0.00.008.931 E gguf_init_from_file: failed to open GGUF file '/nope-mmproj.gguf' (No such file or directory)
0.00.008.948 E mtmd_get_memory_usage: error: Failed to load CLIP model from /nope-mmproj.gguf

0.00.008.952 E srv    load_model: [mtmd] failed to get memory usage of mmproj
0.00.009.029 E gguf_init_from_file: failed to open GGUF file '/nope.gguf' (No such file or directory)
0.00.009.070 E llama_model_load: error loading model: llama_model_loader: failed to load model from /nope.gguf
0.00.009.074 E llama_model_load_from_file_impl: failed to load model
0.00.009.098 E common_fit_params: encountered an error while trying to fit params to free device memory: failed to load model
0.00.009.105 E gguf_init_from_file: failed to open GGUF file '/nope.gguf' (No such file or directory)
0.00.009.112 E llama_model_load: error loading model: llama_model_loader: failed to load model from /nope.gguf
0.00.009.112 E llama_model_load_from_file_impl: failed to load model
0.00.009.113 E cmn  common_init_: failed to load model '/nope.gguf'
0.00.009.115 E srv    load_model: failed to load model, '/nope.gguf'
0.00.009.115 I srv    operator(): operator(): cleaning up before exit...
0.00.009.373 E srv  server_run_i: exiting due to model loading error
```

## 4. android-arm64 二进制里有没有 jinja 字符串
```
impl: /tmp/la/llama-b12032/libllama-server-impl.so
_Z34convert_transcriptions_to_chatcmplRK11common_jsonPK21common_chat_templatesRKNSt6__ndk13mapINS5_12basic_stringIcNS5_11char_traitsIcEENS5_9allocatorIcEEEE13uploaded_fileNS5_4lessISC_EENSA_INS5_4pairIKSC_SD_EEEEEERNS5_6vectorINSN_IhNSA_IhEEEENSA_ISP_EEEE
_ZN5jinja7contextC2ENSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE
_ZN5jinja7runtime7executeERKNS_7programE
_ZN5jinja7runtime19gather_string_partsERKNSt6__ndk110shared_ptrINS_7value_tEEE
_ZN5jinja6stringD2Ev
_ZN5jinja7contextD2Ev
_ZTVNSt6__ndk120__shared_ptr_emplaceIN5jinja14value_object_tENS_9allocatorIS2_EEEE
_ZN5jinja14value_object_t6insertERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEERKNS1_10shared_ptrINS_7value_tEEE
_ZTVNSt6__ndk120__shared_ptr_emplaceIN5jinja12value_none_tENS_9allocatorIS2_EEEE
_ZTVNSt6__ndk120__shared_ptr_emplaceIN5jinja14value_string_tENS_9allocatorIS2_EEEE
_ZTVNSt6__ndk120__shared_ptr_emplaceIN5jinja13value_array_tENS_9allocatorIS2_EEEE
_ZN5jinja14value_string_tC2ERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE
_ZNSt6__ndk120__shared_ptr_emplaceIN5jinja14value_object_tENS_9allocatorIS2_EEED2Ev
_ZNSt6__ndk120__shared_ptr_emplaceIN5jinja14value_object_tENS_9allocatorIS2_EEED0Ev
_ZN5jinja7value_tD2Ev
_ZTVN5jinja7value_tE
_ZN5jinja7value_tD0Ev
_ZNK5jinja7value_t4typeEv
_ZNK5jinja7value_t6as_intEv
_ZNK5jinja7value_t16throw_type_errorEPKc
```
