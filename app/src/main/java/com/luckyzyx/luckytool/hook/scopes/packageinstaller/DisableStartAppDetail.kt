package com.luckyzyx.luckytool.hook.scopes.packageinstaller

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class DisableStartAppDetail(val dexKitBridge: DexKitBridge) : Hooker {

    override fun onHook() {
        //Source AppDetailRedirectionUtils
        dexKitBridge.findClass {
            matcher {
                usingStrings("AppDetailRedirectionUtils", "RemoteAppdetailService")
            }
        }.apply {
            checkDataList("AppDetailRedirectionUtils")

            findMethod {
                matcher {
//                    paramTypes(Context::class.java, String::class.java)
                    returnType(classOf<Int>())
                    usingStrings("count_canceled_by_app_detail", "com.oplus.appdetail")
                }
            }.apply {
                checkDataList("checkCommon")

                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
//                        parameters(Context::class, String::class)
                        returnType = Int::class
                    }.hook {
                        replaceTo(9)
                    }
                }
            }
        }
    }
}
