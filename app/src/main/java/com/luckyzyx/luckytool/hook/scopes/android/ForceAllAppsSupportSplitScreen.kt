package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object ForceAllAppsSupportSplitScreen : Hooker {
    override fun onHook() {
        var isEnable =
            prefs(ModulePrefs).getBoolean("force_all_apps_support_split_screen", false)
        Channel.wait<Boolean>("force_all_apps_support_split_screen") { isEnable = it }

        //Source OplusSplitScreenManagerService
        "com.android.server.wm.OplusSplitScreenManagerService".toClass().resolve().apply {
            method {
                name = "supportsSplitScreenByVendorPolicy"
                parameters { it[0] == classOf<String>() && it[1] == classOf<String>() }
                parameterCount { it in 3..4 }
            }.hookAll {
                before {
                    if (!isEnable) return@before
                    val packageName = args().first().cast<String>() ?: ""
                    val activityName = args(1).cast<String>() ?: ""
//                    val candidate = args(2).cast<Boolean>() ?: false

                    if (packageName.isBlank()) return@before

                    val isSafeSenterUI = firstMethod {
                        name = "isSafeSenterUI"
                        parameterCount = 1
                    }.of(instance).invoke<Boolean>(activityName) ?: false
                    if (isSafeSenterUI) return@before

                    if (method.parameterCount == 4) {
                        val userId = args().last().cast<Int>() ?: 0
                        val isHidenPackage = firstMethod {
                            name = "isHidenPackage"
                            parameterCount = 2
                        }.of(instance).invoke<Boolean>(packageName, userId) ?: false
                        if (isHidenPackage) return@before
                    }

                    result = true
                }
            }
            firstMethod { name = "isInForbidActivityList" }.hook {
                if (isEnable) {
                    replaceTo(false)
                }
            }
            firstMethod { name = "supportsSplitScreenWindowingMode" }.hook {
                if (isEnable) {
                    replaceTo(true)
                }
            }
        }
    }
}