package com.luckyzyx.luckytool.hook.scopes.uiengine

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveAodNotificationWhitelist : Hooker {
    override fun onHook() {
        //Source NotificationView -> BaseView
        "com.oplus.egview.widget.BaseView".toClass().resolve().apply {
            firstMethod { name = "isExpRegion" }.hook {
                replaceTo(true)
            }
        }
    }
}