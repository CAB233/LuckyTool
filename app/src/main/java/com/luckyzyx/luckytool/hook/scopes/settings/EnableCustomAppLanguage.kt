package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableCustomAppLanguage : Hooker {
    override fun onHook() {
        //Source AppLocaleUtil
        "com.android.settings.applications.AppLocaleUtil".toClass().resolve().apply {
            firstMethod { name = "canDisplayLocaleUi" }.hook {
                replaceTo(true)
            }
        }
        //Source AppLocalePreferenceController
        "com.android.settings.applications.appinfo.AppLocalePreferenceController".toClass()
            .resolve().apply {
            firstMethod { name = "getAvailabilityStatus" }.hook {
                replaceTo(0)
            }
        }
    }
}