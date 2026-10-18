package com.luckyzyx.luckytool.ui.compose.scopes.related

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.luckyzyx.luckytool.ui.compose.components.PrefCard
import com.luckyzyx.luckytool.ui.compose.scopes.ScopePageSpec
import com.luckyzyx.luckytool.ui.theme.LocalUiMode
import com.luckyzyx.luckytool.ui.theme.UiMode
import com.luckyzyx.luckytool.utils.LauncherBackupPrefs
import com.luckyzyx.luckytool.utils.LauncherBackupTransfer
import com.luckyzyx.luckytool.utils.LauncherBackupUtils
import com.luckyzyx.luckytool.utils.LauncherSnapshot
import com.luckyzyx.luckytool.utils.LauncherSnapshotTag
import com.luckyzyx.luckytool.utils.LauncherTaskError
import com.luckyzyx.luckytool.utils.LauncherTaskResult
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.formatFileSize
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面布局备份/恢复页。
 *
 * 执行层在 launcher 进程（hook 的 LauncherBackupHooker），本页只做三件事：
 *  - 文件打包落盘：Download/LuckyTool/launcher_backup/<yyyyMMdd_HHmmss>_<tag>.ltbackup（标准 zip：meta.json + layout.json）；
 *  - 把文件 uri 经 dataChannel 指令交给桌面进程，并提前 grantUriPermission（跨 uid 不授权必抛 SecurityException）；
 *  - 列本地文件、恢复前补拍、二次确认。
 *
 * 没有回传通道：备份的 ack 是「文件长度由 0 变非 0」，恢复只提示已下发，结果看桌面 toast。
 */
object LauncherBackupPage {

    /** 由页面内容组合时注册的刷新回调（下拉刷新时重列本地文件） */
    private var reloader: (() -> Unit)? = null

