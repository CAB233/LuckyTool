package com.luckyzyx.luckytool.hook.scopes.uiengine

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.A14
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.hook.core.*

object SetAodNotificationIconStyle : Hooker {

    override fun onHook() {
        val mode = prefs(ModulePrefs).getString("set_aod_notification_icon_style", "0")
        if (mode == "0") return

        //Source ProductFlavorOption
        "com.oplus.egview.util.ProductFlavorOption".toClass().resolve().apply {
            firstMethod {
                name = if (SDK >= A14) "isFlavorTwoDeviceExp" else "isFlavorTwoDevice"
            }.hook {
                when (mode) {
                    "1" -> {
                        replaceTo(true)
                    }
                    "2" -> {
                        replaceTo(false)
                    }
                }
            }
        }
    }
}