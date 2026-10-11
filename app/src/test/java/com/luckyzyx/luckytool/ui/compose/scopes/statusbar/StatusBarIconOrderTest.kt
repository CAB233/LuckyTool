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

    /** 「重置」写回的偏好（左区只剩时钟、右区清空）必须等于全新安装时的状态 */
    @Test
    fun `reset writes the same zones as a fresh install`() {
        assertEquals(zoneSplit("", ""), zoneSplit(STATUS_BAR_ICON_ORDER_CLOCK_ITEM, ""))
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

    /** 时钟可以搬到右区：此时左区可以为空，右区里时钟按保存顺序停在其它槽位之间 */
    @Test
    fun `clock can be moved to the right zone`() {
        val (left, right) = zoneSplit("", "clock,battery")
        assertTrue(left.isEmpty())
        assertEquals("clock", right[0])
        assertEquals("battery", right[1])
        // 49 个宿主槽位 + `clock` 伪条目（clock 本身不是宿主槽位）
        assertEquals(StatusBarIconSlots.defaultOrder.size + 1, right.size)
        assertTrue("clock" !in StatusBarIconSlots.defaultOrder)
    }

    /** 两边都写了时钟时以左区为准，右区里的时钟被清掉 */
    @Test
    fun `the clock prefers the left zone when both zones claim it`() {
        val (left, right) = zoneSplit("clock,network_speed", "clock,battery")
        assertEquals(listOf("clock", "network_speed"), left)
        assertTrue("clock" !in right)
        assertEquals("battery", right[0])
    }

    @Test
    fun `unknown entries are dropped from both zones`() {
        val (left, right) = zoneSplit("ghost,clock", "ghost,battery")
        assertEquals(listOf("clock"), left)
        assertEquals("battery", right[0])
        assertTrue("ghost" !in left)
        assertTrue("ghost" !in right)
    }

    /** 松手落位：同区内换位、跨区时区界同步增减（时钟也能跨区，因此左区可以被搬空） */
    @Test
    fun `zone drop reorders inside a zone and carries across the border`() {
        // 左区内下移：clock 与 network_speed 互换，左区项数不变
        val inside = mutableListOf("clock", "network_speed", "battery", "wifi")
        assertEquals(2, zoneDrop(inside, 2, 0, 1))
        assertEquals(listOf("network_speed", "clock", "battery", "wifi"), inside)

        // 左区项搬到右区末尾：区界左移
        val toRight = mutableListOf("clock", "network_speed", "battery", "wifi")
        assertEquals(1, zoneDrop(toRight, 2, 1, 3))
        assertEquals(listOf("clock", "battery", "wifi", "network_speed"), toRight)

        // 右区项搬到左区最前：区界右移
        val toLeft = mutableListOf("clock", "battery", "wifi")
        assertEquals(2, zoneDrop(toLeft, 1, 1, 0))
        assertEquals(listOf("battery", "clock", "wifi"), toLeft)

        // 时钟搬到右区：左区被搬空，自动补回占位条目（占位不算进偏好，也不参与区头计数）
        val clockToRight = mutableListOf("clock", "battery", "wifi")
        assertEquals(1, zoneDrop(clockToRight, 1, 0, 1))
        assertEquals(
            listOf(STATUS_BAR_ICON_ORDER_LEFT_EMPTY_MARKER, "battery", "clock", "wifi"),
            clockToRight,
        )
    }

    /** 左区为空时占位条目占住下标 0：右区第一个位置照常可用，只有拖到占位行才放回左侧 */
    @Test
    fun `zone drop keeps the first right slot reachable when the left zone is empty`() {
        val marker = STATUS_BAR_ICON_ORDER_LEFT_EMPTY_MARKER

        // 拖到占位槽（to = 0）＝放进左侧：占位被顶替后消失
        val onto = mutableListOf(marker, "clock", "battery", "wifi")
        assertEquals(1, zoneDrop(onto, 1, 1, 0))
        assertEquals(listOf("clock", "battery", "wifi"), onto)

        // 拖到右区第一个位置（to = 1）：占位保留，右区照常排序
        val firstRight = mutableListOf(marker, "clock", "battery")
        assertEquals(1, zoneDrop(firstRight, 1, 2, 1))
        assertEquals(listOf(marker, "battery", "clock"), firstRight)

        // 右区第一项原地不动（from = to = 1）：无操作，不会误入左区
        val noOp = mutableListOf(marker, "clock", "battery")
        assertEquals(1, zoneDrop(noOp, 1, 1, 1))
        assertEquals(listOf(marker, "clock", "battery"), noOp)
    }

    @Test
    fun `zone drop ignores a no-op or an invalid target`() {
        val items = mutableListOf("clock", "battery")
        // 原地不动：不改列表，也不触发写回
        assertEquals(1, zoneDrop(items, 1, 0, 0))
        assertEquals(listOf("clock", "battery"), items)
        // 下标越界
        assertEquals(1, zoneDrop(items, 1, 0, 5))
        assertEquals(1, zoneDrop(items, 1, 5, 0))
        assertEquals(listOf("clock", "battery"), items)
        // 空列表
        assertEquals(0, zoneDrop(mutableListOf(), 0, 0, 1))
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

    /**
     * 拖到列表上下边缘自动滚动：远离边缘不滚，越靠边越快，方向由落在哪一侧决定。
     * `visualTop` 是被拖行的窗口 Y，`viewport` 是窗口高度，`edge` 是触发自动滚动的边缘宽度。
     */
    @Test
    fun `auto scroll only kicks in near the viewport edges`() {
        val viewport = 2000f
        val edge = 300f
        val max = 40f
        // 中间：不滚
        assertEquals(0f, autoScrollStep(1000f, viewport, edge, max), 0.001f)
        // 上边缘区内：向上滚（负），越靠上越快
        val upper = autoScrollStep(150f, viewport, edge, max)
        assertTrue("上边缘应向上滚，实际 $upper", upper < 0f)
        assertTrue("越靠上越快", autoScrollStep(90f, viewport, edge, max) < upper)
        // 下边缘区内：向下滚（正），越靠下越快
        val lower = autoScrollStep(1800f, viewport, edge, max)
        assertTrue("下边缘应向下滚，实际 $lower", lower > 0f)
        assertTrue("越靠下越快", autoScrollStep(1900f, viewport, edge, max) > lower)
    }

    @Test
    fun `auto scroll speed is clamped and invalid geometry never scrolls`() {
        // 贴住边缘 = 满速；越界（手指拖出屏幕）也只维持满速
        assertEquals(-40f, autoScrollStep(0f, 2000f, 300f, 40f), 0.001f)
        assertEquals(-40f, autoScrollStep(-500f, 2000f, 300f, 40f), 0.001f)
        assertEquals(40f, autoScrollStep(2000f, 2000f, 300f, 40f), 0.001f)
        // 边缘宽度/速度非法时不滚（窗口高度拿不到时调用点也会直接跳过）
        assertEquals(0f, autoScrollStep(10f, 2000f, 0f, 40f), 0.001f)
        assertEquals(0f, autoScrollStep(10f, 2000f, 300f, 0f), 0.001f)
    }
}
