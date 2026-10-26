package com.luckyzyx.luckytool.hook.scopes.games

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableGameBypassChargingSupport : Hooker {
    override fun onHook() {
        //Source COSAExportedImpl
        "com.oplus.cosa.exported.COSAExportedImpl".toClass().resolve().apply {
            firstMethod { name = "getBypassChargingDeviceSupport" }.hook{
                replaceTo(2)
            }
        }
    }
}