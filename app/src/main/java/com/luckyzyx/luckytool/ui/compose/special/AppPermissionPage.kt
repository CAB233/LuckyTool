package com.luckyzyx.luckytool.ui.compose.special

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.ui.activity.MainActivity
import com.luckyzyx.luckytool.ui.compose.components.PrefCard
import com.luckyzyx.luckytool.ui.compose.components.PrefGroup
import com.luckyzyx.luckytool.ui.compose.components.PrefRow
import com.luckyzyx.luckytool.ui.compose.components.PrefRowInset
import com.luckyzyx.luckytool.ui.compose.components.miuix.MiuixPrefDefaults
import com.luckyzyx.luckytool.ui.compose.scopes.ScopePageSpec
import com.luckyzyx.luckytool.ui.service.XposedServiceBridge
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import com.luckyzyx.luckytool.utils.AppPermission
import com.luckyzyx.luckytool.utils.DeviceUtils
import com.luckyzyx.luckytool.utils.PermissionUtils
import com.luckyzyx.luckytool.utils.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

/**
 * 应用权限页（pageKey = `app_permission`）。
 *
 * 同一份内容 [AppPermissionContent] 有两个消费方：
 * - 设置页入口（本 [spec]，正常态、可返回）；
 * - 冷启动阻塞门（MainShell 内提前渲染，阻塞态：无底栏/抽屉、返回键无效、无跳过出口）。
 */
object AppPermissionPage {

    val spec = ScopePageSpec(
        pageKey = "app_permission",
        prefsName = SettingsPrefs,
        packName = "",
        scopes = arrayOf(),
        restartEnabled = false,
    ) {
        custom(key = "app_permission_content", bare = true) {
            // Miuix 线：bare 条目在渲染缝里被统一 offset(-CardHorizontalInset)，那是给「自带 16dp
            // 内距的裸分类标题」抵消列表级 12dp 用的；本页内容是要跟随卡片 12dp 内缩的卡片，故必须
            // 等量回正，否则卡片会贴到屏幕边缘 —— 与 HomePage 的 status_card（bare + 卡片内容，
            // pages/HomePage.kt:215-229）、ForceFpsPage、CorePatchPage 同一惯例。
            // 该 seam 只作用于设置页入口这条 bare 缝；冷启动阻塞页不经列表，左右留白由 selfInset 自补
            val bareAlign = if (LocalUiMode.current == UiMode.Miuix) {
                Modifier.offset(x = MiuixPrefDefaults.CardHorizontalInset)
            } else {
                Modifier
            }
            // custom() 逃生舱：material 线行内容需自带 16dp 内缩（Miuix 行由 insideMargin 处理）
            PrefRowInset {
                AppPermissionContent(
                    activity = LocalContext.current.findMainActivity(),
                    blocking = false,
                    modifier = bareAlign,
                )
            }
        }
    }
}

/** 权限行的查询状态快照（[PermissionUtils] 的纯查询结果） */
private data class PermissionRowState(val granted: Boolean, val doNotAskAgain: Boolean)

/** Root / LSPosed 只读信息（查询要起 shell，放 IO 线程） */
private data class RootInfo(
    val rooted: Boolean,
    val rootVersion: String,
    val frameworkVersion: String,
)

/**
 * 应用权限页内容（阻塞态与设置页入口共用）。
 *
 * [blocking] = true 时是冷启动阻塞态：4 项齐全后回调 [onAllGranted] 放行，并显示「退出应用」。
 * 本页只做纯查询展示，申请与跳转都只能由用户手势发起（行右侧按钮或整行点击）。
 *
 * [selfInset] = true 表示宿主不提供行级水平内缩（阻塞态独立成屏），由本页自补，否则内容贴边；
 * 被作用域页列表包裹时（设置页入口）保持 false，避免与宿主内缩叠加成 24/32dp。
 */
