package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableStatusBarClockFormat : Hooker {
    override fun onHook() {
        //Source RmStatusbarClockPreferenceController
        "com.oplus.settings.feature.notification.controller.RmStatusbarClockPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "getAvailabilityStatus" }.hook {
                    replaceTo(0)
                }
            }
    }
}