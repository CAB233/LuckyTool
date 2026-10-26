package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.hook.core.*

object ForceDisplayProcessManagement : Hooker {
    override fun onHook() {
        //com.oplus.settings.feature.process.RunningApplicationActivity
        //Source RunningApplicationsPreferenceController
        VariousClass(
            "com.oplus.settings.feature.othersettings.controller.RunningApplicationsPreferenceController", //C13 C14
            "com.oplus.settings.feature.spfunction.RunningApplicationsPreferenceController" //C14.1
        ).toClass().resolve().apply {
            firstMethod { name = "getAvailabilityStatus" }.hook {
                replaceTo(0)
            }
        }
        //Source RunningApplicationsNewPreferenceController
        "com.oplus.settings.feature.appmanager.controller.RunningApplicationsNewPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "getAvailabilityStatus" }.hook {
                    replaceTo(0)
                }
            }
    }
}