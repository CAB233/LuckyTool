package com.luckyzyx.luckytool.hook.globals

import android.util.ArrayMap
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookGlobalPmsFeature : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        val list = ArrayMap<String, Boolean>().apply {

        }
        loadHooker(PmsFeature(list))
    }

    class PmsFeature(private val features: Map<String, Boolean>) : Hooker {
        override fun onHook() {
            //Source PackageManagerService
            "com.android.server.pm.PackageManagerService".toClass().resolve().apply {
                firstMethod {
                    name = "hasSystemFeature"
                    parameters(String::class, Int::class)
                    returnType = Boolean::class
                }.hook {
                    before {
                        val key = args().first().cast<String>()
                        if (key.isNullOrBlank()) return@before
                        val value = features[key]
                        if (value != null) result = value
                    }
                }
            }
        }
    }
}