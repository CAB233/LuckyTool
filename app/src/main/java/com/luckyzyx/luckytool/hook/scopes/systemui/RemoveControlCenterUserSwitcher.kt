package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveControlCenterUserSwitcher : Hooker {
    override fun onHook() {
        //Search Log showUserSwitcher
        "com.oplusos.systemui.qs.OplusQSFooterImpl".toClass().resolve().apply {
            firstMethod {
                name = "showUserSwitcher"
                emptyParameters()
                returnType = Boolean::class
            }.hook {
                replaceTo(false)
            }
        }
    }
}