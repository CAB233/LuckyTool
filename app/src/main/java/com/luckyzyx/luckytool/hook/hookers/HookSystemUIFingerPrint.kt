package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.systemui.FingerPrintIconAnim
import com.luckyzyx.luckytool.hook.core.*

object HookSystemUIFingerPrint : Hooker {
    override fun onHook() {
        //指纹图标
        loadHooker(FingerPrintIconAnim)
    }
}