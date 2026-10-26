package com.luckyzyx.luckytool.hook.scopes.browser

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveBrowserWindowLimitNumber(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source TabManager
        dexKitBridge.findClass {
            matcher {
                className("com.android.browser.TabManager")
            }
        }.findMethod {
            matcher {
                paramCount(0)
                returnType(classOf<Int>())
                usingStrings("TabManager", "multiWindowPerf")
            }
        }.apply {
            checkDataList("RemoveBrowserWindowLimitNumber")
            single().className.toClass().resolve().apply {
                firstMethod {
                    name = single().methodName
                    emptyParameters()
                    returnType = Int::class
                }.hook {
                    replaceTo(999)
                }
            }
        }
    }
}