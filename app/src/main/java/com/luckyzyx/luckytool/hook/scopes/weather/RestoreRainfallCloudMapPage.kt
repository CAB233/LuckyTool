package com.luckyzyx.luckytool.hook.scopes.weather

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RestoreRainfallCloudMapPage : Hooker {
    override fun onHook() {
        //Source IndexOperationsManager
        "com.oplus.weather.indexoperations.IndexOperationsManager".toClassOrNull()?.resolve()
            ?.apply {
                firstMethod { name = "supportIndexOperationsFeature" }.hook {
                    replaceTo(false)
                }
            }
    }
}