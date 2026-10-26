package com.luckyzyx.luckytool.hook.scopes.systemui

import android.view.OplusWindowManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.hook.core.*

object AutoWakeUpFaceUnlockNotification : Hooker {
    override fun onHook() {
        //Source WakeupScreenHelper
        VariousClass(
            "com.oplusos.systemui.notification.helper.WakeupScreenHelper", //C12.1
            "com.oplus.systemui.statusbar.notification.helper.WakeupScreenHelper", //C13
            "com.oplus.systemui.notification.interruption.wakeup.WakeupScreenHelper" //C14 C15
        ).toClass().resolve().apply {
            firstMethod { name = "powerOnScreen" }.hook {
                after {
                    OplusWindowManager().requestKeyguard("android.policy:POWER")
                }
            }
        }
    }
}