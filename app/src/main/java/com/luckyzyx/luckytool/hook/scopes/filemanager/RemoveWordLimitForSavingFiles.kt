package com.luckyzyx.luckytool.hook.scopes.filemanager

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveWordLimitForSavingFiles(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 40) loadHooker(WordLimitForSavingFiles(dexKitBridge))
        else loadHooker(WordLimitForSavingFilesV16(dexKitBridge))

    }

    class WordLimitForSavingFiles(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source PickerInputView
            dexKitBridge.findClass {
                matcher {
                    className("com.filemanager.common.view.picker.PickerInputView")
                }
            }.apply {
                checkDataList("PickerInputView")

                findField {
                    matcher {
                        type(classOf<Int>())
                        addReadMethod {
                            paramCount(0)
                            returnType(Void.TYPE)
                        }
                        addReadMethod {
                            paramCount(4)
                            returnType(Void.TYPE)
                        }
                    }
                }.apply {
                    checkDataList("MaxCount17")

                    single().className.toClass().resolve().apply {
                        firstConstructor { parameterCount = 3 }.hook {
                            after {
                                firstField { name = single().fieldName; type = Int::class }.of(
                                    instance
                                )
                                    .set(9999)
                            }
                        }
                    }
                }
            }
        }
    }

    class WordLimitForSavingFilesV16(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source ActionModeController（c16 及以下：限制字段在控制器，构造器 1 参）
            dexKitBridge.findClass {
                matcher {
                    className("com.oplus.filemanager.picker.controller.ActionModeController")
                }
            }.apply {
                checkDataList("ActionModeController")

                findField {
                    matcher {
                        type(classOf<Int>())
                        addReadMethod {
                            paramCount(0)
                            returnType(Void.TYPE)
                        }
                        addReadMethod {
                            paramCount(4)
                            returnType(Void.TYPE)
                        }
                    }
                }.apply {
                    checkDataList("MaxCount")

                    single().className.toClass().resolve().apply {
                        firstConstructor { parameterCount = 1 }.hook {
                            after {
                                firstField { name = single().fieldName; type = Int::class }.of(
                                    instance
                                )
                                    .set(9999)
                            }
                        }
                    }
                }
            }
        }
    }

}