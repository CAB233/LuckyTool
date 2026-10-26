package com.luckyzyx.luckytool.hook.scopes.packageinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.widget.Button
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import com.luckyzyx.luckytool.hook.core.*

class HookUninstallerActivity(val dexKitBridge: DexKitBridge) : Hooker {

    val autoUninstall = prefs(ModulePrefs).getBoolean("auto_click_uninstall_button", false)

    override fun onHook() {
        //Source UninstallerActivity
        dexKitBridge.findClass {
            matcher {
                className("com.android.packageinstaller.UninstallerActivity")
            }
        }.apply {
            checkDataList("UninstallerActivity")

            val showUninstallConfirmation = findMethod {
                matcher {
                    paramTypes(classOf<Intent>())
                    returnType(Void.TYPE)
                    addUsingField { type(classOf<Boolean>()) }
                    usingStrings("isUninstalledFont")
                }
            }.checkDataList("showUninstallConfirmation").single()

            if (autoUninstall) hookAutoUninstall(showUninstallConfirmation)
        }
    }

    @SuppressLint("DiscouragedApi")
    fun hookAutoUninstall(showUninstallConfirmation: MethodData) {
        showUninstallConfirmation.className.toClass().resolve().apply {
            firstMethod {
                name = showUninstallConfirmation.methodName
                parameters(Intent::class)
            }.hook {
                after {
                    val activity = instance<Activity>()
                    activity.findViewById<Button>(
                        activity.resources.getIdentifier(
                            "ok_button", "id",
                            this@HookUninstallerActivity.packageName
                        )
                    )?.performClick()
                }
            }
        }
    }

}