package com.luckyzyx.luckytool.ui.compose

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavTransitionScope
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNavigationTest {
    private val activities = mutableListOf<ActivityController<ComponentActivity>>()

    @After
    fun tearDown() {
        activities.forEach { it.pause().stop().destroy() }
        drainFrames()
    }

    @Test
    fun `all destinations and search targets are serializable as AppRoute`() {
        val routes: List<AppRoute> = listOf(
            MainRoute, ThemeRoute, SearchRoute,
            ScopeRoute("statusbar", "状态栏", "clock_style", 12),
        )
        assertEquals(routes, Json.decodeFromString<List<AppRoute>>(Json.encodeToString(routes)))
    }

    @Test
    fun `repeated taps cannot duplicate a destination or remove the root`() {
        val nav = NavController(navBackStackOf(MainRoute))
        val page = ScopeRoute("statusbar", "状态栏")
        nav.navigateTo(SearchRoute)
        nav.navigateTo(page)
        nav.navigateTo(page)
        nav.navigateTo(SearchRoute)
        assertEquals(listOf(MainRoute, SearchRoute, page), nav.backStack.toList())
        assertTrue(nav.pop())
        assertEquals(SearchRoute, nav.backStack.last())
        assertTrue(nav.pop())
        assertFalse(nav.pop())
        assertEquals(listOf(MainRoute), nav.backStack.toList())
    }

    @Test
    fun `predictive back cancellation keeps the page and completion pops once`() {
        val host = createHost()
        host.nav.navigateTo(ThemeRoute)
        drainFrames()
        val input = DirectNavigationEventInput()
        host.activity.navigationEventDispatcher.addInput(input)

        input.backStarted(backEvent(0f))
        input.backProgressed(backEvent(0.45f))
        drainFrames()
        assertEquals(0.45f, host.scopes.getValue(ThemeRoute).gesture!!.progress, 0.001f)
        assertEquals(-0.45f, host.scopes.getValue(ThemeRoute).relativeDepth, 0.001f)
        assertEquals(listOf(MainRoute, ThemeRoute), host.nav.backStack.toList())

        input.backCancelled()
        drainFrames()
        assertEquals(listOf(MainRoute, ThemeRoute), host.nav.backStack.toList())
        assertNull(host.scopes.getValue(ThemeRoute).gesture)
        assertEquals(0f, host.scopes.getValue(ThemeRoute).relativeDepth, 0.001f)

        input.backStarted(backEvent(0f))
        input.backProgressed(backEvent(0.65f))
        drainFrames()
        input.backCompleted()
        drainFrames()
        assertEquals(listOf(MainRoute), host.nav.backStack.toList())
        host.activity.navigationEventDispatcher.removeInput(input)
    }

    @Test
    fun `activity recreation restores the stack and search result arguments`() {
        val host = createHost()
        val target = ScopeRoute("statusbar", "状态栏", "clock_style", 12)
        host.nav.navigateTo(SearchRoute)
        host.nav.navigateTo(target)
        drainFrames()

        val savedState = Bundle()
        host.controller.pause().saveInstanceState(savedState).stop().destroy()
        activities.remove(host.controller)
        drainFrames()
        val restored = createHost(savedState)
        assertEquals(listOf(MainRoute, SearchRoute, target), restored.nav.backStack.toList())
        restored.nav.pop()
        drainFrames()
        assertEquals(SearchRoute, restored.nav.backStack.last())
    }

    private fun createHost(savedState: Bundle? = null): Host {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            .create(savedState).start().resume().visible()
        activities += controller
        val host = Host(controller)
        host.activity.setContent {
            val nav = rememberNavController<AppRoute>(MainRoute)
            SideEffect { host.nav = nav }
            AppNavDisplay(
                navController = nav,
                modifier = Modifier.fillMaxSize(),
                mainContent = { host.Page(MainRoute) },
                themeContent = { host.Page(ThemeRoute) },
                searchContent = { host.Page(SearchRoute) },
                scopeContent = { host.Page(it) },
            )
        }
        drainFrames()
        return host
    }

    private class Host(val controller: ActivityController<ComponentActivity>) {
        val activity: ComponentActivity get() = controller.get()
        lateinit var nav: NavController
        val scopes = mutableMapOf<AppRoute, NavTransitionScope>()

        @Composable
        fun Page(route: AppRoute) {
            val scope = LocalNavTransitionScope.current
            SideEffect { scopes[route] = scope }
            Box(Modifier.fillMaxSize())
        }
    }

    private fun backEvent(progress: Float) = NavigationEvent(
        swipeEdge = NavigationEvent.EDGE_LEFT,
        progress = progress,
        touchY = 300f,
    )

    private fun drainFrames() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
    }
}
