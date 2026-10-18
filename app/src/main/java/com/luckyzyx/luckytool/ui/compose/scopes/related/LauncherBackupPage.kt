package com.luckyzyx.luckytool.ui.compose.scopes.related

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyzyx.luckytool.R
import com.luckyzyx.luckytool.service.work.LauncherBackupScheduler
import com.luckyzyx.luckytool.ui.compose.components.PrefCard
import com.luckyzyx.luckytool.ui.compose.scopes.ScopePageSpec
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import com.luckyzyx.luckytool.utils.LauncherBackupPrefs
import com.luckyzyx.luckytool.utils.LauncherBackupUtils
import com.luckyzyx.luckytool.utils.LauncherSnapshot
import com.luckyzyx.luckytool.utils.LauncherSnapshotTag
import com.luckyzyx.luckytool.utils.LauncherState
import com.luckyzyx.luckytool.utils.ModulePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面布局备份/恢复页。
 *
 * 执行层在 launcher 进程（hook），本页只负责下发指令与展示回传：
 *  - 下发：把 "action|arg|token" 写到 prefs 的 launcher_backup_command，hook 用 dataChannel 监听并去重；
 *  - 回传：hook 发显式广播，LauncherBackupReceiver 存进 prefs 的 launcher_backup_state，本页解析展示；
 *  - 无回传（hook 未激活/桌面正在重启）时给出明确提示，不崩溃、不抛未捕获异常。
 */
object LauncherBackupPage {

    /** 等待回传的轮询间隔 */
    private const val POLL_INTERVAL_MS = 250L

