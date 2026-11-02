package com.wangzan101.mika.local

/**
 * 通往 native 的桥。
 *
 * 先不在这里干重活 —— P3a 的唯一目的是证明：Mika 这个原本没有任何 CMake/NDK 配置的工程，
 * 能被我们加上 native 构建，且 .so 真的打进了 APK。
 *
 * `init` 里的 loadLibrary 只在第一次用到本类时才跑，所以没接功能之前不会影响 App 启动。
 */
object NativeBridge {
    init {
        System.loadLibrary("mikalocal")
    }

    /** native 侧回一句话；空壳阶段用来判断 .so 在不在。 */
    external fun nativeHello(): String
}
