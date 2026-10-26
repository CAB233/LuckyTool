package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object ForceDisplayDisabledAppsManager : Hooker {
    override fun onHook() {
        //Source DisabledAppsPreferenceController
        "com.android.settings.applications.disableapps.DisabledAppsPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "getAvailabilityStatus" }.hook {
                    replaceTo(0)
                }
            }
    }
}