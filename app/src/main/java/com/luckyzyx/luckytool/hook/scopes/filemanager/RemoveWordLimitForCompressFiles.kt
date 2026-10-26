package com.luckyzyx.luckytool.hook.scopes.filemanager

import android.text.InputFilter
import android.widget.EditText
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveWordLimitForCompressFiles(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source CompressConfirmDialog
        dexKitBridge.findClass {
            matcher {
                methods {
                    add { name("onTextChanged") }
                    add { paramTypes(classOf<EditText>(), classOf<InputFilter>()) }
                }
                usingStrings("CompressConfirmDialog")
            }
        }.apply {
            checkDataList("CompressConfirmDialog")

            findMethod {
                matcher {
                    paramCount(0)
                    returnType(classOf<Int>())
                    usingNumbers(50)
                }
            }.apply {
                checkDataList("MaxCount")

                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        emptyParameters()
                        returnType = Int::class
                    }.hook {
                        replaceTo(9999)
                    }
                }
            }
        }
    }
}