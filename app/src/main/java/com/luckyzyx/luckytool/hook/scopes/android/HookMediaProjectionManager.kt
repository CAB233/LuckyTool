package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object HookMediaProjectionManager : Hooker {
    override fun onHook() {
        val isEnable =
            prefs(ModulePrefs).getBoolean("enable_record_calls_on_third_party_apps", false)

        //Source MediaProjectionManagerServiceExtImpl
        "android.media.projection.MediaProjectionManagerServiceExtImpl".toClassOrNull()?.resolve()?.apply {
            firstMethod { name = "isOplusApp";parameterCount = 1 }.hook {
                after {
                    if (!isEnable) return@after
                    val packageName = args().first().cast<String>() ?: ""
                    if (packageName == "com.oplus.audiomonitor") result = true
                }
            }
        }
    }
}