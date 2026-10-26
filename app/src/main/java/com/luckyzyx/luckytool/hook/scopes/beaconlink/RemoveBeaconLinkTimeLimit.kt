package com.luckyzyx.luckytool.hook.scopes.beaconlink

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveBeaconLinkTimeLimit(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source IDs.java
        dexKitBridge.findClass {
            matcher {
                fields {
                    add {
                        type(classOf<String>())
                        addReadMethod {
                        returnType(classOf<HashMap<*,*>>())
                        }
                    }
                    add {
                        type(classOf<Long>())
                        addWriteMethod {
                        paramTypes(classOf<Context>(), classOf<String>(), classOf<String>())
                        }
                        addReadMethod {
                        paramTypes(classOf<Context>(), classOf<String>(), classOf<String>())
                        }
                    }
                }
                fieldCount(2)
            }
        }.apply {
            checkDataList("IDs")

            single().name.toClass().resolve().apply {
                firstMethodOrNull {
                    parameters(String::class)
                    returnType = Boolean::class
                }?.hook {
                    replaceTo(true)
                } ?: run {
                    firstConstructor {
                        parameters(String::class, Long::class)
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