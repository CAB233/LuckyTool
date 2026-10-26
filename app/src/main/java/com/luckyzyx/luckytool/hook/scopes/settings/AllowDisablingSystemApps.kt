package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object AllowDisablingSystemApps : Hooker {
    override fun onHook() {
        //Source AppButtonsPreferenceControllerAdaptor
        "com.oplus.settings.adaptor.AppButtonsPreferenceControllerAdaptor".toClass().resolve().apply {
            firstMethod { name = "setUninstallButtonEnabled" }.hook {
                before {
                    args().first().set(true)
                }
            }
        }
    }
}