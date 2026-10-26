package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object HookLauncherFeatureFlags : Hooker {
    override fun onHook() {
        val twoLine = prefs(ModulePrefs).getBoolean("enable_drawer_layout_double_line_names", false)

        //Source FeatureFlags
        "com.android.launcher3.config.FeatureFlags".toClass().resolve().apply {
            firstMethod {
                name = "getDebugFlag"
                parameterCount = 3
            }.hook {
                before {
                    val key = args().first().cast<String>() ?: ""
//                    val defValue = args(1).cast<Boolean>() ?: false
//                    val description = args().last().cast<String>() ?: ""
                    when (key) {
                        "ENABLE_TWOLINE_ALLAPPS" -> if (twoLine) args(1).set(true)
                    }
                }
            }
        }
    }
}