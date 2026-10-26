package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveAodMusicWhitelist : Hooker {
    override fun onHook() {
        //Source AodMediaDataListener
        "com.oplusos.systemui.aod.mediapanel.AodMediaDataListener\$Companion".toClass().resolve().apply {
            firstMethod { name = "isAodMediaSupport" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "isAodMediaSupportWithoutFeature" }.hook {
                replaceTo(true)
            }
        }
    }
}