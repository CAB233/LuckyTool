package com.luckyzyx.luckytool.hook.scopes.multiapp

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveMultiAppBlacklist : Hooker {
    override fun onHook() {
        //Source MultiAppBlackListUpdateHelper
        "com.oplus.multiapp.utils.MultiAppBlackListUpdateHelper".toClass().resolve().apply {
            firstMethod { name = "loadMultiappBlackListConfig" }.hook {
                intercept()
            }
        }
    }
}