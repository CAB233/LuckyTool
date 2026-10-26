package com.luckyzyx.luckytool.hook.scopes.systemui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object RemoveStatusBarBottomNetworkWarn : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 34) loadHooker(StatusBarBottomNetworkWarn)
        else loadHooker(StatusBarBottomNetworkWarnOld)
    }

    object StatusBarBottomNetworkWarn : Hooker {
        override fun onHook() {
            var removeMode = prefs(ModulePrefs).getString("remove_control_center_networkwarn", "0")
            Channel.wait<String>("remove_control_center_networkwarn") { removeMode = it }

            //Source OplusQSSecurityController
            "com.oplus.systemui.qs.policy.OplusQSSecurityController".toClass().resolve().apply {
                firstMethod { name = "showDeviceMonitoringDialog" }.hook {
                    if (removeMode == "1" || removeMode == "2") intercept()
                }
                (firstMethodOrNull { name = "handleRefreshState" }
                    ?: firstMethod { name { it.contains("handleRefreshState") } }).hook {
                    if (removeMode == "2") intercept()
                }
            }
        }
    }

    object StatusBarBottomNetworkWarnOld : Hooker {
        override fun onHook() {
            var removeMode = prefs(ModulePrefs).getString("remove_control_center_networkwarn", "0")
            Channel.wait<String>("remove_control_center_networkwarn") { removeMode = it }

            //Source OplusQSSecurityText
            VariousClass(
                "com.oplusos.systemui.qs.widget.OplusQSSecurityText", //C13
                "com.oplus.systemui.qs.widget.OplusQSSecurityText" //C14
            ).toClass().resolve().apply {
                firstMethod { name = "handleClick" }.hook {
                    if (removeMode == "1" || removeMode == "2") intercept()
                }
                firstMethod { name = "handleRefreshState" }.hook {
                    if (removeMode == "2") intercept()
                }
            }
        }
    }
}