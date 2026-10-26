package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object SystemEnableVolumeKeyControlFlashlight : Hooker {
    override fun onHook() {
        if (getOSVersionCode < 27) return
        val isEnable = prefs(ModulePrefs).getBoolean("enable_volume_key_control_flashlight", false)

        //Source OplusScreenOffTorchHelper
        "com.android.server.power.OplusScreenOffTorchHelper".toClassOrNull()?.resolve()?.apply {
            firstMethod {
                name = "getInstance"
                parameters(Context::class)
            }.hook {
                after {
                    if (!isEnable) return@after
                    val context = args().first().cast<Context>() ?: return@after

                    if (result == null) result = firstConstructor {
                        parameters(Context::class)
                    }.create(context)
                }
            }
        }
    }
}