package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveControlCenterCarriers : Hooker {
    override fun onHook() {
        //Source OplusSecondCarrierText
        "com.oplus.systemui.qs.widget.OplusSecondCarrierText".toClass().resolve().apply {
            firstConstructor {
                parameterCount = 3
            }.hook {
                after {
                    firstField { name = "mCarrierTextCallback" }.of(instance).set(null)
                }
            }
        }
    }
}