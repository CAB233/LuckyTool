package com.luckyzyx.luckytool.ui.compose.scopes.statusbar

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixHorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 右区顺序偏好键（宿主 `statusIcons` 容器内的图标顺序，逗号分隔槽位名；空 = 宿主默认顺序） */
const val STATUS_BAR_ICON_ORDER_KEY = "statusbar_icon_order"

/** 左区顺序偏好键（被搬到左侧容器的图标顺序，逗号分隔槽位名，总是含 [STATUS_BAR_ICON_ORDER_CLOCK_ITEM]） */
const val STATUS_BAR_ICON_ORDER_LEFT_KEY = "statusbar_icon_order_left"

/** 总开关：关闭时 hooker 不装载，改动需重启作用域（开关本身不做实时同步） */
const val STATUS_BAR_ICON_ORDER_ENABLED_KEY = "statusbar_icon_order_enabled"

/**
 * 时钟伪条目。
 *
 * 宿主的时钟不是 `StatusBarIconList` 槽位，而是左侧容器里的 `clock_for_fake` 子 view，
 * 因此左区列表用它占一行：它的邻居顺序同样由本功能的左区顺序表达。
 */
const val STATUS_BAR_ICON_ORDER_CLOCK_ITEM = "clock"

/**
 * 排序行高。
 *
 * 拖拽时用位移换算「跨过几行」，因此必须与行的实际高度一致（[OrderItemRowHeight]）。
 */
private val OrderItemHeight = 64.dp

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
        STATUS_BAR_ICON_ORDER_CLOCK_ITEM -> R.string.statusbar_icon_order_clock
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
 * 偏好值 → (左区列表, 右区列表)。
 *
 * - 左区：保存顺序里的合法项（[STATUS_BAR_ICON_ORDER_CLOCK_ITEM] 或已收录槽位），去重；时钟缺失时补到最前
 *   （时钟只能待在左区，缺失时也不能落进右区）。
 * - 右区：保存顺序里的合法项（排除左区项）+ 其余已收录槽位按宿主默认顺序补齐，
 *   因此本版本新增的槽位默认仍排在右侧尾部。
 * - 未收录的槽位名一律丢弃（宿主里本来就不存在，或未来版本才有）。
 */
internal fun zoneSplit(leftRaw: String, rightRaw: String): Pair<List<String>, List<String>> {
    val known = StatusBarIconSlots.defaultOrder
    val clock = STATUS_BAR_ICON_ORDER_CLOCK_ITEM
    val leftSaved = leftRaw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && (it == clock || it in known) }
        .distinct()
    val left = if (clock in leftSaved) leftSaved else listOf(clock) + leftSaved
    val rightSaved = rightRaw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in known && it !in left }
        .distinct()
    val right = rightSaved + known.filterNot { it in left || it in rightSaved }
    return left to right
}

/** 一次拖动位移的结果（`leftCount` 会因跨区搬运而改变） */
internal data class ZoneMove(val leftCount: Int, val moved: Boolean)

/**
 * 一次相邻位移：同区内交换，或跨过区界搬运到另一个区。
 *
 * [items] = 左区 + 右区 的扁平列表，[leftCount] = 左区项数，[index] = 被拖动项在扁平列表里的下标，
 * [down] = 向下移一行。夹在中间的三条边界规则：
 * - 左区至少要留一项（[STATUS_BAR_ICON_ORDER_CLOCK_ITEM] 就在左区，时钟不能搬去右区）；
 * - 从右区往上越过区界 = 变成左区末项（`leftCount + 1`）；
 * - 从左区往下越过区界 = 变成右区首项（`leftCount - 1`）。
 */
internal fun zoneMove(items: MutableList<String>, leftCount: Int, index: Int, down: Boolean): ZoneMove {
    if (index !in items.indices) return ZoneMove(leftCount, false)
    if (down) {
        if (index < leftCount - 1) {
            items[index] = items[index + 1].also { items[index + 1] = items[index] }
            return ZoneMove(leftCount, true)
        }
        if (index == leftCount - 1) {
            // 左区末项：要么留在左区（至少给时钟留一项），要么整体交给右区
            if (leftCount <= 1) return ZoneMove(leftCount, false)
            if (items[index] == STATUS_BAR_ICON_ORDER_CLOCK_ITEM) return ZoneMove(leftCount, false)
            return ZoneMove(leftCount - 1, true)
        }
        if (index < items.lastIndex) {
            items[index] = items[index + 1].also { items[index + 1] = items[index] }
            return ZoneMove(leftCount, true)
        }
        return ZoneMove(leftCount, false)
    }
    if (index > leftCount) {
        items[index] = items[index - 1].also { items[index - 1] = items[index] }
        return ZoneMove(leftCount, true)
    }
    if (index == leftCount) {
        // 右区首项：越过区界成为左区末项
        if (items.size <= leftCount) return ZoneMove(leftCount, false)
        return ZoneMove(leftCount + 1, true)
    }
    if (index > 0) {
        items[index] = items[index - 1].also { items[index - 1] = items[index] }
        return ZoneMove(leftCount, true)
    }
    return ZoneMove(leftCount, false)
}

