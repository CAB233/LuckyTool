package com.luckyzyx.luckytool.hook.scopes.soundrecorder

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object HookBaseUtil : Hooker {
    override fun onHook() {
        //Source BaseUtil
        "com.soundrecorder.base.utils.BaseUtil".toClass().resolve().apply {
            firstMethod { name = "isRealme" }.hook {
                replaceTo(true)
            }
        }
    }
}