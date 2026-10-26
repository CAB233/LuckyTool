package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class ForceEnableRecentTaskMemoryDisplay(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        //Source MemoryInfoManager
        //C16 及以下类名与方法名均未混淆，按名字定位；C17 起被混淆，只能经 DexKit 按调用点特征定位
        if (osCode >= 40) {
            dexKitBridge.findClass {
                matcher {
                    usingStrings("MemoryInfoManager")
                }
            }.apply {
                checkDataList("MemoryInfoManager")

                findMethod {
                    matcher {
                        paramCount(0)
                        returnType(classOf<Boolean>())
                        callerMethods {
                            add { name("getIsMem") }
                            add {
                                paramTypes(classOf<Boolean>())
                                returnType(Void.TYPE)
                                usingNumbers(8)
                            }
                        }
                    }
                }.apply {
                    checkDataList("needMemoryDetail")
                }.single().getMethodInstance(hostClassLoader!!).hook {
                    replaceTo(true)
                }

                findMethod {
                    matcher {
                        paramCount(0)
                        returnType(classOf<Boolean>())
                        callerMethods {
                            add {
                                paramTypes(classOf<Boolean>())
                                returnType(Void.TYPE)
                                usingStrings("OplusRecentsView")
                            }
                        }
                    }
                }.apply {
                    checkDataList("isAllowMemoryInfoDisplay")
                }.single().getMethodInstance(hostClassLoader!!).hook {
                    replaceTo(true)
                }
            }
        } else {
            "com.oplus.quickstep.memory.MemoryInfoManager".toClass().resolve().apply {
                firstMethod { name = "isAllowMemoryInfoDisplay" }.hook { replaceTo(true) }
                firstMethod { name = "needMemoryDetail" }.hook { replaceTo(true) }
            }
        }
    }
}
