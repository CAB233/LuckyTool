package com.luckyzyx.luckytool.hook.utils

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class OplusMirageDisplayManagerUtils(val classLoader: ClassLoader?) {

    val service = "com.android.server.display.OplusMirageDisplayManagerService".toClass(classLoader)

    fun getInstance(): Any? {
        return service.resolve().firstMethod {
            name = "getInstance";emptyParameters()
        }.invoke()
    }

    fun notifyCastSuccess(instance: Any, displayId: Int) {
        instance.asResolver().firstMethod { name = "notifyCastSuccess";parameters(Int::class) }
            .invoke(displayId)
    }
}