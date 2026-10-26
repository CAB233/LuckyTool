package com.luckyzyx.luckytool.hook.scopes.settings

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableSwipeUpNavigationGesture : Hooker {
    override fun onHook() {
        //Source NavBarSettingsValueUtil
        "com.oplus.settings.feature.navbar.NavBarSettingsValueUtil".toClass().resolve().apply {
            firstMethod {
                name = "getGestureUpModeAvailable"
                parameters(Context::class)
                returnType = Int::class
            }.hook {
                replaceTo(0)
            }
        }
    }
}