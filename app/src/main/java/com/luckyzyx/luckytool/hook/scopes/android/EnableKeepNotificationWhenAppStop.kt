package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object EnableKeepNotificationWhenAppStop : Hooker {
    override fun onHook() {
        var isEnable =
            prefs(ModulePrefs).getBoolean("enable_keep_notification_when_app_stop", false)
        Channel.wait<Boolean>("enable_keep_notification_when_app_stop") { isEnable = it }

        //Source NotificationManagerService -> cancelAllNotificationsInt
        //Source OplusNotificationManagerServiceExtImpl -> shouldKeepNotifcationWhenForceStop
        //Source OplusNotificationCommonPolicy -> shouldKeepNotifcationWhenForceStop
        "com.android.server.notification.OplusNotificationManagerServiceExtImpl".toClass().resolve()
            .apply {
                firstMethod { name = "shouldKeepNotifcationWhenForceStop" }.hook {
                    before {
                        if (!isEnable) return@before
                        val reason = args().last().cast<Int>() ?: 0
                        if (reason == 10020 || reason == 10021) result = true
                    }
                }
            }
    }
}