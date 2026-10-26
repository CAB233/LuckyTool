package com.luckyzyx.luckytool.hook.scopes.notificationmanager

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveNotificationManagerLimit : Hooker {
    override fun onHook() {
        //Source ControllerChannelGroup$AppItemListener -> 通知渠道列表允许通知锁
        "com.oplus.notificationmanager.property.uicontroller.ControllerChannelGroup\$AppItemListener".toClass()
            .resolve().apply {
                firstMethod { name = "isSwitchEnabled" }.hook {
                    replaceTo(true)
                }
            }
        //Source ControllerAllowNotificationChannel -> 通知渠道内允许通知锁
        "com.oplus.notificationmanager.property.uicontroller.ControllerAllowNotificationChannel".toClass()
            .resolve().apply {
                firstMethod { name = "isNormAppEnabled" }.hook {
                    replaceTo(true)
                }
            }
        //Source ControllerUnimportantChannel -> 通知渠道内不重要通知锁
        "com.oplus.notificationmanager.property.uicontroller.ControllerUnimportantChannel".toClass()
            .resolve().apply {
                firstMethod { name = "isNormAppEnabled" }.hook {
                    replaceTo(true)
                }
            }
        //Source ControllerAllowNotificationPkg -> 应用内允许通知锁
        "com.oplus.notificationmanager.property.uicontroller.ControllerAllowNotificationPkg".toClass()
            .resolve().apply {
                firstMethod { name = "isNormAppEnabled" }.hook {
                    replaceTo(true)
                }
            }
//
//        //Source ControllerAppLockBannerBubble -> 应用内气泡锁
//        "com.oplus.notificationmanager.property.uicontroller.ControllerAppLockBannerBubble".toClass()
//            .apply {
//                method { name = "isBubbleEnabled" }.hook {
//                    replaceToTrue()
//                }
//            }
//        //Source ControllerAppLockBannerBubble ->
//        "com.oplus.notificationmanager.property.uicontroller.ControllerChannelLockBannerBubble".toClass()
//            .apply {
//                method { name = "isBubbleEnabled" }.hook {
//                    replaceToTrue()
//                }
//            }
//        //Source ControllerAppLockBannerBubble ->
//        "com.oplus.notificationmanager.property.uicontroller.ControllerConversationLockBannerBubble".toClass()
//            .apply {
//                method { name = "isBubbleEnabled" }.hook {
//                    replaceToTrue()
//                }
//            }
    }
}