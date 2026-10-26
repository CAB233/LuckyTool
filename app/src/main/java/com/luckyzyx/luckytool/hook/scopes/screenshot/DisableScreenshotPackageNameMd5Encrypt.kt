package com.luckyzyx.luckytool.hook.scopes.screenshot

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class DisableScreenshotPackageNameMd5Encrypt(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source EncryptUtils
        dexKitBridge.findClass {
            matcher {
            addMethod { returnType(classOf<String>()) }
                usingStrings("EncryptUtils", "encryptToMd5", "queryEncryptName")
            }
        }.apply {
            checkDataList("DisableScreenshotPackageNameMd5Encrypt")

            findMethod {
                matcher {
                    paramTypes(classOf<String>())
                    returnType(classOf<String>())
                    usingStrings("queryEncryptName")
                }
            }.apply {
                checkDataList("queryEncryptName")

                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        parameters(String::class)
                        returnType = String::class
                    }.hook {
                        before {
                            val packName = args().first().cast<String>() ?: ""
                            if (packName.isNotBlank()) result = packName
                        }
                    }
                }
            }
        }
    }
}