    val spec = ScopePageSpec(
        pageKey = "launcher_backup",
        prefsName = ModulePrefs,
        packName = "",
        scopes = arrayOf(),
        restartEnabled = false,
        onRefresh = { reloader?.invoke() },
    ) {
        val c = requireNotNull(context) { "ScopeScreen 未注入 Context" }
        category(c.getString(R.string.launcher_backup))
        switch(
            key = LauncherBackupPrefs.KEY_AUTO_ENABLED,
            title = c.getString(R.string.launcher_backup_auto),
            summary = c.getString(R.string.launcher_backup_auto_summary),
        )
        list(
            key = LauncherBackupPrefs.KEY_AUTO_INTERVAL,
            title = c.getString(R.string.launcher_backup_interval),
            entries = LauncherBackupPrefs.INTERVAL_HOURS.map { intervalLabel(c, it) }.toTypedArray(),
            entryValues = LauncherBackupPrefs.INTERVAL_HOURS.map { it.toString() }.toTypedArray(),
            default = LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS.toString(),
            summary = c.getString(R.string.launcher_backup_interval_summary),
        )
        // standalone：本段自己就是若干张卡片，不能再被共享层套一层卡片
        custom(key = "launcher_backup_body", standalone = true) {
            val horizontal = if (LocalUiMode.current == UiMode.Miuix) 0.dp else 16.dp
            val coroutineScope = rememberCoroutineScope()
            val backupDir = remember { LauncherBackupTransfer.backupDir() }
            val launcherPackage = remember { LauncherBackupUtils.detectLauncherPackage(c) }
            val busy by LauncherBackupTransfer.busy.collectAsStateWithLifecycle()
            var snapshots by remember { mutableStateOf(LauncherBackupTransfer.listSnapshots()) }
            var notice by remember { mutableStateOf("") }
            var pendingRestore by remember { mutableStateOf<LauncherSnapshot?>(null) }

            fun reload() {
                snapshots = LauncherBackupTransfer.listSnapshots()
            }

            //下拉刷新重列文件（onRefresh 只能拿到这个回调）
            DisposableEffect(Unit) {
                LauncherBackupPage.reloader = { reload() }
                onDispose { LauncherBackupPage.reloader = null }
            }

            val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                coroutineScope.launch {
                    notice = resultText(c, LauncherBackupTransfer.importFile(c, uri))
                    reload()
                }
            }

            suspend fun backupNow() {
                notice = c.getString(R.string.launcher_backup_busy)
                notice = resultText(c, LauncherBackupTransfer.backup(c, LauncherSnapshotTag.MANUAL))
                reload()
            }

            suspend fun restoreNow(target: LauncherSnapshot) {
                notice = c.getString(R.string.launcher_backup_busy)
                val result = LauncherBackupTransfer.restore(c, File(backupDir, target.fileName))
                //恢复没有回传：下发成功只说明指令到了，结果由桌面 toast 给出
                notice = if (result is LauncherTaskResult.Success) {
                    c.getString(R.string.launcher_backup_result_sent)
                } else {
                    resultText(c, result)
                }
                reload()
            }

            Column(Modifier.fillMaxWidth()) {
                PrefCard(
                    title = stringResource(R.string.launcher_backup_status),
                    summary = when {
                        busy -> c.getString(R.string.launcher_backup_busy)
                        notice.isNotBlank() -> notice
                        launcherPackage == null -> c.getString(R.string.launcher_backup_no_launcher)
                        else -> c.getString(R.string.launcher_backup_ok_ready, launcherPackage)
                    },
                    modifier = Modifier.padding(horizontal = horizontal),
                    enabled = !busy,
                    onClick = { reload() },
                )
                PrefCard(
                    title = stringResource(R.string.launcher_backup_root),
                    summary = backupDir.absolutePath,
                    modifier = Modifier.padding(horizontal = horizontal, vertical = 12.dp),
                    enabled = !busy,
                    onClick = { reload() },
                )
                PrefCard(
                    title = stringResource(R.string.launcher_backup_backup_now),
                    summary = stringResource(R.string.launcher_backup_backup_now_summary),
                    modifier = Modifier.padding(horizontal = horizontal),
                    enabled = !busy && launcherPackage != null,
                    onClick = { coroutineScope.launch { backupNow() } },
                )
                PrefCard(
                    title = stringResource(R.string.launcher_backup_import),
                    summary = stringResource(R.string.launcher_backup_import_summary),
                    modifier = Modifier.padding(horizontal = horizontal, vertical = 12.dp),
                    enabled = !busy,
                    onClick = { importer.launch("*/*") },
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
                            enabled = !busy && launcherPackage != null,
                            onClick = { pendingRestore = snapshot },
                        )
                    }
                }
            }

            // 恢复必须二次确认：会先自动补拍一份 pre_restore，然后整表覆盖布局
            val target = pendingRestore
            if (target != null) {
                val message = stringResource(R.string.launcher_backup_restore_message, target.fileName)
                when (LocalUiMode.current) {
                    UiMode.Miuix -> OverlayDialog(
                        show = true,
                        onDismissRequest = { pendingRestore = null },
                    ) {
                        MiuixText(text = message, modifier = Modifier.fillMaxWidth())
                        MiuixTextButton(
                            text = stringResource(R.string.launcher_backup_restore_confirm),
                            onClick = {
                                pendingRestore = null
                                coroutineScope.launch { restoreNow(target) }
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
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingRestore = null
                                coroutineScope.launch { restoreNow(target) }
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

    /** 结果文案：成功只说完成与文件名，失败按 [LauncherTaskError] 取本地化说明（不拼英文异常） */
    private fun resultText(context: Context, result: LauncherTaskResult): String = when (result) {
        is LauncherTaskResult.Success ->
            context.getString(R.string.launcher_backup_result_ok) + " · " + result.file.name

        is LauncherTaskResult.Failure ->
            context.getString(R.string.launcher_backup_result_failed, errorText(context, result))
    }

    private fun errorText(context: Context, failure: LauncherTaskResult.Failure): String =
        when (failure.error) {
            LauncherTaskError.NO_LAUNCHER -> context.getString(R.string.launcher_backup_no_launcher)
            LauncherTaskError.CREATE_FAILED ->
                context.getString(R.string.launcher_backup_err_create, failure.detail)

            LauncherTaskError.FILE_EXISTS ->
                context.getString(R.string.launcher_backup_err_exists, failure.detail)

            LauncherTaskError.GRANT_FAILED ->
                context.getString(R.string.launcher_backup_err_grant, failure.detail)

            LauncherTaskError.SEND_FAILED -> context.getString(R.string.launcher_backup_send_failed)
            LauncherTaskError.TIMEOUT -> context.getString(R.string.launcher_backup_err_timeout)
            LauncherTaskError.EMPTY_FILE ->
                context.getString(R.string.launcher_backup_err_empty, failure.detail)

            LauncherTaskError.PRE_RESTORE_FAILED ->
                context.getString(R.string.launcher_backup_err_pre_restore, failure.detail)

            LauncherTaskError.INVALID_FILE ->
                context.getString(R.string.launcher_backup_err_invalid)

            LauncherTaskError.IO_ERROR ->
                context.getString(R.string.launcher_backup_err_io, failure.detail)
        }

    /** 备份行标题：时间 + 标签（文件名里的时间戳对用户不直观） */
    private fun snapshotTitle(context: Context, snapshot: LauncherSnapshot): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(snapshot.timeMillis))
        return stamp + " · " + tagLabel(context, snapshot.tag)
    }

    /** 备份行摘要：文件名与字节数 */
    private fun snapshotSummary(context: Context, snapshot: LauncherSnapshot): String {
        val size = if (snapshot.sizeBytes <= 0L) "-" else formatFileSize(snapshot.sizeBytes.toFloat())
        return snapshot.fileName + " · " + size
    }

    private fun tagLabel(context: Context, tag: LauncherSnapshotTag): String = when (tag) {
        LauncherSnapshotTag.AUTO -> context.getString(R.string.launcher_backup_tag_auto)
        LauncherSnapshotTag.MANUAL -> context.getString(R.string.launcher_backup_tag_manual)
        LauncherSnapshotTag.PRE_RESTORE -> context.getString(R.string.launcher_backup_tag_pre_restore)
    }

    private fun intervalLabel(context: Context, hours: Int): String = when (hours) {
        6 -> context.getString(R.string.launcher_backup_interval_6h)
        12 -> context.getString(R.string.launcher_backup_interval_12h)
        48 -> context.getString(R.string.launcher_backup_interval_48h)
        else -> context.getString(R.string.launcher_backup_interval_24h)
    }
}
