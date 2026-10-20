package com.luckyzyx.luckytool.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.activity.MainActivity
import com.luckyzyx.luckytool.ui.compose.components.GateDialog
import com.luckyzyx.luckytool.ui.compose.components.material.LocalBottomBarPresent
import com.luckyzyx.luckytool.ui.compose.components.material.LocalShellBottomInset
import com.luckyzyx.luckytool.ui.compose.components.miuix.MiuixBarItem
import com.luckyzyx.luckytool.ui.compose.components.miuix.MiuixBottomBar
import com.luckyzyx.luckytool.ui.compose.pages.HomePage
import com.luckyzyx.luckytool.ui.compose.pages.LogPage
import com.luckyzyx.luckytool.ui.compose.pages.OtherPage
import com.luckyzyx.luckytool.ui.compose.pages.SettingPage
import com.luckyzyx.luckytool.ui.compose.pages.ThemeScreen
import com.luckyzyx.luckytool.ui.compose.special.AppPermissionContent
import com.luckyzyx.luckytool.ui.shell.LocalEnableBlur
import com.luckyzyx.luckytool.ui.shell.LocalEnableFloatingBottomBar
import com.luckyzyx.luckytool.ui.shell.LocalEnableFloatingBottomBarBlur
import com.luckyzyx.luckytool.ui.shell.LocalEnableNavigationBadge
import com.luckyzyx.luckytool.ui.shell.ShellBadgeState
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import kotlin.system.exitProcess
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 跨标签页打开作用域页时保留来源标签。 */
data class FunctionRequest(val pageKey: String, val title: String?)

private enum class MainTab(val labelRes: Int, val iconRes: Int, val mIcon: ImageVector) {
    Other(R.string.nav_other, R.drawable.ic_baseline_dashboard_24, Icons.Rounded.Dashboard),
    Function(R.string.nav_function, R.drawable.ic_baseline_extension_24, Icons.Rounded.Extension),
    Home(R.string.nav_home, R.drawable.ic_baseline_home_24, Icons.Rounded.Home),
    Log(R.string.nav_log, R.drawable.ic_baseline_assignment_24, Icons.AutoMirrored.Rounded.Assignment),
    Setting(R.string.nav_setting, R.drawable.ic_baseline_settings_24, Icons.Rounded.Settings),
}

/** 悬浮胶囊底栏占位高度。 */
private val FloatingBarInset = 76.dp

@Composable
fun MainShell(activity: MainActivity) {
    val navController = rememberNavController<AppRoute>(MainRoute)
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    val atMain = navController.backStack.last() == MainRoute
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val containerColor = if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

    LaunchedEffect(atMain, selectedTab) {
        activity.isOnFunctionTab = atMain && selectedTab == MainTab.Function
        activity.isOnHomeTab = atMain && selectedTab == MainTab.Home
    }
    LaunchedEffect(activity, navController) {
        activity.functionNavRequests.collect { request ->
            if (request != null) {
                activity.functionNavRequests.value = null
                navController.navigateTo(ScopeRoute(request.pageKey, request.title.orEmpty()))
            }
        }
    }

    // 启动门优先；权限未齐全时不挂载导航内容。
    if (activity.isPermissionGateActive && activity.gateDialog == null) {
        PermissionGateScreen(activity)
        return
    }

    // 标签页不是页面栈：主页之外的标签先返回主页，页面返回由 NavDisplay 接管。
    BackHandler(enabled = atMain && selectedTab != MainTab.Home && activity.gateDialog == null) {
        selectedTab = MainTab.Home
    }

    val shellContent: @Composable (PaddingValues) -> Unit = { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppNavDisplay(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                mainContent = {
                    MainTabs(
                        activity = activity,
                        selectedTab = selectedTab,
                        onSelected = { selectedTab = it },
                        onOpenTheme = { navController.navigateTo(ThemeRoute) },
                        onOpenPage = { key, title -> navController.navigateTo(ScopeRoute(key, title)) },
                        onOpenSearch = { navController.navigateTo(SearchRoute) },
                    )
                },
                themeContent = { ThemeScreen(onBack = { navController.pop() }) },
                searchContent = {
                    FunctionSearchScreen(
                        onBack = { navController.pop() },
                        onOpen = navController::navigateTo,
                    )
                },
                scopeContent = { route ->
                    ScopePageHost(
                        activity = activity,
                        route = route,
                        onBack = { navController.pop() },
                        onNavigate = { key, title -> navController.navigateTo(ScopeRoute(key, title.orEmpty())) },
                    )
                },
            )
            activity.gateDialog?.let { spec ->
                GateDialog(
                    spec = spec,
                    onConfirm = { exitProcess(0) },
                    onNeutral = { activity.gateDialog = null },
                )
            }
        }
    }
    // 外观切换时保留同一个导航宿主及其页面状态。
    val currentShellContent = rememberUpdatedState(shellContent)
    val movableShellContent = remember {
        movableContentOf<PaddingValues> { padding -> currentShellContent.value(padding) }
    }
    // 页面各自处理 insets；根 Scaffold 只提供全局 popup host。
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            containerColor = containerColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            content = movableShellContent,
        )
    } else {
        Scaffold(
            containerColor = containerColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            content = movableShellContent,
        )
    }
}

