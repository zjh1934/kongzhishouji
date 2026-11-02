package com.wangzan101.mika.local

import android.app.ActivityManager
import android.content.Context

/**
 * 端侧推理的内存预算。
 *
 * **核心认知（调研得来，不记下来一定会踩）**：
 * 端侧 OOM 的元凶**不是模型权重，是 KV cache**。
 * 每 token 的 KV cache ≈ 2 × 层数 × d_model × 2 字节，是**随上下文线性增长**的。
 * 7B 模型实测 512KB/token —— 也就是说 8K 上下文光 KV 就要 4GB，
 * 这已经超出多数手机的可用内存。
 *
 * 所以上下文不是"越大越好"。我们最初设的 8192 是错的（当时只想到截图吃 token，
 * 没算 KV 的账）。改成按机身内存分级。
 */
object LocalMemoryBudget {

    /** 机身总内存（GB）。拿不到时给个保守的 8。 */
    fun ramGb(ctx: Context): Int {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(mi)
        val gb = (mi.totalMem / (1024.0 * 1024 * 1024)).toInt()
        return if (gb <= 0) 8 else gb
    }

    /**
     * 上下文长度。
     *
     * 分级依据：4B Q4 权重约 2.5GB，留给 KV cache 的余量在 8GB 机上很紧张。
     * 多个端侧项目的共同起手值是 `-c 2048`，我们在此基础上按内存放宽。
     */
    fun contextFor(ctx: Context): Int = when {
        ramGb(ctx) >= 16 -> 4096    // 16GB 机：可用 12-14GB，给得起
        ramGb(ctx) >= 12 -> 3072
        ramGb(ctx) >= 8 -> 2048     // 8GB 机：保守，别贪
        else -> 1024                // 6GB 及以下：只够跑最简单的动作
    }

    /**
     * 推理线程数。
     *
     * 不是越多越好：骁龙 8 系是 1+3+4 大小核架构，**小核会拖后腿**，
     * 实测开 4-5 个（只用性能核）反而比开满 8 个快。
     */
    fun threads(): Int {
        val available = Runtime.getRuntime().availableProcessors()
        return (available - 1).coerceIn(2, 5)
    }

    /** 单 slot：多 slot 会倍增 KV 预算，单 Agent 场景用不上。 */
    const val SLOTS = 1

    /** 输出上限。动作 JSON 通常不到 200 token，给太长反而是幻觉温床。 */
    const val MAX_OUTPUT = 512

    /** 低于这个内存的机器不给 4B 选项（4B Q4 权重就 2.5GB）。 */
    const val MIN_RAM_FOR_4B = 8
}
