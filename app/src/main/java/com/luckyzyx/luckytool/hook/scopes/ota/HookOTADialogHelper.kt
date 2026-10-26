package com.luckyzyx.luckytool.hook.scopes.ota

import android.content.Context
import android.content.DialogInterface
import android.view.Window
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class HookOTADialogHelper(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val autoDownload = prefs(ModulePrefs).getBoolean("remove_ota_auto_download_dialog", false)

        //Source OTADialogHelper
        dexKitBridge.findClass {
            matcher {
            addMethod { paramTypes(classOf<Window>()) }
                addMethod {
                paramTypes(classOf<Context>(), classOf<DialogInterface.OnClickListener>())
                }
                usingStrings("OTADialogHelper", "auto_download_network_type")
            }
        }.apply {
            checkDataList("OTADialogHelper")

            if (autoDownload) findMethod {
                matcher {
                    returnType(Void.TYPE)
                    usingStrings("auto_download_network_type")
                    //R.array.data_network_switch_array
                }
            }.apply {
                checkDataList("AutoDownloadDialog")

                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        returnType = Void.TYPE
                    }.hook {
                        intercept()
                    }
                }
            }
        }
    }
}