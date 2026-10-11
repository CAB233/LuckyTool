package com.luckyzyx.luckytool.ui.compose.scopes.statusbar

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/** 状态栏图标排序偏好键（逗号分隔的 slot 名，空 = 宿主默认顺序） */
const val STATUS_BAR_ICON_ORDER_KEY = "statusbar_icon_order"

/** 状态栏图标排序总开关（关闭时 hooker 不装载，改动需重启作用域） */
const val STATUS_BAR_ICON_ORDER_ENABLED_KEY = "statusbar_icon_order_enabled"

/** 时钟位置开关：开启后时钟排到通知图标右侧（默认左侧），同一容器内换序，无需重启 */
const val STATUS_BAR_ICON_ORDER_CLOCK_AFTER_KEY = "statusbar_icon_order_clock_after"

/**
 * 状态栏图标槽位表（宿主 `config_statusBarIcons` 的顺序与名字）。
 *
 * 数据来源：ColorOS SystemUI 17 的 `res/values/arrays.xml` + `res/values/strings.xml`
 * （`status_bar_xxx` 字符串的实际值就是 `StatusBarIconList.Slot.mName`，例如
 * `airplane_slot` → `airplane`、`oem_satellite` → `satellite`）。
 */
object StatusBarIconSlots {

    /** 宿主默认顺序；也是用户「重置」后的顺序 */
    val defaultOrder: List<String> = listOf(
        "logging",
        "hotspot",
        "data_saver",
        "cast",
        "connected_display",
        "vpn",
        "nfc",
        "headset",
        "high_performance",
        "alarm_clock",
        "volume",
        "bluetooth",
        "sos",
        "location",
        "microphone",
        "camera",
        "rotate",
        "ime",
        "sync_failing",
        "sync_active",
        "tty",
        "speakerphone",
        "cdma_eri",
        "data_connection",
        "phone_evdo_signal",
        "phone_signal",
        "secure",
        "managed_profile",
        "mute",
        "Ethernet",
        "network_speed",
        "vowifi",
        "volte",
        "zen",
        "satellite",
        "scenario_assistant",
        "stacked_mobile",
        "mobile",
        "nosim_all",
        "nosim_one",
        "nosim_two",
        "wifi",
        "dynamic_info",
        "secure_pay",
        "privacy_call",
        "stealth_mode",
        "airplane",
        "oplus_satellite",
        "battery",
    )

    /** 槽位的展示名资源；未收录的槽位（未来版本新增）返回 null，调用点回落成 slot 名 */
    @StringRes
    fun labelRes(slot: String): Int? = when (slot) {
        "logging" -> R.string.status_bar_slot_logging
        "hotspot" -> R.string.status_bar_slot_hotspot
        "data_saver" -> R.string.status_bar_slot_data_saver
        "cast" -> R.string.status_bar_slot_cast
        "connected_display" -> R.string.status_bar_slot_connected_display
        "vpn" -> R.string.status_bar_slot_vpn
        "nfc" -> R.string.status_bar_slot_nfc
        "headset" -> R.string.status_bar_slot_headset
        "high_performance" -> R.string.status_bar_slot_high_performance
        "alarm_clock" -> R.string.status_bar_slot_alarm_clock
        "volume" -> R.string.status_bar_slot_volume
        "bluetooth" -> R.string.status_bar_slot_bluetooth
        "sos" -> R.string.status_bar_slot_sos
        "location" -> R.string.status_bar_slot_location
        "microphone" -> R.string.status_bar_slot_microphone
        "camera" -> R.string.status_bar_slot_camera
        "rotate" -> R.string.status_bar_slot_rotate
        "ime" -> R.string.status_bar_slot_ime
        "sync_failing" -> R.string.status_bar_slot_sync_failing
        "sync_active" -> R.string.status_bar_slot_sync_active
        "tty" -> R.string.status_bar_slot_tty
        "speakerphone" -> R.string.status_bar_slot_speakerphone
        "cdma_eri" -> R.string.status_bar_slot_cdma_eri
        "data_connection" -> R.string.status_bar_slot_data_connection
        "phone_evdo_signal" -> R.string.status_bar_slot_phone_evdo_signal
        "phone_signal" -> R.string.status_bar_slot_phone_signal
        "secure" -> R.string.status_bar_slot_secure
        "managed_profile" -> R.string.status_bar_slot_managed_profile
        "mute" -> R.string.status_bar_slot_mute
        "Ethernet" -> R.string.status_bar_slot_ethernet
        "network_speed" -> R.string.status_bar_slot_network_speed
        "vowifi" -> R.string.status_bar_slot_vowifi
        "volte" -> R.string.status_bar_slot_volte
        "zen" -> R.string.status_bar_slot_zen
        "satellite" -> R.string.status_bar_slot_satellite
        "scenario_assistant" -> R.string.status_bar_slot_scenario_assistant
        "stacked_mobile" -> R.string.status_bar_slot_stacked_mobile
        "mobile" -> R.string.status_bar_slot_mobile
        "nosim_all" -> R.string.status_bar_slot_nosim_all
        "nosim_one" -> R.string.status_bar_slot_nosim_one
        "nosim_two" -> R.string.status_bar_slot_nosim_two
        "wifi" -> R.string.status_bar_slot_wifi
        "dynamic_info" -> R.string.status_bar_slot_dynamic_info
        "secure_pay" -> R.string.status_bar_slot_secure_pay
        "privacy_call" -> R.string.status_bar_slot_privacy_call
        "stealth_mode" -> R.string.status_bar_slot_stealth_mode
        "airplane" -> R.string.status_bar_slot_airplane
        "oplus_satellite" -> R.string.status_bar_slot_oplus_satellite
        "battery" -> R.string.status_bar_slot_battery
        else -> null
    }

