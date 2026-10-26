package com.luckyzyx.luckytool.hook.utils.sysui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.A14
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.hook.core.*

class FlavorOneFeatureUtils(val classLoader: ClassLoader?) {

    val clazz = "com.oplusos.systemui.common.feature.FlavorOneFeatureOption".toClass(classLoader)

    fun getInstance(): Any? {
        return if (SDK >= A14) clazz.resolve().firstField { name = "INSTANCE" }.get() else null
    }

    fun isFlavorOneDevice(): Boolean? {
        return clazz.resolve().firstMethod { name = "isFlavorOneDevice" }.of(getInstance())
            .invoke<Boolean>()
    }
}