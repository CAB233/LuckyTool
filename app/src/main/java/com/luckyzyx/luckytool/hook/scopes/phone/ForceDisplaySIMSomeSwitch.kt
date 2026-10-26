package com.luckyzyx.luckytool.hook.scopes.phone

import android.content.Context
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import com.luckyzyx.luckytool.hook.core.*

class ForceDisplaySIMSomeSwitch(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val volteCall = prefs(ModulePrefs).getBoolean("force_display_volte_calls", false)
        val preferredNetwork =
            prefs(ModulePrefs).getBoolean("force_display_preferred_network_type", false)

        //Source OplusSimInfoActivity
        dexKitBridge.findClass {
            matcher {
                className(
                    "com.android.simsettings.activity.OplusSimInfoActivity",
                    StringMatchType.StartsWith
                )
                addFieldForType(classOf<Context>())
                addFieldForType(classOf<String>())
                addFieldForType(classOf<Boolean>())
                addFieldForType(classOf<View>())
            }
        }.apply {
            checkDataList("ForceDisplaySIMSomeSwitch Clazz")

            //Source OplusSimInfoActivity changeVolteSwitchConfig
            findMethod {
                matcher {
                    paramTypes(classOf<Int>(), null, null)
                    returnType(Void.TYPE)
                    usingNumbers(1, 2, 3, 4, 7)
                    usingStrings("changeVolteSwitchConfig", "SIMS_OplusSimInfoActivity")
                    addUsingField {
                        type("com.coui.appcompat.preference.COUISwitchPreference")
                    }
                }
            }.apply {
                checkDataList("ForceDisplaySomeSwitch changeVolteSwitchConfig")
                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        parameters {
                            it[0] == classOf<Int>()
                                    && it.contains(classOf<Boolean>())
                                    && it.contains(classOf<String>())
                        }
                        parameterCount = 3
                    }.hook {
                        before {
                            if (!volteCall) return@before
                            val type = args().first().cast<Int>() ?: 0
                            val bool = arguments.find { it is Boolean } ?: return@before
                            val index = args.indexOf(bool).takeIf { it != -1 } ?: return@before
                            if (type == 1) args(index).set(true)
                        }
                    }
                }
            }

            //Source OplusSimInfoActivity changeNetworkModeConfig
            findMethod {
                matcher {
                    paramTypes(classOf<Int>(), null, null)
                    returnType(Void.TYPE)
                    usingNumbers(1, 2, 5)
                    usingStrings("changeNetworkModeConfig", "SIMS_OplusSimInfoActivity")
                    addUsingField {
                        type("com.coui.appcompat.preference.COUIJumpPreference")
                    }
                }
            }.apply {
                checkDataList("ForceDisplaySomeSwitch changeNetworkModeConfig")
                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        parameters {
                            it[0] == classOf<Int>()
                                    && it.contains(classOf<Boolean>())
                                    && it.contains(classOf<String>())
                        }
                        parameterCount = 3
                    }.hook {
                        before {
                            if (!preferredNetwork) return@before
                            val type = args().first().cast<Int>() ?: 0
                            val bool = arguments.find { it is Boolean } ?: return@before
                            val index = args.indexOf(bool).takeIf { it != -1 } ?: return@before
                            if (type == 1) args(index).set(true)
                        }
                    }
                }
            }
        }
    }
}