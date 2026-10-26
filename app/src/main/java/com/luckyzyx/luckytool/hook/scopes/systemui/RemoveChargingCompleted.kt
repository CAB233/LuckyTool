package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.hook.core.*

object RemoveChargingCompleted : Hooker {
    override fun onHook() {
        //Source OplusPowerNotificationWarnings
        VariousClass(
            "com.coloros.systemui.notification.power.ColorosPowerNotificationWarnings", //A11
            "com.oplusos.systemui.notification.power.OplusPowerNotificationWarnings",
            "com.oplus.systemui.statusbar.notification.power.OplusPowerNotificationWarnings" //C14
        ).toClass().resolve().apply {
            firstMethod { name = "showChargeErrorDialog";parameterCount = 1 }.hook {
                before {
                    if ((args().first().cast<Int>() ?: 0) == 7) result = null
                }
            }
        }
    }
}