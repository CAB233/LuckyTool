package com.luckyzyx.luckytool.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.luckyzyx.luckytool.ui.shell.LocalFloatingBottomBarStyle
import com.luckyzyx.luckytool.ui.shell.LocalEnableNavigationBadge
import com.luckyzyx.luckytool.ui.shell.ShellBadgeState
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.system.exitProcess

/** 跨标签页打开作用域页时保留来源标签。 */
data class FunctionRequest(val pageKey: String, val title: String?)

internal enum class MainTab(val labelRes: Int, val iconRes: Int, val miuixIcon: ImageVector) {
    Other(R.string.nav_other, R.drawable.ic_baseline_dashboard_24, MiuixIcons.GridView),
    Function(R.string.nav_function, R.drawable.ic_baseline_extension_24, MiuixIcons.Tune),
    Home(R.string.nav_home, R.drawable.ic_baseline_home_24, MiuixIcons.Home),
    Log(R.string.nav_log, R.drawable.ic_baseline_assignment_24, MiuixIcons.Notes),
    Setting(R.string.nav_setting, R.drawable.ic_baseline_settings_24, MiuixIcons.Settings),
}

@Composable
fun MainShell(activity: MainActivity) {
    val navController = rememberNavController<AppRoute>(MainRoute)
    val pagerState = rememberPagerState(initialPage = MainTab.Home.ordinal) { MainTab.entries.size }
    val mainPagerState = rememberMainPagerState(pagerState)
    val settledTab = MainTab.entries[pagerState.settledPage]
    val atMain = navController.backStack.last() == MainRoute
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val containerColor = if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

    LaunchedEffect(atMain, settledTab) {
        activity.isOnFunctionTab = atMain && settledTab == MainTab.Function
        activity.isOnHomeTab = atMain && settledTab == MainTab.Home
    }
    LaunchedEffect(activity, navController) {
        activity.functionNavRequests.collect { request ->
            if (request != null) {
                activity.functionNavRequests.value = null
                navController.navigateTo(ScopeRoute(request.pageKey, request.title.orEmpty()))
            }
        }
    }

    // 功能页数据（功能树行 + 全屏搜索索引）在 App 打开时就后台预取：切到「功能」标签页时
    // 不再现算 50 页偏好 DSL + PackageManager IPC，避免首帧空白再 pop-in 的割裂感（见 FunctionPageData）
    LaunchedEffect(activity) { FunctionPageData.preload(activity) }

    // 启动门优先；权限未齐全时不挂载导航内容。
    if (activity.isPermissionGateActive && activity.gateDialog == null) {
        PermissionGateScreen(activity)
        return
    }

    // 标签页不是页面栈：主页之外的标签先返回主页，页面返回由 NavDisplay 接管。
    MainPagerBackHandler(
        state = mainPagerState,
        enabled = atMain && activity.gateDialog == null,
    )

    val shellContent: @Composable (PaddingValues) -> Unit = { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AppNavDisplay(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                mainContent = {
                    MainTabs(
                        mainPagerState = mainPagerState,
                        isActive = atMain && activity.gateDialog == null,
                    ) { tab, isCurrentPage ->
                        when (tab) {
                            MainTab.Home -> HomePage(activity, isCurrentPage)
                            MainTab.Other -> OtherPage(activity, isCurrentPage)
                            MainTab.Function -> FunctionPage(
                                activity = activity,
                                onOpenPage = { key, title -> navController.navigateTo(ScopeRoute(key, title)) },
                                onOpenSearch = { navController.navigateTo(SearchRoute) },
                            )
                            MainTab.Log -> LogPage()
                            MainTab.Setting -> SettingPage(activity) { navController.navigateTo(ThemeRoute) }
                        }
                    }
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
internal fun MainTabs(
    mainPagerState: MainPagerState,
    isActive: Boolean,
    pageContent: @Composable (tab: MainTab, isCurrentPage: Boolean) -> Unit,
) {
    val isMiuix = LocalUiMode.current == UiMode.Miuix
    val enableBlur = LocalEnableBlur.current
    val floatingStyle = LocalFloatingBottomBarStyle.current
    val density = LocalDensity.current
    var floatingBarHeight by remember(floatingStyle, density) { mutableStateOf(76.dp) }
    val enableBadge = LocalEnableNavigationBadge.current
    val floatingBar = isMiuix && LocalEnableFloatingBottomBar.current
    val tabs = MainTab.entries
    val selectedIndex = mainPagerState.selectedPage
    val onSelect: (Int) -> Unit = { index -> if (isActive) mainPagerState.animateToPage(index) }
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
    val needBackdrop = barBlurBackdrop != null
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
                            icon = tab.miuixIcon,
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
                    floating = false,
                )
            }

            else -> {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedIndex == tab.ordinal
                        // 角标数据源：主页发现新版本。
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
            LocalShellBottomInset provides if (floatingBar) {
                (floatingBarHeight - innerPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
            } else 0.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .then(if (backdrop != null && needBackdrop) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    MainPager(
                        state = mainPagerState,
                        gesturesEnabled = isActive,
                        pageKey = { tabs[it].name },
                    ) { index ->
                        val tab = tabs[index]
                        val isCurrentPage = isActive && index == mainPagerState.pagerState.settledPage &&
                            !mainPagerState.pagerState.isScrollInProgress
                        tabStateHolder.SaveableStateProvider(tab.name) {
                            pageContent(tab, isCurrentPage)
                        }
                    }
                }

                // 悬浮底栏位于背景采样层之外，页面按实际测量高度留出滚动空间。
                if (floatingBar) {
                    MiuixBottomBar(
                        items = tabs.map { tab ->
                            MiuixBarItem(
                                label = stringResource(tab.labelRes),
                                icon = tab.miuixIcon,
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
                        floating = true,
                        floatingStyle = floatingStyle,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { floatingBarHeight = with(density) { it.height.toDp() } },
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

