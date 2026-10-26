package com.luckyzyx.luckytool.hook.utils.sysui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class QSFeatureOptionUtils(val classLoader: ClassLoader?) {

    val clazz = "com.oplusos.systemui.common.feature.QSFeatureOption".toClassOrNull(classLoader)

    fun getInstance(): Any? {
        return clazz?.resolve()?.firstField { name = "INSTANCE" }?.get()
    }

    fun isSupportVolumeSeekBar(): Boolean {
        return clazz?.resolve()?.firstMethodOrNull { name = "isSupportVolumeSeekBar" }
            ?.of(getInstance())?.invoke<Boolean>() ?: false
    }
}