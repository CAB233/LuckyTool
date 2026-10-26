package com.luckyzyx.luckytool.hook.scopes.games

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveGameAssistantTemperatureDetection : Hooker {
    override fun onHook() {
        //Source CoolingBubbleTipsHelper
        "business.module.perfmode.CoolingBubbleTipsHelper".toClass().resolve().apply {
            field { type = Int::class }.forEach {
                val value = it.get<Int>() ?: return@forEach
                if (value > 0) it.set(100)
            }
        }
    }
}