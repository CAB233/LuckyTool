package com.luckyzyx.luckytool.hook.scopes.battery

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveBatteryRestrictPlugin(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source PluginSupporter
        //Search loadRestrictPlugin / battery_restrict_plugin
        dexKitBridge.findClass {
            matcher {
                addFieldForType(classOf<Context>())
                addFieldForType(classOf<String>())
                usingStrings(
                    "loadRestrictPlugin",
                    "onPluginConnected"
                )
            }
        }.apply {
            checkDataList("PluginSupporter")

            findMethod {
                matcher {
                    usingStrings("loadRestrictPlugin")
                }
            }.apply {
                checkDataList("loadRestrictPlugin", onlyOne = false)

                forEach {
                    it.className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameterCount = single().paramCount
                        }.hook {
                            intercept()
                        }
                    }
                }
            }
        }
    }
}