/** 开关卡片里的长说明：解释长按手柄拖动与跨区搬运的规则（对齐设计稿） */
@Composable
fun StatusBarIconOrderHint() {
    Column(modifier = Modifier.fillMaxWidth()) {
        ZoneDivider(modifier = Modifier.padding(horizontal = 16.dp))
        if (LocalUiMode.current == UiMode.Miuix) {
            MiuixText(
                text = stringResource(R.string.statusbar_icon_order_drag_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
        } else {
            Text(
                text = stringResource(R.string.statusbar_icon_order_drag_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 两区域排序列表（左区 / 右区，各自带序号）。
 *
 * 长按行（手柄图标所在处）后上下拖动：位移累计满一行就交换一次，越过区界即在两个区之间搬运；
 * 松手时把两个区一起写回偏好，宿主侧经 dataChannel 实时生效。
 * 拖拽期间的列表是本地状态（[items]），偏好值变化（含本次写回）会重建 [session] 让本次编辑作废，
 * 避免本地状态与已生效顺序分叉。
 */
@Composable
fun StatusBarIconOrderZones(
    leftRaw: String,
    rightRaw: String,
    onApply: (left: String, right: String) -> Unit,
) {
    val context = LocalContext.current
    val session = remember(leftRaw, rightRaw) { Any() }
    val initial = remember(session) { zoneSplit(leftRaw, rightRaw) }
    val items = remember(session) {
        mutableStateListOf<String>().apply {
            addAll(initial.first)
            addAll(initial.second)
        }
    }
    var leftCount by remember(session) { mutableIntStateOf(initial.first.size) }
    var draggingKey by remember(session) { mutableStateOf<String?>(null) }
    val step = with(LocalDensity.current) { OrderItemHeight.toPx() }

    // 长按手柄后上下拖动：位移累计满一行就交换一次（跨过区界即在两区之间搬运），松手时写回偏好
    val dragModifierFor: (String) -> Modifier = { key ->
        Modifier.pointerInput(key, session) {
            var pending = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    draggingKey = key
                    pending = 0f
                },
                onDragCancel = { draggingKey = null },
                onDragEnd = {
                    draggingKey = null
                    onApply(
                        items.take(leftCount).joinToString(","),
                        items.drop(leftCount).joinToString(","),
                    )
                },
                onDrag = { change, amount ->
                    change.consume()
                    pending += amount.y
                    while (pending >= step) {
                        val move = zoneMove(items, leftCount, items.indexOf(key), down = true)
                        if (!move.moved) {
                            pending = 0f
                            break
                        }
                        leftCount = move.leftCount
                        pending -= step
                    }
                    while (pending <= -step) {
                        val move = zoneMove(items, leftCount, items.indexOf(key), down = false)
                        if (!move.moved) {
                            pending = 0f
                            break
                        }
                        leftCount = move.leftCount
                        pending += step
                    }
                },
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ZoneHeader(
            text = stringResource(R.string.statusbar_icon_order_zone_left, leftCount),
            topPadding = 12.dp,
        )
        items.take(leftCount).forEachIndexed { index, key ->
            OrderItemRow(
                number = index + 1,
                label = StatusBarIconSlots.label(context, key),
                slot = key,
                dragging = draggingKey == key,
                dragModifier = dragModifierFor(key),
            )
        }
        ZoneDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ZoneHeader(
            text = stringResource(R.string.statusbar_icon_order_zone_right, items.size - leftCount),
            topPadding = 8.dp,
        )
        items.drop(leftCount).forEachIndexed { index, key ->
            OrderItemRow(
                number = index + 1,
                label = StatusBarIconSlots.label(context, key),
                slot = key,
                dragging = draggingKey == key,
                dragModifier = dragModifierFor(key),
            )
        }
    }
}

/** 区头：「左侧区域(共 N 项)」（N 随拖拽实时变化） */
@Composable
private fun ZoneHeader(text: String, topPadding: Dp) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 4.dp)
    if (LocalUiMode.current == UiMode.Miuix) {
        MiuixText(
            text = text,
            modifier = modifier,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote1,
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 区界分隔线（列表内的分组线，两侧留 16dp 内缩） */
@Composable
private fun ZoneDivider(modifier: Modifier = Modifier) {
    if (LocalUiMode.current == UiMode.Miuix) {
        MiuixHorizontalDivider(modifier)
    } else {
        HorizontalDivider(modifier)
    }
}

/** 一行条目：序号 + 展示名 + 槽位名 + 右侧拖拽手柄；高度固定，供拖拽换算行数 */
@Composable
private fun OrderItemRow(
    number: Int,
    label: String,
    slot: String,
    dragging: Boolean,
    dragModifier: Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixOrderItemRow(number, label, slot, dragging, dragModifier)
        UiMode.Material -> MaterialOrderItemRow(number, label, slot, dragging, dragModifier)
    }
}

@Composable
private fun MiuixOrderItemRow(
    number: Int,
    label: String,
    slot: String,
    dragging: Boolean,
    dragModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OrderItemHeight)
            .then(
                if (dragging) {
                    Modifier.background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = number.toString(),
            modifier = Modifier.width(28.dp),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.body2,
        )
        Column(modifier = Modifier.weight(1f)) {
            MiuixText(text = label, style = MiuixTheme.textStyles.main)
            MiuixText(
                text = slot,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        MiuixIcon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun MaterialOrderItemRow(
    number: Int,
    label: String,
    slot: String,
    dragging: Boolean,
    dragModifier: Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OrderItemHeight)
            .then(
                if (dragging) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp)
            .then(dragModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = slot,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
