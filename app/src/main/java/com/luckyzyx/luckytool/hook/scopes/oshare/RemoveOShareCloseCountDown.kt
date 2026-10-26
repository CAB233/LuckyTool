package com.luckyzyx.luckytool.hook.scopes.oshare

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveOShareCloseCountDown(val dexKitBridge: DexKitBridge) : Hooker {

    override fun onHook() {
        val osCode = getOSVersionCode

        if (osCode >= 27) loadHooker(HookOShareFeatureConfig(dexKitBridge))
        loadHooker(HookOShareSpUtils(dexKitBridge))
    }

    class HookOShareFeatureConfig(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source OShareFeatureConfig
            dexKitBridge.findClass {
                matcher {
                    usingStrings("OShareFeatureConfig")
                }
            }.apply {
                checkDataList("OShareFeatureConfig")
                findMethod {
                    matcher {
                    paramTypes(classOf<Context>())
                        returnType(classOf<Long>())
                        usingStrings("getSwitchTimeOut")
                    }
                }.apply {
                    checkDataList("OShareFeatureConfig getSwitchTimeOut")
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().name
                            parameters(Context::class)
                            returnType = Long::class
                        }.hook {
                            replaceTo(0L)
                        }
                    }
                }
            }
        }
    }

    class HookOShareSpUtils(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source SpUtils
            dexKitBridge.findClass {
                matcher {
                    usingStrings("SpUtils", "share_config")
                }
            }.apply {
                checkDataList("SpUtils")
                findMethod {
                    matcher {
                    paramTypes(classOf<Context>(), classOf<Long>())
                        usingStrings("updateLastTurnOnTime", "key_last_turn_on_time")
                    }
                }.apply {
                    checkDataList("SpUtils updateLastTurnOnTime")
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            parameters(Context::class, Long::class)
                        }.hook {
                            before {
                                args().last().set(0L)
                            }
                        }
                    }
                }
            }
        }
    }
}