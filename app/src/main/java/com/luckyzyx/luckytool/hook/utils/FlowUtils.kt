package com.luckyzyx.luckytool.hook.utils

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class FlowUtils(val classLoader: ClassLoader?) {

    val stateFlowKt = "kotlinx.coroutines.flow.StateFlowKt".toClass(classLoader)
    val flowKtShareKt = "kotlinx.coroutines.flow.FlowKt__ShareKt".toClass(classLoader)

    fun MutableStateFlow(any: Any?): Any? {
        return stateFlowKt.resolve().firstMethod { name = "MutableStateFlow" }.invoke(any)
    }

    fun asStateFlow(mutableStateFlow: Any?): Any? {
        return flowKtShareKt.resolve().firstMethod { name = "asStateFlow" }.invoke(mutableStateFlow)
    }

    inline fun <reified T> getValue(flow: Any): T? {
        return flow.asResolver().let {
            (it.firstMethodOrNull { name = "getValue" }
                ?: it.firstMethod { name = "getValue" }).invoke<T>()
        }
    }

}