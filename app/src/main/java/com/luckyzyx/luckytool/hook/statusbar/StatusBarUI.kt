package com.luckyzyx.luckytool.hook.statusbar

import com.luckyzyx.luckytool.hook.scopes.systemui.DoubleClickLockScreen
import com.luckyzyx.luckytool.hook.scopes.systemui.VibrateWhenOpeningTheStatusBar
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object StatusBarUI : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //双击状态栏锁屏
        if (prefs(ModulePrefs).getBoolean("statusbar_double_click_lock_screen", false)) {
            loadHooker(DoubleClickLockScreen)
        }
        //打开状态栏时振动
        if (prefs(ModulePrefs).getBoolean("vibrate_when_opening_the_statusbar", false)) {
            if (osCode >= 26) loadHooker(VibrateWhenOpeningTheStatusBar)
        }
    }
}