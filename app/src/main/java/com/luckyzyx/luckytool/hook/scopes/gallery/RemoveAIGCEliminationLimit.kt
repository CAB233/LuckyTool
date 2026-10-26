package com.luckyzyx.luckytool.hook.scopes.gallery

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveAIGCEliminationLimit(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source EliminateDetectInfo / PanoramicSegmentationInfo
        dexKitBridge.findClass {
            matcher {
                addFieldForType(classOf<Boolean>())
                addMethod { name("equals") }
                addMethod { name("hashCode") }
                addMethod { name("toString") }
                usingStrings("Info", "isContentSensitive")
            }
        }.apply {
            checkDataList("EliminateDetectInfo")

            single().name.toClass().resolve().apply {
                firstConstructor { parameters { it.contains(classOf<Boolean>()) } }.hook {
                    before {
                        args.forEachIndexed { index, it ->
                            if (it is Boolean) args(index).set(false)
                            if (it?.javaClass?.isEnum == true) args(index).set(null)
                        }
                    }
                }
            }
        }

        //Source EliminateStack / PanoramicSegmentationStack
        dexKitBridge.findClass {
            matcher {
                addFieldForType(classOf<Int>())
                addFieldForType(classOf<String>())
                addFieldForType(classOf<Boolean>())
                addMethod { name("equals") }
                addMethod { name("hashCode") }
                addMethod { name("toString") }
                usingStrings("EliminateSaveEntry", "isContentSensitive")
            }
        }.apply {
            checkDataList("EliminateSaveEntry")

            single().name.toClass().resolve().apply {
                firstConstructor { parameters { it.contains(classOf<Boolean>()) } }.hook {
                    before {
                        args.forEachIndexed { index, it ->
                            if (it is Boolean) args(index).set(false)
                            if (it?.javaClass?.isEnum == true) args(index).set(null)
                        }
                        if (args().last().any() is Boolean) args().last().set(true)
                    }
                }
            }
        }
    }
}