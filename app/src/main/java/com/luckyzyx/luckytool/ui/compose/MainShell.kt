package com.luckyzyx.luckytool.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import com.luckyzyx.luckytool.ui.shell.LocalEnableSwipeDismiss
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 路由层级（Route 封闭层级）见 NavRoutes.kt：MainShell 与 FunctionPage 共用同一条栈

/** 跨 tab 跳转请求：pageKey = ScopePageRegistry 页键，title = 目标页标题（可空） */
data class FunctionRequest(val pageKey: String, val title: String?)

/** 底栏条目：drawable 供 Material 底栏，ImageVector 供 Miuix 底栏 */
private data class ShellTab(
    val labelRes: Int,
    val iconRes: Int,
    val mIcon: ImageVector,
    val route: Route,
)

/** 悬浮胶囊底栏占位高度（对齐 MiuixFloatingBottomBar 的 64.dp 胶囊 + 12.dp 余量） */
private val FloatingBarInset = 76.dp

@Composable
fun MainShell(activity: MainActivity) {
    // miuix-nav：返回栈就是一串 Route。显式给 <Route> 让库用封闭层级的序列化器持久化整条栈
    //（配置变更 / 进程死亡都能恢复），且栈底必须非空（NavDisplay 对空栈直接 require 失败）。
    val nav = rememberNavController<Route>(HomeRoute)
    val backStack = nav.backStack

    // 恢复旧版本存档的返回栈时补齐栈底：旧版本可能存成 [OtherRoute] 这种以非主页为根的栈，
    // 那样在该页返回会直接退出 App。
    LaunchedEffect(Unit) {
        if (backStack.firstOrNull() != HomeRoute) {
            Snapshot.withMutableSnapshot { backStack.add(0, HomeRoute) }
        }
    }

    val tabs = listOf(
        ShellTab(R.string.nav_other, R.drawable.ic_baseline_dashboard_24, Icons.Rounded.Dashboard, OtherRoute),
        ShellTab(R.string.nav_function, R.drawable.ic_baseline_extension_24, Icons.Rounded.Extension, FunctionTreeRoute),
        ShellTab(R.string.nav_home, R.drawable.ic_baseline_home_24, Icons.Rounded.Home, HomeRoute),
        ShellTab(R.string.nav_log, R.drawable.ic_baseline_assignment_24, Icons.AutoMirrored.Rounded.Assignment, LogRoute),
        ShellTab(R.string.nav_setting, R.drawable.ic_baseline_settings_24, Icons.Rounded.Settings, SettingRoute),
    )

    // tab 选中态不能只看栈顶：搜索页/作用域页压在同一条栈上，栈顶变成 ScopeRoute 时任何 tab 都不能判错
    //（selectedIndex = -1、底栏整条消失），而它其实属于功能 tab 的子栈。
    // 因此取「返回栈里最靠上的那个 tab 根路由」——子页归属它压在下面的那个 tab。
    val activeTabKey = backStack.lastOrNull { key -> tabs.any { tab -> tab.route == key } }
    // 栈顶 = 当前展示的目的地
    val topKey = backStack.lastOrNull()

    val onFunctionTab = activeTabKey == FunctionTreeRoute
    val onHomeTab = activeTabKey == HomeRoute
    // 主题页为全屏子页：隐藏底部导航，避免与页内 MaterialScaffold 顶栏叠加
    val onThemePage = topKey == ThemeRoute
    val onScopePage = topKey is ScopeRoute

    val uiMode = LocalUiMode.current
    val isMiuix = uiMode == UiMode.Miuix
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val enableBadge = LocalEnableNavigationBadge.current
    val enableSwipeDismiss = LocalEnableSwipeDismiss.current
    // 侧滑返回方向：库不会按布局方向自动翻转 entry 级 swipeDismiss，RTL 时往反方向滑；
    // 设置项「侧滑返回」关闭时不给任何页面挂手势（系统返回仍然可用）。
    val pageSwipeDirection = when {
        !enableSwipeDismiss -> NavSwipeDirection.None
        LocalLayoutDirection.current == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }

    // 作用域页分两种，不能一律隐藏底栏：
    // 1) 功能树子栈里的（onFunctionTab = true）：与合并前一致保留底栏并高亮「功能」；
    // 2) 跨 tab 直达的（onFunctionTab = false）：全屏页，隐藏底栏，返回时回到来源 tab。
    val showBar = !onThemePage && !(onScopePage && !onFunctionTab)
    // 悬浮底栏：Miuix 外观线 + 开关开启（主题页/全屏作用域页自身不显示底栏）
    val floatingBar = isMiuix && showBar && enableFloatingBottomBar

    // 回传当前 tab 状态（MainActivity.onResume 显示恢复 / checkOs 判断）
    LaunchedEffect(onFunctionTab) { activity.isOnFunctionTab = onFunctionTab }
    LaunchedEffect(onHomeTab) { activity.isOnHomeTab = onHomeTab }

    // 跨 tab 跳转请求（OtherPage/SettingPage → 作用域页）：直接压入全屏作用域页（隐藏底栏），
    // 不切到 Function tab，因此底栏不会从来源 tab（Other/Setting）跳到「功能」；返回时 pop 一次直接回到来源 tab。
    LaunchedEffect(Unit) {
        activity.functionNavRequests.collect { request ->
            if (request != null) {
                activity.functionNavRequests.value = null
                nav.pushOnce(ScopeRoute(request.pageKey, request.title ?: ""))
            }
        }
    }

    val selectedIndex = tabs.indexOfFirst { it.route == activeTabKey }
    // 点 tab = 把返回栈重置成该 tab 的根（不保留各 tab 子栈，不做 saveState/restoreState）
    val onSelect: (Int) -> Unit = { index ->
        tabs.getOrNull(index)?.let { nav.resetToTabRoot(it.route) }
    }

    // Miuix 外观线：模糊采样源（普通底栏背景模糊与悬浮胶囊液态玻璃共用）。
    // 关键约束：该图层只录制页面内容（录制修饰符挂在 NavDisplay 上），录制盒内
    // 绝不能包含任何绘制该图层的组件（底栏/胶囊），否则渲染树循环引用 →
    // RenderThread prepareTree 无限递归 → 原生栈溢出崩溃（SIGSEGV）。
    val backdropSurface = if (isMiuix) MiuixTheme.colorScheme.surface else Color.Unspecified
    val backdrop = if (isMiuix) {
        rememberLayerBackdrop {
            if (backdropSurface != Color.Unspecified) drawRect(backdropSurface)
            drawContent()
        }
    } else {
        null
    }
    // 普通底栏背景模糊：仅在模糊开关开启且 RenderEffect 可用时使用采样层
    val barBlurBackdrop = if (isMiuix && enableBlur && isRenderEffectSupported()) backdrop else null
    // 是否存在实际消费者需要录制背板：普通底栏模糊 或 悬浮胶囊液态玻璃
    val needBackdrop = backdrop != null &&
        ((barBlurBackdrop != null && !floatingBar) || (floatingBar && enableFloatingBottomBarBlur))

    val containerColor = if (isMiuix) {
        MiuixTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    // 权限阻塞门：模块自身 4 项权限未全授予时只渲染权限页（无底栏/抽屉、返回键无效、无跳过出口）。
    // 启动门（gateDialog）优先，避免两把门互抢；4 项齐全后本标志变 false，自动回到主界面（不重启 Activity）。
    val permissionBlocked = activity.isPermissionGateActive && activity.gateDialog == null
    if (permissionBlocked) {
        PermissionGateScreen(activity)
        return
    }

    // 根脚手架按外观线分派（对齐 KernelSU MainActivity「UiMode.Miuix -> Scaffold { navDisplay() }」）：
    // Miuix 脚手架的 popupHost 默认槽位即 MiuixPopupHost()，是本应用唯一能承载
    // OverlayDropdownPreference / OverlayDialog 的挂载点 —— 库中 LocalPopupStates /
    // LocalRootPopupStates 均为 internal，应用层无法自行提供，只能由根部 Miuix Scaffold 提供。
    // 因此各页保留自己的空 popupHost 槽位（库内 Miuix 页面同样如此），弹层统一冒泡到此处渲染。
    val rootBottomBar: @Composable () -> Unit = {
            when {
                // 主题页为全屏子页：不显示底栏
                !showBar -> Unit
                // 悬浮胶囊模式：胶囊在内容区上方单独绘制，底栏槽位留空
                floatingBar -> Unit
                isMiuix -> {
                    MiuixBottomBar(
                        items = tabs.mapIndexed { index, tab ->
                            MiuixBarItem(
                                label = stringResource(tab.labelRes),
                                icon = tab.mIcon,
                                badge = if (enableBadge && tab.route == HomeRoute && ShellBadgeState.updateAvailable) {
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
                            // 与 selectedIndex 同源：Material 底栏也必须按 tab 根判定，否则作用域页/搜索页上无 tab 高亮
                            val selected = tab.route == activeTabKey
                            // 角标数据源：主页发现新版本（对齐 KernelSU 导航角标开关）
                            val showBadge = enableBadge &&
                                tab.route == HomeRoute &&
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
            // 底部导航栏已消费系统导航栏 inset：告知页面列表不要重复叠加底部内边距
            LocalBottomBarPresent provides showBar,
            // 悬浮胶囊覆盖在内容之上：列表额外预留胶囊高度，条目仍可滑到胶囊之下
            LocalShellBottomInset provides if (floatingBar) FloatingBarInset else 0.dp,
        ) {
            // 采样层录制盒是 NavDisplay（见上）：本 Box 只做普通容器，
            // 悬浮胶囊位于本 Box 内、录制盒之外，避免渲染树循环引用
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    // 系统返回 / 预测性返回：弹一次栈顶。栈只剩根时库内部根本不注册返回处理
                    //（NavDisplay.kt:470 的 enabled = backStack.size > 1），事件照旧透传给系统 → 退出应用。
                    onBack = { nav.pop() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .then(
                            if (backdrop != null && needBackdrop) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    // 主页永远是栈底：非主页 tab 的根压在它上面，所以「tab 根」也能退回主页，
                    // 侧滑方向与返回语义一致；栈底（topIndex == 0）由库自动禁用，不用特判。
                    entry<HomeRoute>(swipeDismiss = pageSwipeDirection) { HomePage(activity) }
                    entry<OtherRoute>(swipeDismiss = pageSwipeDirection) { OtherPage(activity) }
                    // 功能 tab 的根就是功能树：功能树 → 搜索页 → 作用域页全压在同一条 shell 栈上。
                    // 库按 key 的精确运行时类匹配 entry，每种具体路由类型各注册一份即可。
                    entry<FunctionTreeRoute>(swipeDismiss = pageSwipeDirection) {
                        FunctionTreePage(activity, nav)
                    }
                    entry<LogRoute>(swipeDismiss = pageSwipeDirection) { LogPage() }
                    entry<SettingRoute>(swipeDismiss = pageSwipeDirection) {
                        SettingPage(
                            activity = activity,
                            onOpenTheme = { nav.pushOnce(ThemeRoute) },
                        )
                    }
                    // 可 pop 的子页：跟手横移 + 预测性返回都由库的 display 容器统一驱动，
                    // 这里只声明方向；页面自身不再挂边缘侧滑组件。
                    entry<SearchRoute>(swipeDismiss = pageSwipeDirection) { FunctionSearchPage(nav) }
                    entry<ThemeRoute>(swipeDismiss = pageSwipeDirection) {
                        ThemeScreen(onBack = { nav.pop() })
                    }
                    // 作用域页唯一宿主：功能树内打开与跨 tab 全屏直达共用，返回键/侧滑返回都弹本栈
                    entry<ScopeRoute>(swipeDismiss = pageSwipeDirection) { route ->
                        ScopePageHost(
                            activity = activity,
                            route = route,
                            onBack = { nav.pop() },
                            onNavigate = { key, title -> nav.pushOnce(ScopeRoute(key, title ?: "")) },
                        )
                    }
                }

                // 悬浮底栏（Apple 风格胶囊）：覆盖在内容之上，透明底 + 液态玻璃
                if (floatingBar) {
                    MiuixBottomBar(
                        items = tabs.map { tab ->
                            MiuixBarItem(
                                label = stringResource(tab.labelRes),
                                icon = tab.mIcon,
                                badge = if (enableBadge && tab.route == HomeRoute && ShellBadgeState.updateAvailable) {
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

                // 启动阻塞门（Xposed 未激活 / 无 root / 系统不支持）：不可取消，确定退出进程
                // Miuix 线的 OverlayDialog 由本 Scaffold 的 popup host 承载，因此必须组合在这里
                activity.gateDialog?.let { spec ->
                    GateDialog(
                        spec = spec,
                        onConfirm = { exitProcess(0) },
                        onNeutral = { activity.gateDialog = null },
                    )
                }
            }
        }
    }

    // 切换外观线会在两套 Scaffold 之间切换组合位置（Miuix Scaffold ↔ material3 Scaffold）：
    // 直接切换会销毁重建其内容，NavDisplay / NavController / 返回栈 / 页面内 remember 全部丢失。
    // movableContentOf 把同一份 shell 内容在两者之间「搬移」，槽表（状态）随内容走、只换外观容器；
    // shellContent 通过 rememberUpdatedState 间接化，避免固化首次组合的 lambda 实例
    //（否则底栏选中态、悬浮开关等捕获值会被冻结）。
    val currentShellContent = rememberUpdatedState(shellContent)
    val movableShellContent = remember {
        movableContentOf<PaddingValues> { innerPadding -> currentShellContent.value(innerPadding) }
    }

    if (isMiuix) {
        top.yukonga.miuix.kmp.basic.Scaffold(
            containerColor = containerColor,
            bottomBar = rootBottomBar,
            // 顶部 inset 由各页自己的顶栏（MiuixCompactTopBar 的 windowInsetsPadding）处理，
            // 根部脚手架不再下推内容，消除「状态栏高度 × 2」的顶部多余空白。
            // 底部/水平 inset 保留默认：底栏（含悬浮胶囊模式）与横屏仍需根部提供。
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
            ),
            content = movableShellContent,
        )
    } else {
        Scaffold(
            containerColor = containerColor,
            bottomBar = rootBottomBar,
            // 顶部 inset 由各页自己的顶栏（TopAppBar / MiuixCompactTopBar 的 windowInsets）处理，
            // 根部脚手架不再下推内容，消除「状态栏高度 × 2」的顶部多余空白。
            // 底部/水平 inset 保留默认：底栏与横屏仍需根部提供。
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
