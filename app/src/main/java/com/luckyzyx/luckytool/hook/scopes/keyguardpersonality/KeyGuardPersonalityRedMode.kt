package com.luckyzyx.luckytool.hook.scopes.keyguardpersonality

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object KeyGuardPersonalityRedMode : Hooker {
    override fun onHook() {
        var redMode = prefs(ModulePrefs).getString("lock_screen_clock_redone_mode", "0")
        Channel.wait<String>("lock_screen_clock_redone_mode") { redMode = it }

        // ColorOS 17 将经典/数字时钟移入 personality APK，并提供独立红色 1 开关。
        //Source ClockViewRootModel
        listOf(
            "com.oplus.keyguard.clock.base.domain.model.ClockViewRootModel",
            "com.oplus.keyguard.clock.digital.domain.model.ClockViewRootModel"
        ).forEach { className ->
            className.toClass().resolve().firstMethod {
                name = "setOnePlusRedOneSwitch"
                parameters(Boolean::class)
            }.hook {
                before {
                    when (redMode) {
                        "1" -> args(0).set(true)
                        "2" -> args(0).set(false)
                    }
                }
            }
        }
    }
}
