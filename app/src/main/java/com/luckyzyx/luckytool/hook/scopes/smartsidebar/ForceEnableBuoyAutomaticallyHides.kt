package com.luckyzyx.luckytool.hook.scopes.smartsidebar

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object ForceEnableBuoyAutomaticallyHides : Hooker {
    override fun onHook() {
        //Source EdgePanelUtils
        "com.coloros.edgepanel.utils.EdgePanelUtils".toClass().resolve().apply {
            firstMethodOrNull { name = "isMetaDataSupportByPackage";parameterCount = 2 }?.hook {
                after {
                    val packName = args().first().cast<String>() ?: ""
                    val key = args().last().cast<String>() ?: ""
                    if (packName == "com.android.systemui" && key == "sidebar_gesture_support") result = true
                }
            }
        }
    }
}