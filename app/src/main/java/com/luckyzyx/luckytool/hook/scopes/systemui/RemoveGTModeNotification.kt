package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.utils.A14
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.hook.core.*

object RemoveGTModeNotification : Hooker {
    override fun onHook() {
        //Source GTUtils
        VariousClass(
            "com.oplusos.systemui.statusbar.util.GTUtils", //C13
            "com.oplus.systemui.statusbar.util.GTUtils" //C14
        ).toClass().resolve().apply {
            firstMethod {
                name = if (SDK >= A14) "notifyOpenGtMode"
                else "showOpenGtModeNotify"
            }.hook {
                intercept()
            }
        }
    }
}