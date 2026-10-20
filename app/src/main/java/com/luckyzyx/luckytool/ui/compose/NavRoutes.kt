package com.luckyzyx.luckytool.ui.compose

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavController
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 全应用唯一的导航路由层级（miuix-nav 的 [NavKey]）。
 *
 * MainShell 与 FunctionPage 的所有目的地都必须落在同一个**封闭**层级里：
 * `rememberNavController<Route>(HomeRoute)` 用它生成反射无关的多态序列化器来持久化整条返回栈，
 * 因此这里必须是 @Serializable 的 sealed 层级（开放接口会退化成需要 SerializersModule，直接抛异常）。
 */
@Serializable
sealed interface Route : NavKey

/** 其它（主页以外的分类入口页） */
@Serializable
data object OtherRoute : Route

/** 功能 tab 的根：功能树 */
@Serializable
data object FunctionTreeRoute : Route

/** 全屏搜索 */
@Serializable
data object SearchRoute : Route

@Serializable
data object HomeRoute : Route

@Serializable
data object LogRoute : Route

@Serializable
data object SettingRoute : Route

/** 主题与配色页（迁移 KernelSU colorpalette，Material / Miuix 双实现） */
@Serializable
data object ThemeRoute : Route

/** 作用域页：pageKey = ScopePageRegistry 页键，title = 目标页标题（可空），scrollKey/scrollPosition = 进入后定位的偏好行 */
@Serializable
data class ScopeRoute(
    val pageKey: String,
    val title: String = "",
    val scrollKey: String = "",
    val scrollPosition: Int = -1,
) : Route

/**
 * 压栈，但跳过与栈顶相同的路由（等价于 navigation-compose 的 launchSingleTop）。
 *
 * miuix-nav 按 key 的取值去重：同一条相等的 key 压两次会在 reconcile 时抛 IllegalArgumentException，
 * 双击 / 重复请求必须在这里挡掉。
 */
internal fun NavController.pushOnce(key: Route) {
    if (backStack.lastOrNull() != key) push(key)
}
