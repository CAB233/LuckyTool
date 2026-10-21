package com.luckyzyx.luckytool.ui.compose

import android.app.Application
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import com.luckyzyx.luckytool.ui.compose.components.MainPageActivationEffect
import com.luckyzyx.luckytool.ui.compose.components.material.MaterialPageScaffold
import com.luckyzyx.luckytool.ui.shell.LocalEnableFloatingBottomBar
import com.luckyzyx.luckytool.ui.shell.LocalEnableFloatingBottomBarBlur
import com.luckyzyx.luckytool.ui.shell.LocalPagerInterceptionMode
import com.luckyzyx.luckytool.ui.shell.PagerModeCrossAxis
import com.luckyzyx.luckytool.ui.shell.PagerModeIosLike
import com.luckyzyx.luckytool.ui.shell.PagerModeNative
import com.luckyzyx.luckytool.ui.theme.AppSettings
import com.luckyzyx.luckytool.ui.theme.ColorMode
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.LuckyTheme
import com.luckyzyx.luckytool.ui.theme.UiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.utils.overScrollVertical

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "zh-rCN-w411dp-h880dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainPagerTest {
    @Test
    fun `all bottom bars coordinate rapid navigation without an obsolete target winning`() {
        for ((mode, floating) in listOf(UiMode.Material to false, UiMode.Miuix to false, UiMode.Miuix to true)) {
            PagerHost(mode, floating, glass = floating).use { h ->
                assertEquals(2, h.state.pagerState.settledPage)
                h.ui.clickText("设置")
                assertEquals(4, h.state.selectedPage)
                h.ui.advance(80)
                h.ui.clickText("其他")
                assertEquals(0, h.state.selectedPage)
                h.ui.advance(80)
                h.ui.clickText("功能")
                h.ui.advance()
                h.assertSettled(1)
                h.ui.clickText("功能")
                h.ui.advance()
                h.assertSettled(1)
                h.ui.snapshot("${mode.name.lowercase()}-${if (floating) "floating" else "bottom"}")
            }
        }
    }

    @Test
    fun `dragging takes over an in-flight navigation and a cancelled short drag restores the page`() {
        PagerHost().use { h ->
            h.state.animateToPage(4)
            h.ui.advance(80)
            h.ui.drag(180f, 800f, 740f, 800f)
            assertTrue("Gesture must interrupt the old destination", h.state.pagerState.settledPage < 4)
            h.assertSettled(h.state.pagerState.settledPage)
            val before = h.state.pagerState.settledPage
            h.ui.drag(600f, 800f, 520f, 800f, cancel = true)
            h.assertSettled(before)
        }
    }

    @Test
    @Config(sdk = [35])
    fun `liquid glass bottom bar shares the same pager navigation`() {
        PagerHost(UiMode.Miuix, floating = true, glass = true).use { h ->
            h.ui.clickText("其他")
            h.ui.advance()
            h.assertSettled(0)
            h.ui.drag(700f, 800f, 120f, 800f)
            h.assertSettled(1)
            h.ui.clickText("设置")
            h.ui.advance()
            h.assertSettled(4)
        }
    }

    @Test
    fun `all gesture modes allow resting horizontal paging and leave vertical scroll to the list`() {
        for (mode in listOf(PagerModeNative, PagerModeCrossAxis, PagerModeIosLike)) {
            PagerHost().use { h ->
                h.gestureMode = mode
                h.ui.advance()
                h.ui.drag(410f, 300f, 410f, 300f)
                assertEquals("Paging must not consume row taps", 1, h.clicks[MainTab.Home])
                h.ui.drag(410f, 1300f, 410f, 450f)
                h.assertSettled(2)
                assertTrue(h.lists.getValue(MainTab.Home).firstVisibleItemIndex > 0)
                h.ui.drag(700f, 800f, 120f, 800f)
                h.assertSettled(3)
                h.ui.drag(120f, 800f, 700f, 800f)
                h.assertSettled(2)
                assertTrue("Returning must preserve the list position", h.lists.getValue(MainTab.Home).firstVisibleItemIndex > 0)
            }
        }
    }

    @Test
    fun `cross-axis interrupts list momentum while iOS mode requires a second horizontal swipe`() {
        for (appearance in UiMode.entries) {
            for (mode in listOf(PagerModeCrossAxis, PagerModeIosLike)) {
                PagerHost(appearance).use { h ->
                    h.gestureMode = mode
                    h.ui.advance()
                    h.ui.drag(410f, 1400f, 410f, 350f, holdBeforeUp = false, settle = false)
                    assertTrue("The scenario must start with list momentum", h.lists.getValue(MainTab.Home).isScrollInProgress)
                    h.ui.drag(700f, 800f, 120f, 800f)
                    if (mode == PagerModeCrossAxis) {
                        h.assertSettled(3)
                    } else {
                        h.assertSettled(2)
                        assertFalse(h.lists.getValue(MainTab.Home).isScrollInProgress)
                        h.ui.drag(700f, 800f, 120f, 800f)
                        h.assertSettled(3)
                    }
                }
            }
        }
    }

    @Test
    fun `cross-axis can change pages while a Miuix list rebounds at its top edge`() {
        PagerHost(UiMode.Miuix).use { h ->
            h.ui.drag(410f, 450f, 410f, 1300f, holdBeforeUp = false, settle = false)
            h.ui.drag(700f, 800f, 120f, 800f)
            h.assertSettled(3)
        }
    }

    @Test
    fun `preloaded pages load only on activation and only the current page refreshes on resume`() {
        PagerHost().use { h ->
            assertEquals(5, h.instances.size)
            assertEquals(listOf(0, 0, 1, 0, 0), MainTab.entries.map { h.loads[it] ?: 0 })
            val initialInstances = h.instances.toMap()
            h.ui.clickText("其他")
            h.ui.advance()
            assertEquals(1, h.loads[MainTab.Other])
            h.ui.clickText("主页")
            h.ui.advance()
            assertEquals(1, h.loads[MainTab.Home])
            MainTab.entries.forEach { assertSame(initialInstances[it], h.instances[it]) }
            h.ui.controller.pause().stop().start().resume()
            h.ui.advance()
            assertEquals(1, h.resumes[MainTab.Home])
            assertEquals(0, h.resumes[MainTab.Other] ?: 0)
        }
    }

    @Test
    fun `theme changes and recreation retain the settled page and scroll position`() {
        val saved: Bundle
        val position: Int
        PagerHost(withHeaders = true).use { h ->
            h.ui.clickText("设置")
            h.ui.advance()
            h.ui.drag(410f, 1300f, 410f, 450f)
            position = h.lists.getValue(MainTab.Setting).firstVisibleItemIndex
            assertTrue(position > 0)
            for (appearance in listOf(UiMode.Miuix, UiMode.Material, UiMode.Miuix)) {
                h.mode = appearance
                h.floating = appearance == UiMode.Miuix
                h.ui.advance()
                h.assertSettled(4)
                assertEquals(position, h.lists.getValue(MainTab.Setting).firstVisibleItemIndex)
                assertTrue(
                    "Switching appearance must keep a scrolled page's header collapsed",
                    h.topInsets.getValue(MainTab.Setting) < h.topInsets.getValue(MainTab.Home) - 30f,
                )
            }
            saved = h.ui.saveAndClose()
        }
        PagerHost(UiMode.Miuix, floating = true, saved = saved, withHeaders = true).use { h ->
            h.assertSettled(4)
            assertEquals(position, h.lists.getValue(MainTab.Setting).firstVisibleItemIndex)
        }
    }

    @Test
    fun `collapsed headers remain independent across page changes and recreation`() {
        for (appearance in UiMode.entries) {
            val saved: Bundle
            val collapsed: Float
            PagerHost(appearance, withHeaders = true).use { h ->
                val expanded = h.topInsets.getValue(MainTab.Home)
                h.ui.drag(410f, 1300f, 410f, 450f)
                collapsed = h.topInsets.getValue(MainTab.Home)
                h.ui.snapshot("header-${appearance.name.lowercase()}-collapsed")
                assertTrue("$appearance header must collapse: $expanded -> $collapsed", collapsed < expanded - 30f)
                h.ui.clickText("设置")
                h.ui.advance()
                assertEquals(expanded, h.topInsets.getValue(MainTab.Setting), 1f)
                h.ui.clickText("主页")
                h.ui.advance()
                assertEquals(collapsed, h.topInsets.getValue(MainTab.Home), 1f)
                saved = h.ui.saveAndClose()
            }
            PagerHost(appearance, saved = saved, withHeaders = true).use { h ->
                assertEquals(collapsed, h.topInsets.getValue(MainTab.Home), 1f)
            }
        }
    }

    @Test
    fun `back returns to home while a blocking handler takes priority over paging`() {
        PagerHost().use { h ->
            h.ui.clickText("设置")
            h.ui.advance()
            val input = DirectNavigationEventInput()
            h.ui.activity.navigationEventDispatcher.addInput(input)
            h.active = false
            h.ui.advance()
            h.ui.drag(160f, 800f, 720f, 800f)
            h.ui.clickText("其他")
            h.ui.advance()
            input.backCompleted()
            h.ui.advance()
            assertEquals(1, h.blockedBacks)
            h.assertSettled(4)
            h.active = true
            h.ui.advance()
            input.backCompleted()
            h.ui.advance()
            h.assertSettled(2)
            h.ui.activity.navigationEventDispatcher.removeInput(input)
        }
    }

    private class PagerHost(
        mode: UiMode = UiMode.Material,
        floating: Boolean = false,
        saved: Bundle? = null,
        withHeaders: Boolean = false,
        glass: Boolean = false,
    ) : AutoCloseable {
        val ui = ComposeGestureHost(saved)
        var mode by mutableStateOf(mode)
        var floating by mutableStateOf(floating)
        var gestureMode by mutableIntStateOf(PagerModeCrossAxis)
        var active by mutableStateOf(true)
        lateinit var state: MainPagerState
        val lists = mutableMapOf<MainTab, LazyListState>()
        val instances = mutableMapOf<MainTab, Any>()
        val loads = mutableMapOf<MainTab, Int>()
        val resumes = mutableMapOf<MainTab, Int>()
        val clicks = mutableMapOf<MainTab, Int>()
        val topInsets = mutableMapOf<MainTab, Float>()
        var blockedBacks = 0

        init {
            ui.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(2f),
                    LocalUiMode provides this.mode,
                    LocalEnableFloatingBottomBar provides this.floating,
                    LocalEnableFloatingBottomBarBlur provides glass,
                    LocalPagerInterceptionMode provides gestureMode,
                ) {
                    LuckyTheme(AppSettings(colorMode = ColorMode.LIGHT, dynamicColor = false), uiMode = this.mode) {
                        val pager = rememberPagerState(initialPage = 2) { MainTab.entries.size }
                        val mainState = rememberMainPagerState(pager)
                        SideEffect { state = mainState }
                        MainPagerBackHandler(mainState, enabled = active)
                        MainTabs(mainState, isActive = active) { tab, current ->
                            val list = rememberLazyListState()
                            val instance = remember { Any() }
                            SideEffect { lists[tab] = list; instances[tab] = instance }
                            MainPageActivationEffect(
                                current,
                                onFirstActive = { loads[tab] = (loads[tab] ?: 0) + 1 },
                                onResume = { resumes[tab] = (resumes[tab] ?: 0) + 1 },
                            )
                            val rows: @Composable (PaddingValues) -> Unit = { padding ->
                                val top = padding.calculateTopPadding().value
                                SideEffect { topInsets[tab] = top }
                                val listItems: LazyListScope.() -> Unit = {
                                    items(150) { row ->
                                        Text(
                                            "${tab.name} row $row",
                                            Modifier.fillMaxWidth().height(64.dp).clickable {
                                                clicks[tab] = (clicks[tab] ?: 0) + 1
                                            }.padding(16.dp),
                                        )
                                    }
                                }
                                // Match ScopeScreen's separate list nodes while retaining their shared state.
                                if (this.mode == UiMode.Miuix) {
                                    LazyColumn(
                                        state = list,
                                        modifier = Modifier.fillMaxSize().then(if (withHeaders) Modifier.overScrollVertical() else Modifier),
                                        contentPadding = padding,
                                        overscrollEffect = if (withHeaders) null else rememberOverscrollEffect(),
                                        content = listItems,
                                    )
                                } else {
                                    LazyColumn(
                                        state = list,
                                        modifier = Modifier.fillMaxSize().padding(padding),
                                        content = listItems,
                                    )
                                }
                            }
                            if (withHeaders) MaterialPageScaffold(title = tab.name, content = rows)
                            else rows(PaddingValues())
                        }
                        androidx.activity.compose.BackHandler(enabled = !active) { blockedBacks++ }
                    }
                }
            }
            ui.advance()
        }

        fun assertSettled(page: Int) {
            assertFalse(state.isNavigating)
            assertFalse(state.pagerState.isScrollInProgress)
            assertEquals(page, state.selectedPage)
            assertEquals(page, state.pagerState.currentPage)
            assertEquals(page, state.pagerState.settledPage)
        }

        override fun close() = ui.close()
    }
}
