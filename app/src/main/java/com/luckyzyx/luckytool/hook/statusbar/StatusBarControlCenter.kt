package com.luckyzyx.luckytool.hook.statusbar

import com.luckyzyx.luckytool.hook.scopes.systemui.ControlCenterBackgroundTransParency
import com.luckyzyx.luckytool.hook.scopes.systemui.ControlCenterClockStyle
import com.luckyzyx.luckytool.hook.scopes.systemui.ControlCenterDateStyle
import com.luckyzyx.luckytool.hook.scopes.systemui.EnableControlCenterProgressPercentDisplay
import com.luckyzyx.luckytool.hook.scopes.systemui.EnableNotificationSideSpacing
import com.luckyzyx.luckytool.hook.scopes.systemui.NotificationBackgroundBlurAlpha
import com.luckyzyx.luckytool.hook.scopes.systemui.RemoveControlCenterCarriers
import com.luckyzyx.luckytool.hook.scopes.systemui.RemoveControlCenterUserSwitcher
import com.luckyzyx.luckytool.hook.scopes.systemui.RemoveSeparateControlCenterButton
import com.luckyzyx.luckytool.hook.scopes.systemui.RemoveStatusBarBottomNetworkWarn
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object StatusBarControlCenter : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //控制中心时钟样式
        loadHooker(ControlCenterClockStyle)

        //控制中心日期样式
        loadHooker(ControlCenterDateStyle)

        //自定义通知两侧间距
        if (prefs(ModulePrefs).getBoolean("enable_notification_side_spacing", false)) {
            if (osCode >= 23) loadHooker(EnableNotificationSideSpacing)
        }
        //移除控制中心多用户
        if (prefs(ModulePrefs).getBoolean("remove_control_center_user_switcher", false)) {
            if (osCode < 26) loadHooker(RemoveControlCenterUserSwitcher)
        }
        //控制中心底部网络警告
        loadHooker(RemoveStatusBarBottomNetworkWarn)

        //通知背景透明度
//        loadHooker(CustomNotificationBackgroundTransparency)
        if (osCode in 30..33) loadHooker(NotificationBackgroundBlurAlpha)

        //控制中心背景透明度
        if (osCode < 34) loadHooker(ControlCenterBackgroundTransParency)

        //移除分离式控制中心按钮
        if (osCode >= 34) loadHooker(RemoveSeparateControlCenterButton)

        //启用控制中心进度条百分比显示
        val enableProgressPercent =
            prefs(ModulePrefs).getBoolean(
                "enable_control_center_progress_percent_display",
                false
            )
        if (enableProgressPercent) {
            loadHooker(EnableControlCenterProgressPercentDisplay)
        }

        //移除控制中心运营商
        if (prefs(ModulePrefs).getBoolean("remove_control_center_carriers", false)) {
            loadHooker(RemoveControlCenterCarriers)
        }
    }
}