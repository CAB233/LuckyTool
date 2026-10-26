package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.A14
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.hook.core.*

object AllowUntrustedTouch : Hooker {
    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("allow_untrusted_touch", false)

        //Source UntrustedTouchController
        "com.android.server.input.UntrustedTouchController".toClass().resolve().apply {
            firstMethod { name = "isOplusTrustedApp" }.hook {
                if (isEnable) {
                    replaceTo(true)
                }
            }
            firstMethod { name = "showTipsDialog" }.hook {
                if (isEnable) intercept()
            }
        }
        //Source WindowStateExtImpl
        "com.android.server.wm.WindowStateExtImpl".toClass().resolve().apply {
            firstMethod { name = "isOplusTrustedWindow" }.hook {
                if (isEnable) {
                    replaceTo(true)
                }
            }
        }
        if (SDK >= A14) return
        //Source InputManager
        "android.hardware.input.InputManager".toClass().resolve().apply {
            firstMethod { name = "getBlockUntrustedTouchesMode" }.hook {
                if (isEnable) {
                    replaceTo(0)
                }
            }
        }
    }
}