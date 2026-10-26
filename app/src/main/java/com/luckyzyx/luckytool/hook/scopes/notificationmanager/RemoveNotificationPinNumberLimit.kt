package com.luckyzyx.luckytool.hook.scopes.notificationmanager

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveNotificationPinNumberLimit : Hooker {
    override fun onHook() {
        //Source AppNotificationTopController
        "com.oplus.notificationmanager.property.uicontroller.AppNotificationTopController".toClass()
            .let {
                it.resolve().apply {
                    method {
                        parameters(it, "androidx.preference.Preference", Any::class)
                        returnType = Boolean::class
                    }.hookAll {
                        before {
                            val controller = args().first().any() ?: return@before
                            val bool = args().last().cast<Boolean>() ?: false
                            controller.asResolver().firstMethod { name = "onChange";superclass() }
                                .invoke(bool)
                            result = true
                        }
                    }
                }
            }
    }
}