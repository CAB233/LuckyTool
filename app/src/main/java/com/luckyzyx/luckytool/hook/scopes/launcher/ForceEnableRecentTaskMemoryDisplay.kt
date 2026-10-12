package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

@Obfuscate
class ForceEnableRecentTaskMemoryDisplay(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
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
                    intercept(true)
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
                    intercept(true)
                }
            }
        } else {
            "com.oplus.quickstep.memory.MemoryInfoManager".toClass().resolve().apply {
                firstMethod { name = "isAllowMemoryInfoDisplay" }.hook { intercept(true) }
                firstMethod { name = "needMemoryDetail" }.hook { intercept(true) }
            }
        }
    }
}