@Composable
private fun MainTabs(
    activity: MainActivity,
    selectedTab: MainTab,
    onSelected: (MainTab) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenPage: (String, String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val enableBadge = LocalEnableNavigationBadge.current
    val floatingBar = isMiuix && LocalEnableFloatingBottomBar.current
    val tabs = MainTab.entries
    val selectedIndex = selectedTab.ordinal
    val onSelect: (Int) -> Unit = { index -> tabs.getOrNull(index)?.let(onSelected) }
    val tabStateHolder = rememberSaveableStateHolder()

    // 只录制页面内容，底栏/胶囊必须在采样层之外，避免渲染树循环引用。
    val backdropSurface = if (isMiuix) MiuixTheme.colorScheme.surface else Color.Unspecified
    val backdrop = if (isMiuix) {
        rememberLayerBackdrop {
            drawRect(backdropSurface)
            drawContent()
        }
    } else {
        null
    }
    val barBlurBackdrop = if (isMiuix && enableBlur && isRenderEffectSupported()) backdrop else null
    val needBackdrop = backdrop != null &&
        ((barBlurBackdrop != null && !floatingBar) || (floatingBar && enableFloatingBottomBarBlur))
    val containerColor = if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

    val rootBottomBar: @Composable () -> Unit = {
        when {
            // 悬浮胶囊模式：胶囊在内容区上方单独绘制，底栏槽位留空
            floatingBar -> Unit
            isMiuix -> {
                MiuixBottomBar(
                    items = tabs.map { tab ->
                        MiuixBarItem(
                            label = stringResource(tab.labelRes),
                            icon = tab.mIcon,
                            badge = if (enableBadge && tab == MainTab.Home && ShellBadgeState.updateAvailable) {
                                { Badge { Text("1") } }
                            } else {
                                null
                            },
                        )
                    },
                    selectedIndex = selectedIndex,
                    onSelected = onSelect,
                    blurBackdrop = barBlurBackdrop,
                    backdrop = backdrop ?: rememberLayerBackdrop { },
                    floating = false,
                    floatingBlur = false,
                )
            }

            else -> {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedTab == tab
                        // 角标数据源：主页发现新版本（对齐 KernelSU 导航角标开关）
                        val showBadge = enableBadge &&
                            tab == MainTab.Home &&
                            ShellBadgeState.updateAvailable
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onSelect(tabs.indexOf(tab)) },
                            icon = {
                                if (showBadge) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ) { Text("1") }
                                        },
                                    ) {
                                        Icon(painterResource(tab.iconRes), contentDescription = null)
                                    }
                                } else {
                                    Icon(painterResource(tab.iconRes), contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }

    val shellContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        CompositionLocalProvider(
            LocalBottomBarPresent provides true,
            LocalShellBottomInset provides if (floatingBar) FloatingBarInset else 0.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .then(if (backdrop != null && needBackdrop) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    tabStateHolder.SaveableStateProvider(selectedTab.name) {
                        when (selectedTab) {
                            MainTab.Home -> HomePage(activity)
                            MainTab.Other -> OtherPage(activity)
                            MainTab.Function -> FunctionPage(activity, onOpenPage, onOpenSearch)
                            MainTab.Log -> LogPage()
                            MainTab.Setting -> SettingPage(activity, onOpenTheme)
                        }
                    }
                }

                // 悬浮底栏（Apple 风格胶囊）：覆盖在内容之上，透明底 + 液态玻璃
                if (floatingBar) {
                    MiuixBottomBar(
                        items = tabs.map { tab ->
                            MiuixBarItem(
                                label = stringResource(tab.labelRes),
                                icon = tab.mIcon,
                                badge = if (enableBadge && tab == MainTab.Home && ShellBadgeState.updateAvailable) {
                                    { Badge { Text("1") } }
                                } else {
                                    null
                                },
                            )
                        },
                        selectedIndex = selectedIndex,
                        onSelected = onSelect,
                        blurBackdrop = barBlurBackdrop,
                        backdrop = backdrop ?: rememberLayerBackdrop { },
                        floating = true,
                        floatingBlur = enableFloatingBottomBarBlur,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
    val currentShellContent = rememberUpdatedState(shellContent)
    val movableShellContent = remember {
        movableContentOf<PaddingValues> { padding -> currentShellContent.value(padding) }
    }
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            containerColor = containerColor,
            bottomBar = rootBottomBar,
            popupHost = {},
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            ),
            content = movableShellContent,
        )
    } else {
        Scaffold(
            containerColor = containerColor,
            bottomBar = rootBottomBar,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            ),
            content = movableShellContent,
        )
    }
}

