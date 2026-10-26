package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveStartRecordingOrCastingDialog : Hooker {
    override fun onHook() {
        //Source MediaProjectionServiceHelper
        "com.android.systemui.mediaprojection.MediaProjectionServiceHelper".toClass().resolve()
            .apply {
                firstMethod {
                    name = "hasProjectionPermission"
                    parameters(Int::class, String::class)
                    returnType = Boolean::class
                }.hook {
                    replaceTo(true)
                }
            }
    }
}