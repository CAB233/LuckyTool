package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object ADBInstallConfirm : Hooker {
    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("remove_adb_install_confirm", false)

        //Source OplusPackageInstallInterceptManager
        VariousClass(
            "com.android.server.pm.ColorPackageInstallInterceptManager", //A11
            "com.android.server.pm.OplusPackageInstallInterceptManager"
        ).toClass().resolve().apply {
            firstMethod { name = "allowInterceptAdbInstallInInstallStage" }.hook {
                if (isEnable) {
                    replaceTo(false)
                }
            }
        }
    }
}