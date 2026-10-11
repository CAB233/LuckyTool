package com.luckyzyx.luckytool.ui.compose.scopes.statusbar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun `saved order wins and the rest keeps the host order`() {
        val merged = parseSlots("battery,logging")
        assertEquals(49, merged.size)
        assertEquals("battery", merged[0])
        assertEquals("logging", merged[1])
        assertEquals("hotspot", merged[2])
    }

    @Test
    fun `unknown saved entries are dropped`() {
        val merged = parseSlots("ghost,battery,ghost")
        assertEquals(49, merged.size)
        assertEquals("battery", merged[0])
        assertTrue("ghost" !in merged)
    }

    @Test
    fun `blank preference keeps the host default order`() {
        assertEquals(StatusBarIconSlots.defaultOrder, parseSlots(""))
        assertEquals(StatusBarIconSlots.defaultOrder, parseSlots("  ,  "))
    }
}
