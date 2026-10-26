package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveSettingsBottomLaboratory : Hooker {
    override fun onHook() {
        //Source TopLevelLaboratoryPreferenceController
        "com.oplus.settings.feature.homepage.TopLevelLaboratoryPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "getAvailabilityStatus" }.hook {
                    replaceTo(3)
                }
            }
    }
}