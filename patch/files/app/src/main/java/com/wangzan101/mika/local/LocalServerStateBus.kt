package com.wangzan101.mika.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 服务状态的唯一真相，页面和服务都看这一份。
 *
 * 为什么要它：前台服务和 Activity **在同一个进程**（没声明 `android:process`），
 * 所以一个 object 就能共享状态，不用走广播、也不用 IPC。
 *
 * 之前踩的坑：页面自己另起一个 OkHttp 去探 `/health`，
 * 结果服务明明起来了（通知栏写「已就绪」）、页面却一直转圈 ——
 * **两个实例各查各的，且页面的那套 OkHttp 没有 Mika 的 localhost 白名单，
 * 明文 HTTP 被拦了**。让两边看同一份状态，就没有这种"服务好了页面不知道"的怪事。
 */
object LocalServerStateBus {

    private val _state = MutableStateFlow<LocalServerState>(LocalServerState.Stopped)
    val state: StateFlow<LocalServerState> = _state.asStateFlow()

    /** 服务那边更新，页面立刻能看见。 */
    fun set(s: LocalServerState) {
        _state.value = s
    }

    fun current(): LocalServerState = _state.value

    /** 是不是起来了。 */
    fun isUp(): Boolean = _state.value is LocalServerState.Running
}
