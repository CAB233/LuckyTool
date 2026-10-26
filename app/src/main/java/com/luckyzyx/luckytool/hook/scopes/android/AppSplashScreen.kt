package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object AppSplashScreen : Hooker {
    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("disable_splash_screen", false)

        //Source StartingSurfaceController
        "com.android.server.wm.StartingSurfaceController".toClass().resolve().apply {
            firstMethod {
                name = "showStartingWindow"
                parameterCount = 5
            }.hook {
                if (isEnable) intercept()
            }
        }
    }
}