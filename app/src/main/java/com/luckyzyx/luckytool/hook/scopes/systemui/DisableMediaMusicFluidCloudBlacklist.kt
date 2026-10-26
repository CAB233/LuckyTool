package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.oplus.media.OplusMediaControlManager
import com.luckyzyx.luckytool.hook.core.*

object DisableMediaMusicFluidCloudBlacklist : Hooker {
    override fun onHook() {
        //Source OplusMediaDataModelImpl
        "com.oplus.systemui.media.model.OplusMediaDataModelImpl".toClass().resolve().apply {
            firstMethod {
                name = "setMediaControlBlackList"
                parameters(List::class)
            }.hook {
                before {
                    val manager = firstField {
                        type = OplusMediaControlManager::class
                    }.of(instance).get<OplusMediaControlManager>() ?: return@before
                    manager.setMediaControlDenyList(listOf(""))
                    result = null
                }
            }
        }
    }
}