/**
 * 权限阻塞态：只渲染权限页内容（无底栏/抽屉），吞掉系统返回键、无跳过出口。
 * 由 [MainShell] 提前 return 后组合，因此根 Scaffold 一并被跳过（它的弹层挂载点也随之不存在）；
 * 弹层挂载点改由本函数新建的同线 Scaffold 提供 —— 位置仍在 [MainShell] 内部，Miuix 线由此满足
 * OverlayDialog 对根部 Miuix Scaffold popup host 的依赖（见 GateDialog.kt 顶部说明）。
 */
@Composable
private fun PermissionGateScreen(activity: MainActivity) {
    // 阻塞态没有可返回的目的地：系统返回键无效
    BackHandler { }
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val containerColor = if (isMiuix) {
        MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val gateContent: @Composable () -> Unit = {
        AppPermissionContent(
            activity = activity,
            blocking = true,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            // 阻塞页独立成屏，行级水平内缩由自己提供（宿主列表不在场）
            selfInset = true,
            // 4 项齐全：撤掉阻塞门回到主界面（不重启 Activity）
            onAllGranted = { activity.refreshPermissionGate() },
            onExit = { exitProcess(0) },
        )
    }
    val gateInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            containerColor = containerColor,
            contentWindowInsets = gateInsets,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { gateContent() }
        }
    } else {
        Scaffold(
            containerColor = containerColor,
            contentWindowInsets = gateInsets,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { gateContent() }
        }
    }
}

/**
 * 底栏 tab 语义：点 tab 即把返回栈重置成该 tab 的根，不保留各 tab 的子栈（不做 saveState/restoreState）。
 *
 * 主页 [HomeRoute] 永远是栈底：非主页 tab 的根压在主页上面，于是「在其它 tab 的根上返回」= 退回主页，
 * 而不是退出 App（退出只发生在主页根上——栈只剩一项时库不注册预测性返回，事件透传给系统）。
 * 整条栈的替换放在同一个 mutable 快照里提交：NavDisplay 要求栈非空，清空与压入之间不能被观察到空栈。
 */
private fun NavController.resetToTabRoot(route: Route) {
    val target = if (route == HomeRoute) listOf(HomeRoute) else listOf(HomeRoute, route)
    if (backStack == target) return
    Snapshot.withMutableSnapshot {
        backStack.clear()
        backStack.addAll(target)
    }
}
