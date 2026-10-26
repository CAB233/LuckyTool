package com.luckyzyx.luckytool.hook.utils.sysui

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class WifiUtils(val classLoader: ClassLoader?) {

    val clazz = "com.oplus.systemui.statusbar.util.WifiUtils".toClass(classLoader)

    fun getInstance(): Any? {
        return clazz.resolve().firstField { name = "INSTANCE" }.get()
    }

    fun isDualWifiConnected(context: Context): Boolean {
        return clazz.resolve().firstMethod { name = "isDualWifiConnected" }.of(getInstance())
            .invoke<Boolean>(context) ?: false
    }

    fun isPassPointAp(context: Context): Boolean {
        return clazz.resolve().firstMethod { name = "isPassPointAp" }.of(getInstance())
            .invoke<Boolean>(context) ?: false
    }

}