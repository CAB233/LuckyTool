package com.luckyzyx.luckytool.hook.utils

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class OplusCommonFeatureUtils(val classLoader: ClassLoader?) {

    val clazz = "android.common.OplusFeatureCache".toClass(classLoader)

    fun getFeatureCache(cls: Any): Any? {
        return clazz.resolve().firstMethod { name = "get" }.invoke(cls)
    }

    fun getDefaultFeature(cls: String): Any? {
        val oplusCommonFeature = cls.toClass(classLoader)
        return oplusCommonFeature.resolve().firstField { type = cls }.get()
    }

}