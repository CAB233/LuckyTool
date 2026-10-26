package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object ForceDisplayAutoLaunchJumpOption : Hooker {
    override fun onHook() {
        //Source AutoLaunchMgrPreferenceController
        "com.oplus.settings.feature.appmanager.controller.AutoLaunchMgrPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "getAvailabilityStatus" }.hook {
                    replaceTo(0)
                }
            }
    }
}