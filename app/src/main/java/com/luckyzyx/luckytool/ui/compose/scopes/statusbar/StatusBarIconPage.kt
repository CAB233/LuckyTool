package com.luckyzyx.luckytool.ui.compose.scopes.statusbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.compose.components.PrefRow
import com.luckyzyx.luckytool.ui.compose.components.PrefRowInset
import com.luckyzyx.luckytool.ui.compose.scopes.ScopePageSpec
import com.luckyzyx.luckytool.utils.A13
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.getOSVersionCode

/**
 * 状态栏图标页（旧 ui.fragment.scopes.statusbar.StatusBarIcon 的 Compose 等价物）。
 * 逐项对齐：键、默认值、条件可见性（osCode = getOSVersionCode、SDK = Android API）、
 * notify（sendPrefsValue("com.android.systemui")）。
 */
object StatusBarIconPage {

    val spec = ScopePageSpec(
        pageKey = "statusbar_icon",
        prefsName = ModulePrefs,
        packName = "com.android.systemui",
        scopes = arrayOf("com.android.systemui"),
        restartEnabled = true,
    ) {
        val c = requireNotNull(context) { "ScopeScreen 未注入 Context" }
        val osCode = getOSVersionCode
        // 图标位置排序
        category(c.getString(R.string.statusbar_icon_order))
        // 总开关：hooker 只在开启时装载（onHook 里判断），因此关闭/开启都需要重启作用域
        switch(
            key = STATUS_BAR_ICON_ORDER_ENABLED_KEY,
            title = c.getString(R.string.statusbar_icon_order_enable),
            summary = c.getString(R.string.statusbar_icon_order_enable_summary),
            onChange = { restart?.invoke() },
        )
        if (state.getBoolean(STATUS_BAR_ICON_ORDER_ENABLED_KEY, false)) {
            custom(
                key = STATUS_BAR_ICON_ORDER_KEY,
                title = c.getString(R.string.statusbar_icon_order),
            ) {
                val saved by state.stringFlow(STATUS_BAR_ICON_ORDER_KEY, "")
                    .collectAsStateWithLifecycle()
                var showDialog by remember { mutableStateOf(false) }
                PrefRowInset {
                    PrefRow(
                        title = stringResource(R.string.statusbar_icon_order),
                        summary = stringResource(R.string.statusbar_icon_order_summary),
                        onClick = { showDialog = true },
                    )
                }
                if (showDialog) {
                    StatusBarIconOrderDialog(
                        savedOrder = saved,
                        onDismiss = { showDialog = false },
                        onApply = { value ->
                            state.set(STATUS_BAR_ICON_ORDER_KEY, value)
                            sendValue(STATUS_BAR_ICON_ORDER_KEY, value)
                            showDialog = false
                        },
                    )
                }
            }
        }
        // WIFI
        category(c.getString(R.string.StatusBarWIFIIcon))
        switch(
            key = "remove_wifi_data_inout",
            title = c.getString(R.string.remove_wifi_data_inout),
        )
        if (osCode >= 34) {
            switch(
                key = "force_display_wifi_standard",
                title = c.getString(R.string.force_display_wifi_standard),
            )
        }
        // 移动数据
        category(c.getString(R.string.StatusBarMobileDataIcon))
        switch(
            key = "remove_mobile_data_inout",
            title = c.getString(R.string.remove_mobile_data_inout),
        )
        switch(
            key = "remove_mobile_data_type",
            title = c.getString(R.string.remove_mobile_data_type),
        )
        switch(
            key = "hide_non_network_card_icon",
            title = c.getString(R.string.hide_non_network_card_icon),
            notify = true,
        )
        if (osCode < 34) {
            switch(
                key = "hide_inactive_signal_labels_gen2x2",
                title = c.getString(R.string.hide_inactive_signal_labels_gen2x2),
            )
        }
        switch(
            key = "hide_nosim_noservice",
            title = c.getString(R.string.hide_nosim_noservice),
            notify = true,
        )
        // 蓝牙
        category(c.getString(R.string.StatusBarBluetoothIcon))
        switch(
            key = "hide_icon_when_bluetooth_not_connected",
            title = c.getString(R.string.hide_icon_when_bluetooth_not_connected),
            notify = true,
        )
        // 其他
        category(c.getString(R.string.StatusBarOtherIcon))
        switch(
            key = "remove_high_performance_mode_icon",
            title = c.getString(R.string.remove_high_performance_mode_icon),
        )
        switch(
            key = "remove_statusbar_securepayment_icon",
            title = c.getString(R.string.remove_statusbar_securepayment_icon),
        )
        switch(
            key = "remove_green_dot_privacy_prompt",
            title = c.getString(R.string.remove_green_dot_privacy_prompt),
        )
        switch(
            key = "remove_system_prompt_icon",
            title = c.getString(R.string.remove_system_prompt_icon),
            summary = c.getString(R.string.remove_system_prompt_icon_summary),
        )
        // 旧代码连续两次 isVisible，最后一次生效：osCode in 30..33
        if (osCode in 30..33) {
            slider(
                key = "custom_fluid_cloud_icon_background_transparency",
                title = c.getString(R.string.custom_fluid_cloud_icon_background_transparency),
                valueRange = -1..10,
                default = -1,
                notify = true,
            )
        }
        // 图标状态
        if (SDK <= A13) {
            category(c.getString(R.string.StatusBarSmallIconStatus))
            switch(
                key = "status_bar_icon_vertical_center",
                title = c.getString(R.string.status_bar_icon_vertical_center),
            )
        }
    }
}
