package com.wangzan101.mika.local

/**
 * 本地模型的清单。
 *
 * 一个能看图的模型是**两个文件**:主模型(.gguf)+ 视觉投影(mmproj)。
 * 只下主模型不带投影,模型能说话但看不见屏幕,等于白下一半。
 *
 * 为什么列了好几个:
 * 手机 GUI 操控这件事没有公认的"最佳端侧模型"。GUI-Owl 是专门练过的,
 * MAI-UI 体积小一半,千问 4B 是通用基线。谁在真机上更好,只有实测才知道,
 * 所以让用户自己选,而不是我们替他定死。
 *
 * 文件名和字节数**必须用真实直链核实过**(2026-11-02 让 CI 用 HEAD 逐个查的)。
 * 写错一个字符就是白下几个 G。注意 mradermacher 仓库用的是**点**分隔
 * (`MAI-UI-2B.Q4_K_M.gguf`),不是连字符 —— 这个坑踩过。
 */
object LocalModelCatalog {

    /** 单个文件。 */
    data class File(
        val name: String,
        /** 真实字节数(CI 实测 content-length)。摘要拿不到时靠它做基本校验。 */
        val size: Long,
        /** SHA-256;拿不到就留空,校验退化为比对大小。 */
        val sha256: String = "",
    )

    data class Model(
        val id: String,
        val displayName: String,
        /** 一句话说清它擅长什么,给用户选的时候看。 */
        val blurb: String,
        /** 魔搭仓库(主源,国内快)。魔搭上没有则为 null,只能走 hf-mirror。 */
        val msRepo: String?,
        /** HuggingFace 仓库(经 hf-mirror 拉)。 */
        val hfRepo: String,
        val main: File,
        val mmproj: File,
        /** 跑这个模型建议的最小机身内存(GB)。 */
        val minRamGb: Int,
        /** 是不是专门针对手机/GUI 操控训练过。 */
        val guiTrained: Boolean,
    ) {
        val files: List<File> get() = listOf(main, mmproj)
        val totalBytes: Long get() = main.size + mmproj.size
    }

    /**
     * 首选:在 Qwen3-VL 上继续训练的专门 GUI Agent。
     * 底子是千问 4B(所以通用看图、中文能力都在),额外练了 GUI 操控和外部工具调用。
     * 模型卡明确写了支持 tool / MCP 调用 —— 这正是 Mika 需要的。
     */
    val GUI_OWL_4B = Model(
        id = "gui-owl-4b",
        displayName = "GUI-Owl 1.5 4B",
        blurb = "专门练过手机操控,还会调工具。底子是千问 4B。推荐先用这个。",
        msRepo = null,
        hfRepo = "mradermacher/GUI-Owl-1.5-4B-Instruct-GGUF",
        main = File(
            "GUI-Owl-1.5-4B-Instruct.Q4_K_M.gguf",
            2_497_282_208L,
        ),
        mmproj = File(
            "GUI-Owl-1.5-4B-Instruct.mmproj-Q8_0.gguf",
            453_974_336L,
        ),
        minRamGb = 8,
        guiTrained = true,
    )

    /**
     * 体积最小:只有 GUI-Owl 的一半,下载和跑都快。
     * 同样练过 MCP 工具调用。适合先验证链路通不通,或者手机内存吃紧。
     */
    val MAI_UI_2B = Model(
        id = "mai-ui-2b",
        displayName = "MAI-UI 2B",
        blurb = "体积最小(1.4 GB),跑得快。也练过工具调用。入门首选。",
        msRepo = null,
        hfRepo = "mradermacher/MAI-UI-2B-GGUF",
        main = File(
            "MAI-UI-2B.Q4_K_M.gguf",
            1_107_410_400L,
        ),
        mmproj = File(
            "MAI-UI-2B.mmproj-Q8_0.gguf",
            445_053_216L,
        ),
        minRamGb = 6,
        guiTrained = true,
    )

    /**
     * 通用基线:没专门练过 GUI,但通用看图、中文、推理的底子最扎实。
     * 留着做对照 —— 万一专门模型在你手机上反而不好使,可以退回来。
     */
    val QWEN3_VL_4B = Model(
        id = "qwen3-vl-4b",
        displayName = "Qwen3-VL 4B",
        blurb = "通用视觉模型,没专门练过操控手机。作对照用。",
        msRepo = "unsloth/Qwen3-VL-4B-Instruct-GGUF",
        hfRepo = "unsloth/Qwen3-VL-4B-Instruct-GGUF",
        main = File(
            "Qwen3-VL-4B-Instruct-Q4_K_M.gguf",
            2_497_282_336L,
            "d4dcd426bfba75752a312b266b80fec8136fbaca13c62d93b7ac41fa67f0492b",
        ),
        mmproj = File(
            "mmproj-F16.gguf",
            836_180_640L,
            "1b9f4e92f0fbda14d7d7b58baed86039b8a980fe503d9d6a9393f25c0028f1fc",
        ),
        minRamGb = 8,
        guiTrained = false,
    )

    /** 顺序即推荐顺序:专门练过的排前面。 */
    val ALL: List<Model> = listOf(GUI_OWL_4B, MAI_UI_2B, QWEN3_VL_4B)

    fun byId(id: String): Model? = ALL.firstOrNull { it.id == id }

    val DEFAULT: Model get() = MAI_UI_2B

    /**
     * 按机身内存挑:内存够就给推荐顺序里最靠前的,都不够就给最小的那个(跑不动也认了)。
     */
    fun recommend(ramGb: Int): Model? =
        ALL.firstOrNull { ramGb >= it.minRamGb } ?: ALL.minByOrNull { it.minRamGb }
}
