package com.luckyzyx.luckytool.hook.utils.launcher

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class LauncherAppStateUtils(val classLoader: ClassLoader?) {

    val clazz = "com.android.launcher3.LauncherAppState".toClass(classLoader)

    fun getInstance(context: Context?): Any? {
        return clazz.resolve().firstMethod { name = "getInstance" }.invoke(context)
    }

    fun getInstanceNoCreate(): Any? {
        return clazz.resolve().firstMethod { name = "getInstanceNoCreate" }.invoke()
    }

    fun getIDP(context: Context?): Any? {
        return clazz.resolve().firstMethod { name = "getIDP" }.invoke(context)
    }

    fun getContext(ins: Any?): Context? {
        return ins?.asResolver()?.firstMethod { name = "getContext" }?.invoke<Context>()
    }

    fun getInvariantDeviceProfile(ins: Any?): Any? {
        return ins?.asResolver()?.firstMethod { name = "getInvariantDeviceProfile" }?.invoke()
    }

}