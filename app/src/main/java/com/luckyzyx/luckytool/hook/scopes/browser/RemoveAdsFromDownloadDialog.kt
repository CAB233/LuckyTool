package com.luckyzyx.luckytool.hook.scopes.browser

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveAdsFromDownloadDialog(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source DownloadCardAdProvider
        dexKitBridge.findMethod {
            matcher {
                declaredClass {
                addFieldForType(classOf<Context>())
                    addFieldForType(classOf<String>())
                    addMethod {
                    paramTypes(classOf<Context>(), classOf<Int>())
                        returnType(Void.TYPE)
                    }
                    usingStrings("DownloadCardAdProvider")
                }
                usingStrings("DownloadCardAdProvider", "createAdRequest", "appName", "posIds")
            }
        }.apply {
            checkDataList("RemoveAdsFromDownloadDialog")
            single().className.toClass().resolve().apply {
                firstMethod {
                    name = single().methodName
                }.hook {
                    intercept()
                }
            }
        }
    }
}