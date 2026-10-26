package com.luckyzyx.luckytool.hook.scopes.camera

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveFilterModelLimit : Hooker {
    override fun onHook() {
        //Source SystemUtil
        "com.oplus.ocs.camera.ipusdk.processunit.filter.list.SystemUtil".toClass().resolve().apply {
            firstMethod { name = "isMarketNameContainSeriesNum" }.hook {
                replaceTo(true)
            }
        }
    }
}