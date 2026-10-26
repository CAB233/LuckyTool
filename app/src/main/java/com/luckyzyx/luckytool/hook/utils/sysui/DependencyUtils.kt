package com.luckyzyx.luckytool.hook.utils.sysui

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

class DependencyUtils(val classLoader: ClassLoader?, isEx: Boolean = false) {

    val clazz = if (isEx) "com.android.systemui.DependencyEx".toClass(classLoader)
    else "com.android.systemui.Dependency".toClass(classLoader)

    fun getDependency(cls: Class<*>): Any? {
        val sDependency = clazz.resolve().firstField { type = clazz }.get() ?: return null
        return sDependency.asResolver().firstMethod {
            name = "getDependency"
            parameters(Class::class)
        }.invoke<Any>(cls)
    }

}