package com.luckyzyx.luckytool.hook.scopes.systemui

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object HidePanoramicAodStatusBar : Hooker {
    override fun onHook() {
        //Source AodData
        val aodData = "com.oplus.systemui.aod.aodclock.constant.AodData".toClass()

        //Source KeyguardStatusBarViewExImpl
        "com.oplus.systemui.statusbar.phone.KeyguardStatusBarViewExImpl".toClass().resolve().apply {
            firstMethod {
                name = "hookDozingState"
                parameters(Boolean::class)
                returnType = Boolean::class
            }.hook {
                before {
                    val context = firstField { type = Context::class }.of(instance).get<Context>()
                        ?: return@before
                    val aodDataInstance = aodData.resolve().firstMethod {
                        name = "getInstance"
                        parameters(Context::class)
                    }.invoke(context) ?: return@before
                    val isPanoramicAod = aodDataInstance.asResolver().firstMethod {
                        name = "isPanoramicAod"
                    }.invoke<Boolean>() ?: return@before
                    if (args(0).cast<Boolean>() ?: false && isPanoramicAod) result = false
                }
            }
        }
    }
}
