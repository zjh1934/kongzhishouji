package com.wangzan101.mika.local

/**
 * 本地模型的清单。
 *
 * 一个能看图的模型是**两个文件**:主模型(.gguf)+ 视觉投影(mmproj)。
 * 只下主模型不带投影,模型能说话但看不见屏幕,等于白下一半。
 *
 * 两个文件的名字**按仓库分别写死**,不做拼接:
 * 4B 的投影叫 `mmproj-F16.gguf`,2B 的叫 `mmproj-Qwen3VL-2B-Instruct-F16.gguf`,
 * 命名规则不一致,通用拼接会下到错文件。
 *
 * 数字查于 2026-11-02,来源魔搭与 HuggingFace 的文件页(两边的 SHA-256 一致,可跨源校验)。
 */
object LocalModelCatalog {

    /** 单个文件。主模型的精确字节数魔搭没给,只给到 2.50 GB 的页面口径,所以大小只当上界用。 */
    data class File(
        val name: String,
        /** 大小上界;校验以 [sha256] 为准,这个只用来提前判断空间够不够。 */
        val sizeUpperBound: Long,
        val sha256: String,
    )

    data class Model(
        val id: String,
        val displayName: String,
        /** 魔搭仓库(主源,国内快)。 */
        val msRepo: String,
        /** huggingface 仓库(备选源,经 hf-mirror 拉)。 */
        val hfRepo: String,
        val main: File,
        val mmproj: File,
        /**
         * 跑这个模型建议的最小机身内存(GB)。
         * 权重只是文件体积,运行时还要加视觉塔、KV cache、截图缓冲,
         * 所以门槛比"权重大小"高不少。
         */
        val minRamGb: Int,
    ) {
        /** 要下的两个文件,顺序固定:先主模型后投影。 */
        val files: List<File> get() = listOf(main, mmproj)

        /** 预留空间:两个文件上界之和再加两成余量。 */
        val requiredBytes: Long get() = (files.sumOf { it.sizeUpperBound } * 1.2).toLong()
    }

    /** 主力:千问 3 VL 4B,Q4_K_M 量化,原生带视觉,Apache 2.0。 */
    val QWEN3_VL_4B = Model(
        id = "qwen3-vl-4b",
        displayName = "Qwen3-VL 4B",
        msRepo = "unsloth/Qwen3-VL-4B-Instruct-GGUF",
        hfRepo = "unsloth/Qwen3-VL-4B-Instruct-GGUF",
        main = File(
            "Qwen3-VL-4B-Instruct-Q4_K_M.gguf",
            2_681_300_000L,
            "d4dcd426bfba75752a312b266b80fec8136fbaca13c62d93b7ac41fa67f0492b",
        ),
        mmproj = File(
            "mmproj-F16.gguf",
            836_180_640L,
            "1b9f4e92f0fbda14d7d7b58baed86039b8a980fe503d9d6a9393f25c0028f1fc",
        ),
        minRamGb = 12,
    )

    /** 降级:2B,内存吃紧时用。能力弱一些,但整套只有 1.9 GB。 */
    val QWEN3_VL_2B = Model(
        id = "qwen3-vl-2b",
        displayName = "Qwen3-VL 2B(轻量)",
        msRepo = "Qwen/Qwen3-VL-2B-Instruct-GGUF",
        hfRepo = "unsloth/Qwen3-VL-2B-Instruct-GGUF",
        main = File(
            "Qwen3VL-2B-Instruct-Q4_K_M.gguf",
            1_192_000_000L,
            "", // 未核实,跳过摘要校验(只按大小验)
        ),
        mmproj = File(
            "mmproj-Qwen3VL-2B-Instruct-F16.gguf",
            819_400_000L,
            "",
        ),
        minRamGb = 8,
    )

    val ALL: List<Model> = listOf(QWEN3_VL_4B, QWEN3_VL_2B)

    fun byId(id: String): Model? = ALL.firstOrNull { it.id == id }

    /**
     * 按机身内存挑一个:够就用主力,不够退到轻量,再不够就没有能跑的。
     * 内存参数来自 [android.app.ActivityManager.MemoryInfo.totalMem],比标称容量小(系统占掉一部分)。
     */
    fun recommend(ramGb: Int): Model? = ALL.firstOrNull { ramGb >= it.minRamGb }
}
