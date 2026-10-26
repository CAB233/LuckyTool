package com.luckyzyx.luckytool.hook.scopes.systemui

import android.os.Message
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object RemoveStatusBarSecurePayment : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 40) loadHooker(StatusBarSecurePayment)
        else loadHooker(StatusBarSecurePaymentV16)
    }

    object StatusBarSecurePayment : Hooker {
        override fun onHook() {
            //Source SecurePaymentRepository
            "com.oplus.systemui.statusbar.phone.dynamic.pipeline.data.securepayment.SecurePaymentRepository".toClass()
                .resolve().apply {
                    firstMethod {
                        name = "updateDetectionState"
                        parameters(Int::class)
                    }.hook {
                        intercept()
                    }
                }
        }
    }

    object StatusBarSecurePaymentV16 : Hooker {
        override fun onHook() {
            //Source SecurePaymentController
            VariousClass(
                "com.oplus.systemui.statusbar.phone.securepay.SecurePaymentControllerExImpl", //C12 C13
                "com.oplus.systemui.statusbar.phone.dynamic.SecurePaymentController" //C14
            ).toClass().resolve().apply {
                firstMethod {
                    name = "handlePaymentDetectionMessage"
                    parameters(Message::class)
                }.hook {
                    intercept()
                }
            }
        }
    }
}