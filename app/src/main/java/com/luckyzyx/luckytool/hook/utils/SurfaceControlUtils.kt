package com.luckyzyx.luckytool.hook.utils

import android.os.IBinder
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class SurfaceControlUtils(val classLoader: ClassLoader?) {

    val clazz = "android.view.SurfaceControl".toClass(classLoader)

    fun isDisplayToken(): Boolean {
        return clazz.resolve().firstMethodOrNull {
            name = "getDynamicDisplayInfo"
            parameters(IBinder::class)
        } != null
    }

    fun getDynamicDisplayInfo(displayId: Long?): Any? {
        return clazz.resolve().firstMethod {
            name = "getDynamicDisplayInfo"
            parameters(Long::class)
        }.invoke(displayId)
    }

    fun getDynamicDisplayInfo(displayToken: IBinder?): Any? {
        return clazz.resolve().firstMethod {
            name = "getDynamicDisplayInfo"
            parameters(IBinder::class)
        }.invoke(displayToken)
    }

    fun getPhysicalDisplayToken(physicalDisplayId: Long?): IBinder? {
        return clazz.resolve().firstMethod {
            name = "getPhysicalDisplayToken"
            parameters(Long::class)
        }.invoke<IBinder>(physicalDisplayId)
    }

    fun getInternalDisplayToken(): IBinder? {
        return clazz.resolve().firstMethod {
            name = "getInternalDisplayToken"
            emptyParameters()
        }.invoke<IBinder>()
    }
}