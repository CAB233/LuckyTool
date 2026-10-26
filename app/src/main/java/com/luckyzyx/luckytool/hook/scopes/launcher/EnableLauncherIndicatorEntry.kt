package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableLauncherIndicatorEntry : Hooker {
    override fun onHook() {
        //Source IndicatorEntry Companion
        "com.android.launcher3.search.IndicatorEntry\$Companion".toClass().resolve().apply {
            firstMethod { name = "isSupportIndicatorEntryMenu" }.hook {
                replaceTo(true)
            }
        }
    }
}