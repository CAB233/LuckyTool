package com.luckyzyx.luckytool.ui.compose.scopes.statusbar

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.luckyzyx.luckytool.ui.compose.components.PrefScopeBuilder
import com.luckyzyx.luckytool.ui.compose.scopes.ScopePageRegistry
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.PrefState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 状态栏图标排序的槽位表与排序回落逻辑。
 *
 * 槽位名/顺序取自宿主 ColorOS SystemUI 17 的 `config_statusBarIcons`（arrays.xml 49 项，
 * 每项经 strings.xml 解析成 `StatusBarIconList.Slot.mName`）；这里锁住数量、首尾、易错项
 * （`ethernet_slot` → `Ethernet` 带大写、`oem_satellite` → `satellite`、`satellite_slot` → `oplus_satellite`）
 * 与「每个槽位都有非空展示名」，避免以后加槽位时漏翻译。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatusBarIconOrderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `host slot table keeps 49 unique slots`() {
        val order = StatusBarIconSlots.defaultOrder
        assertEquals(49, order.size)
        assertEquals(order.size, order.toSet().size)
    }

    @Test
    fun `host slot table keeps the config_statusBarIcons sequence`() {
        assertEquals("logging", StatusBarIconSlots.defaultOrder.first())
        assertEquals("battery", StatusBarIconSlots.defaultOrder.last())
        assertEquals("Ethernet", StatusBarIconSlots.defaultOrder[29])
        assertEquals("satellite", StatusBarIconSlots.defaultOrder[34])
        assertEquals("airplane", StatusBarIconSlots.defaultOrder[46])
        assertEquals("oplus_satellite", StatusBarIconSlots.defaultOrder[47])
    }

    @Test
    fun `every host slot has a non blank label`() {
        StatusBarIconSlots.defaultOrder.forEach { slot ->
            val res = StatusBarIconSlots.labelRes(slot)
            assertNotNull("missing label resource for $slot", res)
            assertTrue("blank label for $slot", StatusBarIconSlots.label(context, slot).isNotBlank())
        }
    }

    @Test
    fun `unknown slot falls back to its raw name`() {
        assertNull(StatusBarIconSlots.labelRes("unknown_slot"))
        assertEquals("unknown_slot", StatusBarIconSlots.label(context, "unknown_slot"))
        assertTrue(!StatusBarIconSlots.isKnown("unknown_slot"))
    }

    @Test
    fun `blank preference keeps the clock on the left and the host order on the right`() {
        val (left, right) = zoneSplit("", "")
        assertEquals(listOf("clock"), left)
        assertEquals(StatusBarIconSlots.defaultOrder, right)
    }

    @Test
    fun `saved zones are restored in place and never overlap`() {
        val (left, right) = zoneSplit("network_speed,clock,mobile", "battery,vowifi")
        assertEquals(listOf("network_speed", "clock", "mobile"), left)
        assertEquals("battery", right[0])
        assertEquals("vowifi", right[1])
        // 49 个宿主槽位里只有 network_speed / mobile 被搬到左区；时钟是伪条目，不占宿主槽位
        assertEquals(47, right.size)
        assertTrue("network_speed" !in right)
        assertTrue("mobile" !in right)
    }

    @Test
    fun `clock is prepended when the saved left zone lost it`() {
        val (left, _) = zoneSplit("network_speed", "")
        assertEquals(listOf("clock", "network_speed"), left)
    }

    @Test
    fun `unknown entries are dropped from both zones`() {
        val (left, right) = zoneSplit("ghost,clock", "ghost,battery")
        assertEquals(listOf("clock"), left)
        assertEquals("battery", right[0])
        assertTrue("ghost" !in left)
        assertTrue("ghost" !in right)
    }

    @Test
    fun `zone move swaps inside a zone and carries across the border`() {
        val items = mutableListOf("clock", "network_speed", "battery", "wifi")
        // 左区内下移：相邻交换
        assertEquals(ZoneMove(2, true), zoneMove(items, 2, 0, down = true))
        assertEquals(listOf("network_speed", "clock", "battery", "wifi"), items)
        // 右区首项上移越过区界 → 变成左区末项（没有交换）
        assertEquals(ZoneMove(3, true), zoneMove(items, 2, 2, down = false))
        assertEquals(listOf("network_speed", "clock", "battery", "wifi"), items)
        // 左区末项下移越过区界 → 变成右区首项
        assertEquals(ZoneMove(2, true), zoneMove(items, 3, 2, down = true))
    }

    @Test
    fun `zone move keeps the clock on the left and stops at both ends`() {
        // 时钟不能搬去右区
        assertEquals(ZoneMove(2, false), zoneMove(mutableListOf("network_speed", "clock"), 2, 1, down = true))
        // 左区只剩时钟时不能再让出左区
        assertEquals(ZoneMove(1, false), zoneMove(mutableListOf("clock"), 1, 0, down = true))
        // 两端边界与非法下标
        assertEquals(ZoneMove(1, false), zoneMove(mutableListOf("clock", "wifi"), 1, 1, down = true))
        assertEquals(ZoneMove(1, false), zoneMove(mutableListOf("clock", "wifi"), 1, 0, down = false))
        assertEquals(ZoneMove(0, false), zoneMove(mutableListOf(), 0, 0, down = true))
    }

    /**
     * 排序列表独立成页：功能页只留入口行，排序页自己带开关与列表。
     * `com.android.systemui` 是排序页的唯一作用域，Robolectric 下未安装时入口行会被可达性判断拦掉，
     * 因此先装一个包信息（与 `ScopePageReachabilityTest` 同一手法）。
     */
    @Test
    fun `the icon page links to the dedicated order page`() {
        Shadows.shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { packageName = "com.android.systemui" },
        )

        val spec = ScopePageRegistry[StatusBarIconOrderPage.PAGE_KEY]
        assertNotNull("排序页应已注册", spec)
        assertEquals("statusbar_icon_order", StatusBarIconOrderPage.PAGE_KEY)
        assertTrue(
            "排序页必须有可见条目（开关无条件发射），否则入口行会被误隐藏",
            ScopePageRegistry.hasVisibleContent(context, spec!!),
        )

        val raw = context.getSharedPreferences(ModulePrefs, Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        val builder = PrefScopeBuilder(PrefState.ofProvider { raw })
        builder.context = context
        builder.beginBuild()
        StatusBarIconPage.spec.content(builder)
        val keys = builder.entries.mapNotNull { it.indexKey }.toSet()
        assertTrue(
            "图标页必须发射排序页入口行，实际索引键：$keys",
            "page:${StatusBarIconOrderPage.PAGE_KEY}" in keys,
        )
        // 排序的行不再长在功能页里：旧的排序行键与左区键都不该出现在图标页索引中
        assertTrue("排序列不应再留在图标页：$keys", STATUS_BAR_ICON_ORDER_KEY !in keys)
        assertTrue("排序列不应再留在图标页：$keys", STATUS_BAR_ICON_ORDER_LEFT_KEY !in keys)
    }
}
