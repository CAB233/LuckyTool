package com.luckyzyx.luckytool.hook.scopes.screenshot

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.VagueType
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class CustomizeLongScreenshotMaxCapturedPages(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source ScrollCaptureConfigs -> scroll_configs_max_captured_pages / scroll_configs_max_captured_pixels
        //Source StitchLimitUtils -> isCapturedPagesReachLimit / trimToStitchLimit
        dexKitBridge.findClass {
            matcher {
                fieldCount(0)
                methods {
                    add { returnType(classOf<Int>()) }
                    add { returnType(classOf<Boolean>()) }
                    add {
                        paramTypes(classOf<Int>(), classOf<Int>())
                        returnType(classOf<Int>())
                    }
                }
                usingStrings("StitchLimitUtils")
            }
        }.apply {
            checkDataList("CustomizeLongScreenshotMaxCapturedPages")
            single().name.toClass().resolve().apply {
                //isCapturedPagesReachLimit
                firstMethod {
                    parameters(VagueType, Int::class)
                    parameterCount = 2
                    returnType = Boolean::class
                }.hook {
                    replaceTo(false)
                }
                //trimToStitchLimit
                firstMethod {
                    parameters(VagueType, Int::class, Int::class)
                    parameterCount = 3
                    returnType = Int::class
                }.hook {
                    replaceTo(-1)
                }
            }
        }
    }
}