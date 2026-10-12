package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

@Obfuscate
class ForceEnableRecentTaskMemoryDisplay(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        //Source MemoryInfoManager
        //C16 及以下类名与方法名均未混淆，优先按名字定位；
        //C17 起被混淆，名字定位失败时才回退到 DexKit 按调用点特征定位。
        if (!hookByName()) hookByDexKit()
    }

    /**
     * C16 及以下：按未混淆的类名与方法名定位，两个方法都找到才 hook 并返回 true
     */
    private fun hookByName(): Boolean {
        val resolver = "com.oplus.quickstep.memory.MemoryInfoManager".toClassOrNull()
            ?.resolve() ?: return false
        val isAllowMemoryInfoDisplay = resolver.firstMethodOrNull {
            name = "isAllowMemoryInfoDisplay"
        } ?: return false
        val needMemoryDetail = resolver.firstMethodOrNull {
            name = "needMemoryDetail"
        } ?: return false
        isAllowMemoryInfoDisplay.hook { intercept(true) }
        needMemoryDetail.hook { intercept(true) }
        return true
    }

    /**
     * C17 起：类名与方法名被混淆，按调用点特征定位
     */
    private fun hookByDexKit() {
        val classList = dexKitBridge.findClass {
            matcher {
                usingStrings("MemoryInfoManager")
            }
        }.checkDataList("MemoryInfoManager")
        if (classList.isEmpty()) {
            YLog.error("MemoryInfoManager -> findClass isNullOrEmpty", tag = "LuckyTool")
            return
        }

        val needMemoryDetail = classList.findMethod {
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
        }.checkDataList("needMemoryDetail").firstOrNull()?.getMethodInstance(hostClassLoader!!)
        if (needMemoryDetail != null) needMemoryDetail.hook { intercept(true) }
        else YLog.error("needMemoryDetail -> findMethod isNullOrEmpty", tag = "LuckyTool")

        val isAllowMemoryInfoDisplay = classList.findMethod {
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
        }.checkDataList("isAllowMemoryInfoDisplay").firstOrNull()
            ?.getMethodInstance(hostClassLoader!!)
        if (isAllowMemoryInfoDisplay != null) isAllowMemoryInfoDisplay.hook { intercept(true) }
        else YLog.error("isAllowMemoryInfoDisplay -> findMethod isNullOrEmpty", tag = "LuckyTool")
    }
}
