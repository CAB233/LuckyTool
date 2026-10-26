package com.luckyzyx.luckytool.hook.scopes.securitypermission

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class EnableAlwaysAllowAppStartDialog(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        loadHooker(HookAlwaysAllowButton(dexKitBridge))
        loadHooker(HookValidTime(dexKitBridge))
    }

    class HookAlwaysAllowButton(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source COUIAlertDialogBuilder
            dexKitBridge.findClass {
                matcher {
                    fields {
                    addForType(classOf<Configuration>())
                        addForType(classOf<ComponentCallbacks>())
                        addForType(classOf<DialogInterface.OnClickListener>())
                    }
                    usingStrings("COUIAlertDialogBuilder")
                }
            }.apply {
                checkDataList("COUIAlertDialogBuilder")

                findMethod {
                    matcher {
                        paramTypes(
                            classOf<Int>(),
                            classOf<DialogInterface.OnClickListener>(),
                            classOf<Boolean>()
                        )
                        addUsingField {
                            type(classOf<Int>())
                        }
                        usingNumbers(android.R.id.button3)
                    }
                }.apply {
                    checkDataList("setButton")

                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameters(
                                Int::class,
                                DialogInterface.OnClickListener::class,
                                Boolean::class
                            )
                        }.hook {
                            before {
                                val resId = args().first().cast<Int>() ?: 0
                                val listener = args(1).any() ?: return@before
                                val activity =
                                    listener.asResolver().firstField { type = Activity::class }
                                        .get<Activity>() ?: return@before
                                val allow30Id = activity.resources.getIdentifier(
                                    "app_start_dialog_allow_30", "string",
                                    this@HookAlwaysAllowButton.packageName
                                ).takeIf { it > 0 } ?: return@before
                                val alwaysAllowId = activity.resources.getIdentifier(
                                    "app_start_dialog_always_allow", "string",
                                    this@HookAlwaysAllowButton.packageName
                                ).takeIf { it > 0 } ?: return@before
                                if (resId == allow30Id) args().first().set(alwaysAllowId)
                            }
                        }
                    }
                }
            }
        }
    }

    class HookValidTime(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source OplusPermissionManager
            dexKitBridge.findClass {
                matcher {
                    fields {
                        addForType("android.os.ISecurityPermissionService")
                    }
                    usingStrings("OplusPermissionManager")
                }
            }.apply {
                checkDataList("OplusPermissionManager")

                findMethod {
                    matcher {
                        paramTypes(classOf<Bundle>())
                        usingStrings("OplusPermissionManager", "putActivityStartWhiteList")
                    }
                }.apply {
                    checkDataList("putActivityStartWhiteList")

                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameters(Bundle::class)
                        }.hook {
                            before {
                                val bundle = args().first().cast<Bundle>() ?: return@before
                                bundle.remove("valid_time")
                            }
                        }
                    }
                }
            }
        }
    }
}