@SuppressLint("ReplaceWithLifecycleExtension")
@Composable
fun AppPermissionContent(
    activity: MainActivity?,
    blocking: Boolean,
    modifier: Modifier = Modifier,
    selfInset: Boolean = false,
    onAllGranted: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current

    // 授予状态快照：revision 自增即重算（从系统设置页返回、逐项申请回调后）
    var revision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // 去系统设置页授权后返回：只重新查询状态，不申请、不跳转
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val states = remember(activity, revision) {
        if (activity == null) {
            emptyMap()
        } else {
            AppPermission.entries.associateWith { kind ->
                PermissionRowState(
                    granted = PermissionUtils.isGranted(activity, kind),
                    doNotAskAgain = PermissionUtils.isDoNotAskAgain(activity, kind),
                )
            }
        }
    }
    val allGranted = states.isNotEmpty() && states.values.all { it.granted }
    // 4 项齐全即放行（不重启 Activity）：阻塞标志由 MainActivity 的纯查询驱动
    LaunchedEffect(blocking, allGranted) {
        if (blocking && allGranted) onAllGranted()
    }

    // Root / LSPosed 纯只读展示：查询要起 shell，放 IO 线程，进入页面查一次
    val rootInfo by produceState<RootInfo?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            RootInfo(
                rooted = DeviceUtils.getRootStatus(),
                rootVersion = DeviceUtils.getRootVersion(context),
                frameworkVersion = DeviceUtils.getFrameWorkVersion(context),
            )
        }
    }
    val moduleActive by XposedServiceBridge.isModuleActive.collectAsStateWithLifecycle()

    // selfInset：阻塞态独立成屏、宿主列表不在场，行级水平内缩由本页补（否则内容贴边）；
    // 设置页入口由宿主列表提供，保持不叠加
    val lineInset = lineHorizontalInset()
    Column(
        modifier = modifier.then(
            if (selfInset) Modifier.padding(horizontal = lineInset) else Modifier
        )
    ) {
        // ===== Root / LSPosed：纯只读，整块放最上面（不参与门禁判定，无任何跳转）=====
        SectionTitle(stringResource(R.string.app_permission_root_section))
        PrefGroup {
            item {
                PrefRow(
                    title = stringResource(R.string.app_permission_root_status),
                    summary = stringResource(
                        if (rootInfo?.rooted == true) {
                            R.string.app_permission_root_yes
                        } else {
                            R.string.app_permission_root_no
                        }
                    ),
                )
            }
            val info = rootInfo
            // 顺序固定：Root 来源 → Xposed 状态 → 框架版本
            if (info != null && info.rootVersion.isNotBlank()) {
                item { PrefRow(title = info.rootVersion) }
            }
            item {
                PrefRow(
                    title = stringResource(R.string.app_permission_module_status),
                    summary = stringResource(
                        if (moduleActive) {
                            R.string.app_permission_module_active
                        } else {
                            R.string.app_permission_module_inactive
                        }
                    ),
                )
            }
            if (info != null && info.frameworkVersion.isNotBlank()) {
                item { PrefRow(title = info.frameworkVersion) }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle(
            stringResource(
                if (blocking) R.string.app_permission_blocked_title else R.string.app_permission
            )
        )
        SectionBody(
            stringResource(
                if (blocking) {
                    R.string.app_permission_blocked_summary
                } else {
                    R.string.app_permission_summary
                }
            )
        )
        PrefGroup {
            AppPermission.entries.forEach { kind ->
                val state = states[kind]
                val granted = state?.granted == true
                val doNotAskAgain = state?.doNotAskAgain == true
                // 授权：只申请、不自动跳转，回调后重算状态
                val onGrant: (() -> Unit)? = if (!granted && activity != null) {
                    { PermissionUtils(activity).request(kind) { revision++ } }
                } else {
                    null
                }
                // 被「不再询问」永久拒绝：只有用户点「去设置」才跳系统设置页
                val onOpenSettings: (() -> Unit)? =
                    if (!granted && doNotAskAgain && activity != null) {
                        { PermissionUtils(activity).openSystemPermissionSettings(kind) }
                    } else {
                        null
                    }
                item {
                    PrefRow(
                        title = stringResource(kind.labelRes),
                        summary = stringResource(
                            if (granted) {
                                R.string.app_permission_granted
                            } else {
                                R.string.app_permission_denied
                            }
                        ),
                        // 整行点击与右侧按钮同义：永久拒绝时两者都只跳系统设置（且必须由用户手势发起）
                        onClick = onOpenSettings ?: onGrant,
                        // 右侧按钮给出明确可点的目标：授权 / 已授权（弱化不可点）/ 去设置
                        trailing = if (activity == null) {
                            null
                        } else {
                            {
                                when {
                                    granted -> ActionButton(
                                        text = stringResource(R.string.app_permission_granted),
                                        enabled = false,
                                        onClick = {},
                                    )

                                    onOpenSettings != null -> ActionButton(
                                        text = stringResource(R.string.app_permission_open_settings),
                                        onClick = onOpenSettings,
                                    )

                                    else -> ActionButton(
                                        text = stringResource(R.string.app_permission_action_grant),
                                        onClick = onGrant ?: {},
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        if (blocking) {
            Spacer(Modifier.height(24.dp))
            // 无跳过出口：阻塞态只提供逐项授权与退出应用
            PrefCard(
                title = stringResource(R.string.app_permission_exit),
                onClick = onExit,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 行级水平内缩基准（与宿主列表一致）：Miuix 由列表级提供 12dp（行内容再靠行内置 16dp 到 28dp，
 * 见 MiuixPrefList 注释）；material 由 PrefRowInset 提供基准 16dp。注意 material 线的行文字还会
 * 叠加 M3 列表行自带的 ListTokens.ItemLeadingSpace(16dp)，实际落在 32dp —— 即 material 三组为
 * 标题 16 / 卡片 16 / 行文字 32（该 32dp 不来自本函数，本函数只给与卡片对齐的 16dp 基准）。
 */
@Composable
private fun lineHorizontalInset(): Dp =
    if (LocalUiMode.current == UiMode.Miuix) MiuixPrefDefaults.CardHorizontalInset else 16.dp

/** 分类标题相对容器的额外内缩：两条线的标题基准都是 16dp —— Miuix 补 4dp，material 不补 */
@Composable
private fun titleHorizontalInset(): Dp =
    if (LocalUiMode.current == UiMode.Miuix) 16.dp - MiuixPrefDefaults.CardHorizontalInset else 0.dp

/** 小节标题（双线） */
@Composable
private fun SectionTitle(text: String) {
    val extra = titleHorizontalInset()
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = extra, end = extra, top = 12.dp, bottom = 4.dp)
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(text = text, modifier = modifier)
        UiMode.Material -> Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 小节说明文字（双线） */
@Composable
private fun SectionBody(text: String) {
    val extra = titleHorizontalInset()
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = extra, end = extra, bottom = 4.dp)
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(text = text, modifier = modifier)
        UiMode.Material -> Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 双线动作按钮：Miuix TextButton / material3 TextButton；[enabled] = false 走各自禁用弱化样式 */
@Composable
private fun ActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTextButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )

        UiMode.Material -> TextButton(onClick = onClick, enabled = enabled) { Text(text) }
    }
}

/** LocalContext 可能是包装 Context：逐层解包取宿主 Activity，取不到就是 null */
private fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}
