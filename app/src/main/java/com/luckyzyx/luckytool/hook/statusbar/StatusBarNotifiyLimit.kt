package com.luckyzyx.luckytool.hook.statusbar

import com.luckyzyx.luckytool.hook.scopes.systemui.AllowLongPressNotificationModifiable
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object StatusBarNotifiyLimit : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //允许长按通知可修改
        if (prefs(ModulePrefs).getBoolean("allow_long_press_notification_modifiable", false)) {
            if (osCode <= 30) loadHooker(AllowLongPressNotificationModifiable)
        }
    }
}