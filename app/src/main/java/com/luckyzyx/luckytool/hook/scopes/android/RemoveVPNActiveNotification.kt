package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object RemoveVPNActiveNotification : Hooker {
    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("remove_vpn_active_notification", false)

        // Source OplusVpnHelper
        VariousClass(
            "com.android.server.connectivity.VpnExtImpl", //C12 C13 C14
            "com.android.server.connectivity.OplusVpnHelper"
        ).toClass().resolve().apply {
            firstMethod { name = "showNotification" }.hook {
                if (isEnable) intercept()
            }
        }
    }
}