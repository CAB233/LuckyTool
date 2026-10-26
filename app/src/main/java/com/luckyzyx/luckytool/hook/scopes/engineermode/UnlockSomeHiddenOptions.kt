package com.luckyzyx.luckytool.hook.scopes.engineermode

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object UnlockSomeHiddenOptions : Hooker {
    override fun onHook() {
        //Source SecrecyServiceHelper
        "com.oplus.engineermode.impl.SecrecyServiceHelper".toClass().resolve().apply {
            firstMethod { name = "isSecrecySupported" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "getSecrecyState" }.hook {
                replaceTo(false)
            }
        }
    }
}