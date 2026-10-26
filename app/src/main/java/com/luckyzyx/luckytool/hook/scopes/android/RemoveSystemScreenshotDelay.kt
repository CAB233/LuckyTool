package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object RemoveSystemScreenshotDelay : Hooker {
    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("remove_system_screenshot_delay", false)

        //Source PhoneWindowManager
        "com.android.server.policy.PhoneWindowManager".toClass().resolve().apply {
            firstMethod {
                name = "getScreenshotChordLongPressDelay"
                returnType = Long::class
            }.hook {
                if (isEnable) {
                    replaceTo(0L)
                }
            }
        }
    }
}