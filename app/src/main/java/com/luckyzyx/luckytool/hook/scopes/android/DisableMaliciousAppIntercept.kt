package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object DisableMaliciousAppIntercept : Hooker {
    override fun onHook() {
        //Source OplusAppStartConfirmManager
        "com.android.server.wm.OplusAppStartConfirmManager".toClass().resolve().apply {
            firstMethod { name = "checkMaliciousIntercept" }.hook {
                intercept()
            }
        }
    }
}