    fun label(context: Context, slot: String): String =
        labelRes(slot)?.let { context.getString(it) } ?: slot

    /** 是否被本版本收录（未收录的槽位不参与排序，保持宿主默认顺序） */
    fun isKnown(slot: String): Boolean = slot in defaultOrder
}

/**
 * 状态栏图标位置排序弹层。
 *
 * 列出全部已知槽位（顺序 = 当前生效顺序），用 ↑/↓ 调整，应用后写入偏好并由 dataChannel 同步给宿主；
 * 「重置」只改本地列表，确认后才写回宿主默认顺序（偏好留空 = 宿主默认顺序）。
 */
@Composable
fun StatusBarIconOrderDialog(
    savedOrder: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val items = remember(savedOrder) { mutableStateListOf(*parseSlots(savedOrder).toTypedArray()) }

    val reset: () -> Unit = {
        items.clear()
        items.addAll(StatusBarIconSlots.defaultOrder)
    }
    val move: (Int, Int) -> Unit = { from, to ->
        if (from in items.indices && to in items.indices) {
            items.add(to, items.removeAt(from))
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixStatusBarIconOrderDialog(
            items = items,
            context = context,
            move = move,
            onReset = reset,
            onDismiss = onDismiss,
            onApply = onApply,
        )

        UiMode.Material -> MaterialStatusBarIconOrderDialog(
            items = items,
            context = context,
            move = move,
            onReset = reset,
            onDismiss = onDismiss,
            onApply = onApply,
        )
    }
}

/** 偏好值 → 列表：先按保存顺序排已知槽位，再把本版本新增的槽位按默认顺序补在后面 */
internal fun parseSlots(savedOrder: String): List<String> {
    val known = StatusBarIconSlots.defaultOrder
    val saved = savedOrder.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in known }
        .distinct()
    return saved + known.filterNot { it in saved }
}

@Composable
private fun StatusBarIconOrderRow(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpLabel: String,
    moveDownLabel: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixStatusBarIconOrderRow(
            label = label,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            moveUpLabel = moveUpLabel,
            moveDownLabel = moveDownLabel,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )

        UiMode.Material -> MaterialStatusBarIconOrderRow(
            label = label,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            moveUpLabel = moveUpLabel,
            moveDownLabel = moveDownLabel,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }
}

/** Miuix 线：OverlayDialog（根部 popup host 承载）+ 库内行件，内容自收敛高度（同 AppPickerDialog） */
@Composable
private fun MiuixStatusBarIconOrderDialog(
    items: SnapshotStateList<String>,
    context: Context,
    move: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.statusbar_icon_order),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(items, key = { _, slot -> slot }) { index, slot ->
                        MiuixStatusBarIconOrderRow(
                            label = StatusBarIconSlots.label(context, slot),
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            moveUpLabel = stringResource(R.string.statusbar_icon_order_move_up),
                            moveDownLabel = stringResource(R.string.statusbar_icon_order_move_down),
                            onMoveUp = { move(index, index - 1) },
                            onMoveDown = { move(index, index + 1) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MiuixTextButton(
                    text = stringResource(R.string.statusbar_icon_order_reset),
                    onClick = onReset,
                )
                Row {
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(android.R.string.ok),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { onApply(items.joinToString(",")) },
                    )
                }
            }
        }
    }
}

/** Material 线：M3 AlertDialog，列表高度上限 420.dp（同 AppPickerDialog） */
@Composable
private fun MaterialStatusBarIconOrderDialog(
    items: SnapshotStateList<String>,
    context: Context,
    move: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.statusbar_icon_order)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                ) {
                    itemsIndexed(items, key = { _, slot -> slot }) { index, slot ->
                        MaterialStatusBarIconOrderRow(
                            label = StatusBarIconSlots.label(context, slot),
                            canMoveUp = index > 0,
                            canMoveDown = index < items.lastIndex,
                            moveUpLabel = stringResource(R.string.statusbar_icon_order_move_up),
                            moveDownLabel = stringResource(R.string.statusbar_icon_order_move_down),
                            onMoveUp = { move(index, index - 1) },
                            onMoveDown = { move(index, index + 1) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(items.joinToString(",")) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.statusbar_icon_order_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun MiuixStatusBarIconOrderRow(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpLabel: String,
    moveDownLabel: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(text = label, modifier = Modifier.weight(1f))
        MiuixIconButton(onClick = onMoveUp, enabled = canMoveUp) {
            MiuixIcon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = moveUpLabel,
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
        MiuixIconButton(onClick = onMoveDown, enabled = canMoveDown) {
            MiuixIcon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = moveDownLabel,
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun MaterialStatusBarIconOrderRow(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpLabel: String,
    moveDownLabel: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = moveUpLabel)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = moveDownLabel)
        }
    }
}
