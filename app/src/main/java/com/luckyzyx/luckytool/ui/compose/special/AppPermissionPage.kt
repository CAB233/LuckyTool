package com.luckyzyx.luckytool.ui.compose.special

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
            // Miuix 线：bare 条目在渲染缝里被统一 offset(-CardHorizontalInset)（供自带 16dp 内距的
            // 裸分类标题抵消列表级 12dp 内缩）。本页内容是要跟随卡片 12dp 内缩的卡片，故用等量正向
            // offset 回正，左右各 12dp 与其余卡片对齐 —— 与 HomePage 的 status_card（bare + 卡片
            // 内容，pages/HomePage.kt:215-229）同一处理；material 线 bare 无该偏移。
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
 * 页面内逐项申请绝不自动跳转；只有被永久拒绝的行，才由用户手势点「去系统设置」发起跳转。
 */
@Composable
fun AppPermissionContent(
    activity: MainActivity?,
    blocking: Boolean,
    modifier: Modifier = Modifier,
    onAllGranted: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val host = activity

    // 授予状态快照：revision 自增即重算（从系统设置页返回、逐项申请回调后）
    var revision by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // 去系统设置页授权后返回：只重新查询状态，不申请、不跳转
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val states = remember(host, revision) {
        if (host == null) {
            emptyMap()
        } else {
            AppPermission.values().associateWith { kind ->
                PermissionRowState(
                    granted = PermissionUtils.isGranted(host, kind),
                    doNotAskAgain = PermissionUtils.isDoNotAskAgain(host, kind),
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

    Column(modifier) {
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
            AppPermission.values().forEach { kind ->
                val state = states[kind]
                val granted = state?.granted == true
                val request: (() -> Unit)? = if (!granted && host != null) {
                    {
                        // 逐项申请：不自动跳转，回调后只重算状态
                        PermissionUtils(host).request(kind) { revision++ }
                    }
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
                        onClick = request,
                        // 永久拒绝的行：只有用户点这个按钮才跳系统设置页
                        trailing = if (!granted && state?.doNotAskAgain == true && host != null) {
                            {
                                ActionButton(
                                    text = stringResource(R.string.app_permission_open_settings),
                                    onClick = { PermissionUtils(host).openSystemPermissionSettings(kind) },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
            if (info != null) {
                if (info.rootVersion.isNotBlank()) item { PrefRow(title = info.rootVersion) }
                if (info.frameworkVersion.isNotBlank()) {
                    item { PrefRow(title = info.frameworkVersion) }
                }
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

/** 小节标题（双线） */
@Composable
private fun SectionTitle(text: String) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
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
    val modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
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

/** 双线动作按钮：Miuix TextButton / material3 TextButton */
@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTextButton(
            text = text,
            onClick = onClick,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        UiMode.Material -> TextButton(onClick = onClick) { Text(text) }
    }
}

/** LocalContext 可能是包装 Context：逐层解包取宿主 Activity，取不到就是 null */
private fun Context.findMainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> null
}