    val spec = ScopePageSpec(
        pageKey = "launcher_backup",
        prefsName = ModulePrefs,
        packName = "",
        scopes = arrayOf(),
        restartEnabled = false,
    ) {
        val c = requireNotNull(context) { "ScopeScreen 未注入 Context" }
        category(c.getString(R.string.launcher_backup))
        switch(
            key = LauncherBackupPrefs.KEY_AUTO_ENABLED,
            title = c.getString(R.string.launcher_backup_auto),
            summary = c.getString(R.string.launcher_backup_auto_summary),
            onChange = { LauncherBackupScheduler.sync(c) },
        )
        list(
            key = LauncherBackupPrefs.KEY_AUTO_INTERVAL,
            title = c.getString(R.string.launcher_backup_interval),
            entries = arrayOf(
                c.getString(R.string.launcher_backup_interval_6h),
                c.getString(R.string.launcher_backup_interval_12h),
                c.getString(R.string.launcher_backup_interval_24h),
                c.getString(R.string.launcher_backup_interval_48h),
            ),
            entryValues = LauncherBackupPrefs.INTERVAL_HOURS.map { it.toString() }.toTypedArray(),
            default = LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS.toString(),
            summary = c.getString(R.string.launcher_backup_interval_summary),
            onChange = { LauncherBackupScheduler.sync(c) },
        )
        // standalone：本段自己就是若干张卡片，不能再被共享层套一层卡片
        custom(key = "launcher_backup_body", standalone = true) {
            val horizontal = if (LocalUiMode.current == UiMode.Miuix) 0.dp else 16.dp
            val rawState by state.stringFlow(LauncherBackupUtils.KEY_STATE, "")
                .collectAsStateWithLifecycle()
            val parsed = remember(rawState) { LauncherBackupUtils.parseStateJson(rawState) }
            val launcherPackage = remember { LauncherBackupUtils.detectLauncherPackage(c) }
            var busy by remember { mutableStateOf(false) }
            var notice by remember { mutableStateOf("") }
            var pendingRestore by remember { mutableStateOf<LauncherSnapshot?>(null) }
            val coroutineScope = rememberCoroutineScope()

            /** 下发指令并等回传：token 匹配才认为 hook 收到了本次请求 */
            suspend fun request(action: String, arg: String, timeoutMs: Long): LauncherState? {
                val token = LauncherBackupUtils.newToken()
                val command = LauncherBackupUtils.buildCommand(action, arg, token)
                if (!LauncherBackupUtils.sendCommand(c, command, launcherPackage)) {
                    notice = c.getString(R.string.launcher_backup_send_failed)
                    return null
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val current = LauncherBackupUtils.parseStateJson(
                        state.getString(LauncherBackupUtils.KEY_STATE, "")
                    )
                    if (current != null && current.token == token) return current
                    delay(POLL_INTERVAL_MS)
                }
                notice = c.getString(R.string.launcher_backup_no_hook)
                return null
            }

            suspend fun run(action: String, arg: String, timeoutMs: Long) {
                if (busy) return
                busy = true
                val result = request(action, arg, timeoutMs)
                if (result != null) {
                    notice = if (result.ok) c.getString(R.string.launcher_backup_result_ok)
                    else c.getString(R.string.launcher_backup_result_failed) + result.message
                }
                busy = false
            }

            LaunchedEffect(Unit) {
                run(LauncherBackupUtils.ACTION_LIST, "", 6000L)
            }

            val snapshots = parsed?.snapshots.orEmpty()
            val statusText = when {
                busy -> c.getString(R.string.launcher_backup_busy)
                parsed == null -> c.getString(R.string.launcher_backup_no_hook)
                else -> c.getString(R.string.launcher_backup_root) + ": " + parsed.backupRoot +
                    if (notice.isBlank()) "" else "\n" + notice
            }
            Column(Modifier.fillMaxWidth()) {
                PrefCard(
                    title = stringResource(R.string.launcher_backup_status),
                    summary = statusText,
                    modifier = Modifier.padding(horizontal = horizontal),
                    enabled = !busy,
                    onClick = { coroutineScope.launch { run(LauncherBackupUtils.ACTION_LIST, "", 6000L) } },
                )
                PrefCard(
                    title = stringResource(R.string.launcher_backup_backup_now),
                    summary = stringResource(R.string.launcher_backup_backup_now_summary),
                    modifier = Modifier.padding(horizontal = horizontal, vertical = 12.dp),
                    enabled = !busy,
                    onClick = {
                        coroutineScope.launch {
                            run(
                                LauncherBackupUtils.ACTION_BACKUP,
                                LauncherSnapshotTag.MANUAL.id,
                                20000L,
                            )
                        }
                    },
                )
                if (snapshots.isEmpty()) {
                    PrefCard(
                        title = stringResource(R.string.launcher_backup_empty),
                        modifier = Modifier.padding(horizontal = horizontal),
                    )
                } else {
                    snapshots.forEachIndexed { index, snapshot ->
                        PrefCard(
                            title = snapshotTitle(c, snapshot),
                            summary = snapshotSummary(c, snapshot),
                            modifier = Modifier.padding(
                                start = horizontal,
                                end = horizontal,
                                top = if (index == 0) 0.dp else 12.dp,
                            ),
                            enabled = !busy,
                            onClick = { pendingRestore = snapshot },
                        )
                    }
                }
            }

            // 恢复确认：必须有明确的二次确认，恢复后桌面会重启
            val target = pendingRestore
            if (target != null) {
                when (LocalUiMode.current) {
                    UiMode.Miuix -> OverlayDialog(
                        show = true,
                        onDismissRequest = { pendingRestore = null },
                    ) {
                        MiuixText(
                            text = stringResource(R.string.launcher_backup_restore_message),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MiuixTextButton(
                            text = stringResource(R.string.launcher_backup_restore_confirm),
                            onClick = {
                                pendingRestore = null
                                coroutineScope.launch {
                                    run(
                                        LauncherBackupUtils.ACTION_RESTORE,
                                        target.name,
                                        30000L,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                        MiuixTextButton(
                            text = stringResource(R.string.launcher_backup_cancel),
                            onClick = { pendingRestore = null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    UiMode.Material -> AlertDialog(
                        onDismissRequest = { pendingRestore = null },
                        title = { Text(stringResource(R.string.launcher_backup_restore_title)) },
                        text = { Text(stringResource(R.string.launcher_backup_restore_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingRestore = null
                                coroutineScope.launch {
                                    run(
                                        LauncherBackupUtils.ACTION_RESTORE,
                                        target.name,
                                        30000L,
                                    )
                                }
                            }) {
                                Text(stringResource(R.string.launcher_backup_restore_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingRestore = null }) {
                                Text(stringResource(R.string.launcher_backup_cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    /** 快照行标题：时间 + 标签 */
    private fun snapshotTitle(context: Context, snapshot: LauncherSnapshot): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(snapshot.timeMillis))
        return stamp + " · " + tagLabel(context, snapshot.tag)
    }

    /** 快照行摘要：占用大小与布局行数 */
    private fun snapshotSummary(context: Context, snapshot: LauncherSnapshot): String {
        val size = formatSize(snapshot.sizeBytes)
        return size + " · " + snapshot.rows + " " + context.getString(R.string.launcher_backup_rows_unit)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "-"
        bytes < 1024L -> bytes.toString() + " B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun tagLabel(context: Context, tag: LauncherSnapshotTag): String = when (tag) {
        LauncherSnapshotTag.AUTO -> context.getString(R.string.launcher_backup_tag_auto)
        LauncherSnapshotTag.MANUAL -> context.getString(R.string.launcher_backup_tag_manual)
        LauncherSnapshotTag.PRE_RESTORE -> context.getString(R.string.launcher_backup_tag_pre_restore)
    }
}
