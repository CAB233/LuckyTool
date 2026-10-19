package com.luckyzyx.luckytool.hook.scopes.launcher

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.util.Base64
import androidx.core.net.toUri
import com.highcapable.betterandroid.ui.extension.component.lifecycleOwner
import com.highcapable.betterandroid.ui.extension.component.runDelayed
import com.highcapable.betterandroid.ui.extension.view.toast
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.resolver.MethodResolver
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.HiddenApp
import com.luckyzyx.luckytool.utils.LauncherBackupGate
import com.luckyzyx.luckytool.utils.LauncherBackupPrefs
import com.luckyzyx.luckytool.utils.LauncherBackupUtils
import com.luckyzyx.luckytool.utils.LauncherCommand
import com.luckyzyx.luckytool.utils.LauncherSnapshotTag
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SnapshotCandidate
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.lsparanoid.Obfuscate
import java.io.File
import java.util.concurrent.Executors

/**
 * 桌面布局备份/恢复（执行层在 launcher 进程里，用 launcher 自己的 uid，不走 root）
 *
 * 为什么放在 hook 侧：
 *  - launcher 数据在 device-encrypted 目录 /data/user_de/0/<pkg>/，模块 app 没有权限读写，
 *    而 hook 跑在 launcher 进程内，直接以 launcher uid 访问，无需 root。
 *  - 备份不直接拷 launcher.db 文件（同 uid 拷贝可能拿到写入中途的撕裂数据），
 *    改用 SQLiteDatabase 只读打开，把布局白名单表导出成 JSON（含 icon blob，全量约 1.2MB），写进 zip 容器。
 *  - 恢复写盘后必须自己调 LauncherModel.forceReload()：LauncherProvider.reloadLauncherIfExternal
 *    只在「调用方 pid != launcher pid」时生效，我们就在 launcher 进程里写，不会自动重载。
 *  - 隐藏应用状态不在数据库里，权威在进程内缓存/系统服务的 type_hide；恢复必须走
 *    DeepProtectedAppsManager.I(pkg, hidden, user) 这个官方入口，直接改 prefs xml 无效（进程内是缓存）。
 *
 * 通道（只有一条指令，数据走文件）：
 *  prefs launcher_backup_command = backup|<uri>|<tag>|<token> 或 restore|<uri>|<token>
 *  uri 是 app 侧 FileProvider 的 content uri，app 已 grantUriPermission 把读/写权限授予桌面包名。
 * 没有回传：结果只落桌面 toast + 日志，app 侧靠备份文件长度判断 hook 是否真的执行。
 */
@Obfuscate
object LauncherBackupHooker : YukiBaseHooker() {

    private const val TAG = "LauncherBackup"

    /** 单线程执行器：备份/恢复全部落盘动作都在这里，不占 launcher 主线程 */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, TAG).apply { isDaemon = true }
    }

    /** 上次处理过的指令 token（进程重启时从 prefs 读回，避免重放旧指令） */
    @Volatile
    private var lastToken: String? = null

    /**
     * launcher 进程的 Application（进程级单例，不会持有 Activity）。
     * 声明成 Application 而不是 Context：lint 的 StaticFieldLeak 只放过 Application 类型的静态字段，
     * 写 Context 会被判成静态上下文泄漏（这里实际存的就是 hostApplication）。
     */
    @Volatile
    private var hostContext: Application? = null

    @Volatile
    private var dataRoot: File? = null

    /** onHook 可能被重复调用，自查只装一次 */
    @Volatile
    private var selfCheckInstalled = false

    /** 等布局加载完成的轮询计数 */
    private var modelLoadTries = 0

    /** onHook 的 dataRoot / dataChannel 是否已就绪：挂载点可能早于 onHook 命中 */
    @Volatile
    private var mountReady = false

    /** 挂载点只装一次：重复 onHook 不再重复 hook */
    @Volatile
    private var mountInstalled = false

    /** 未就绪时挂载点记下的自查原因：onHook 就绪后补跑，不丢事件 */
    @Volatile
    private var pendingSelfCheckReason: String? = null

    override fun onHook() {
        //挂载点尽量早装：桌面 Application#onCreate 可能就在这次模块装载之后紧接着跑
        installProcessStartMountPoint()
        val context = hostApplication ?: return
        val root = resolveDataRoot(context, appInfo.packageName) ?: run {
            YLog.debug("$TAG 取不到 launcher 数据目录，功能不可用")
            return
        }
        hostContext = context
        dataRoot = root
        lastToken = LauncherBackupUtils.parseCommand(
            preferences(ModulePrefs).getString(LauncherBackupUtils.KEY_COMMAND, "")
        )?.token
        YLog.debug(TAG + " 初始化: pkg=" + appInfo.packageName + " dataRoot=" + root.absolutePath)
        dataChannel.wait<String>(LauncherBackupUtils.KEY_COMMAND) { raw ->
            handleCommand(raw)
        }
        installAutoBackupSelfCheck()
        mountReady = true
        //挂载点可能先于 onHook 命中（模块被延迟装载时），这里补跑一次，不丢事件
        pendingSelfCheckReason?.let { reason ->
            pendingSelfCheckReason = null
            requestSelfCheck(reason)
        }
    }

    // ---------------- 指令分发 ----------------

    private fun handleCommand(raw: String?) {
        val command = LauncherBackupUtils.parseCommand(raw)
        if (command == null) {
            YLog.debug("$TAG 忽略非法指令: $raw")
            return
        }
        if (LauncherBackupUtils.isDuplicateToken(lastToken, command.token)) {
            YLog.debug(TAG + " 重复指令，跳过: " + command.action)
            return
        }
        lastToken = command.token
        executor.execute {
            val outcome = runCatching { dispatch(command) }.getOrElse { throwable ->
                YLog.debug(TAG + " 指令执行异常: " + command.action, throwable)
                Outcome(false, throwable.message ?: throwable.javaClass.simpleName)
            }
            toastOutcome(command, outcome)
        }
    }

    private fun dispatch(command: LauncherCommand): Outcome {
        //tag 由指令里的第三段指定：手动备份传 manual，恢复前的补拍传 pre_restore
        //自动备份不走这条通道（桌面进程自己写文件），缺省按手动处理
        val tag = command.tag ?: LauncherSnapshotTag.MANUAL
        return when (command.action) {
            LauncherBackupUtils.ACTION_BACKUP -> backup(tag, command.uri)
            LauncherBackupUtils.ACTION_RESTORE -> restore(command.uri)
            else -> Outcome(false, "unknown action: " + command.action)
        }
    }

    /** 结果反馈只有桌面 toast（没有回传通道）；成功失败都要有一句中文提示 */
    private fun toastOutcome(command: LauncherCommand, outcome: Outcome) {
        val label = if (command.action == LauncherBackupUtils.ACTION_BACKUP) "备份" else "恢复"
        val text = when {
            outcome.ok -> "布局" + label + "完成" +
                    (if (outcome.message.isBlank()) "" else "（" + outcome.message + "）")

            else -> "布局" + label + "失败：" + outcome.message
        }
        toast(text)
    }

    private fun toast(text: String) {
        val context = hostContext ?: return
        YLog.debug("$TAG $text")
        runCatching {
            Handler(Looper.getMainLooper()).post {
                runCatching { context.toast(text) }
            }
        }
    }

    // ---------------- 进程启动挂载点 ----------------

    /**
     * 唯一的常驻挂载点：桌面自己的 Application#onCreate（com.android.common.LauncherApplication，
     * 见 OplusLauncher manifest 的 application android:name）。
     *
     * 为什么不用 android.app.Application#onCreate：那是框架类，任何 Application 装载都会命中，
     * 风险面覆盖整个框架启动链；这里只挂 ROM 的桌面 Application，这个类只存在于桌面包里，
     * 天然只在桌面进程命中，也不会牵扯模块自己。
     * 为什么 onHook 之外还要这个点：onHook 只在模块装载时跑一次，模块被延迟装载时自查可能一次都不跑，
     * 而桌面进程启动是必然经过的点。
     * 崩溃隔离分三层：解析/挂载/回调各自 runCatching，挂不上就退回 onHook 时机；回调体内不读库不写文件，
     * 只 post 一条消息；post 出去的任务自己再包一层。目标方法怎么抛、我们这层出什么问题，都只落一条日志，
     * 绝不向桌面进程抛出——Application#onCreate 抛异常会被系统判成崩溃并反复重启桌面。
     */
    private fun installProcessStartMountPoint() {
        if (mountInstalled) return
        mountInstalled = true
        runCatching {
            "com.android.common.LauncherApplication".toClass()
                .resolve()
                .firstMethodOrNull { name = "onCreate"; emptyParameters() }
                ?: error("no onCreate")
        }.onSuccess { method ->
            runCatching {
                method.hook {
                    after {
                        //回调里只允许这一句，且它自己也不许抛
                        runCatching { requestSelfCheck("LauncherApplication#onCreate") }
                            .onFailure { YLog.debug("$TAG 挂载点回调异常", it) }
                    }
                }
                YLog.debug("$TAG 挂载点命中: LauncherApplication#onCreate")
            }.onFailure { throwable ->
                YLog.debug("$TAG 挂载 LauncherApplication#onCreate 失败", throwable)
            }
        }.onFailure { throwable ->
            YLog.debug("$TAG 找不到 LauncherApplication#onCreate，只用 onHook 时机", throwable)
        }
    }

    /**
     * 自查统一入口：挂载点先到就记 pending，等 onHook 把 dataRoot / dataChannel 备齐再补跑。
     * 真正的自查挪到主线程下一轮消息，不占 Application#onCreate 的调用栈。
     */
    private fun requestSelfCheck(reason: String) {
        if (!mountReady) {
            pendingSelfCheckReason = reason
            return
        }
        Handler(Looper.getMainLooper()).post {
            YLog.debug("$TAG 挂载点触发自查: $reason")
            runCatching { enqueueAutoBackupSelfCheck() }
                .onFailure { YLog.debug("$TAG 挂载点自查入队失败", it) }
        }
    }

    // ---------------- 自动备份到期自查 ----------------

    /**
     * 在桌面进程里自查自动备份是否到期，onHook 里装一次。
     *
     * 为什么放在桌面侧：桌面进程几乎总在运行，而 app 进程平时不在，定时任务还会被 Doze / 电池优化压后；
     * 每轮自查的成本只是读一次宿主 prefs 加一次比较。
     * 为什么自己写文件：走 MediaStore.Downloads 投稿，不需要存储权限，也不必叫醒 app；
     * 真机实测 launcher 直接写 /storage/emulated/0/Download 是 Permission denied（uid 10238 没有
     * MANAGE_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE），所以不能走文件路径那条路。
     */
    private fun installAutoBackupSelfCheck() {
        if (selfCheckInstalled) return
        selfCheckInstalled = true
        modelLoadTries = 0
        hostContext?.lifecycleOwner?.runDelayed(MODEL_LOAD_POLL_MS) {
            modelLoadedPoll.run()
        }
    }

    /** 先等布局加载完成再自查：加载期间桌面正在读库建模型，别跟它抢 IO */
    private val modelLoadedPoll = object : Runnable {
        override fun run() {
            val loaded = isModelLoaded()
            if (!loaded && modelLoadTries < MODEL_LOAD_MAX_TRIES) {
                modelLoadTries++
                hostContext?.lifecycleOwner?.runDelayed(MODEL_LOAD_POLL_MS) {
                    run()
                }
                return
            }
            if (!loaded) YLog.debug("$TAG 等待布局加载超时，仍做一次到期自查")
            enqueueAutoBackupSelfCheck()
            hostContext?.lifecycleOwner?.runDelayed(selfCheckIntervalMs()) {
                selfCheckTick.run()
            }
        }
    }

    /** 固定间隔自我重排：每轮先判闸门再排下一轮（进程被杀排期自然消失，onHook 会重装） */
    private val selfCheckTick = object : Runnable {
        override fun run() {
            enqueueAutoBackupSelfCheck()
            hostContext?.lifecycleOwner?.runDelayed(selfCheckIntervalMs()) {
                run()
            }
        }
    }

    /** 自查要读库 + 写 1MB 级文件，全部丢到执行器上，不占桌面主线程 */
    private fun enqueueAutoBackupSelfCheck() {
        runCatching { executor.execute { runAutoBackupSelfCheck() } }
            .onFailure { YLog.debug("$TAG 提交自动备份自查失败", it) }
    }

    /** 到期判定：开关和周期从宿主 ModulePrefs 只读，时间戳只来自 [LauncherBackupGate] */
    private fun autoBackupDue(now: Long): Boolean {
        val context = hostContext ?: return false
        val prefs = preferences(ModulePrefs)
        if (!prefs.getBoolean(LauncherBackupPrefs.KEY_AUTO_ENABLED, false)) return false
        val hours = LauncherBackupPrefs.intervalHours(
            prefs.getString(
                LauncherBackupPrefs.KEY_AUTO_INTERVAL,
                LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS.toString(),
            )
        )
        return LauncherBackupGate.autoBackupDue(
            now = now,
            lastSuccess = LauncherBackupGate.readLastSuccess(context),
            lastAttempt = LauncherBackupGate.readLastAttempt(context),
            intervalHours = hours,
        )
    }

    /**
     * 到期就在桌面进程里自己写一份自动备份。
     * 先记尝试时间戳再动手：写失败（例如真机确实没有写公共目录的权限）时靠 30 分钟节流防抖，
     * 不会每一轮自查都重试；只有真的落盘成功才刷新「上次成功」。
     */
    private fun runAutoBackupSelfCheck() {
        runCatching {
            val context = hostContext ?: return
            val now = System.currentTimeMillis()
            if (!autoBackupDue(now)) {
                YLog.debug("$TAG 自动备份未到期或仍在节流窗口内，跳过本次")
                return
            }
            LauncherBackupGate.markAttempt(context, now)
            val outcome = autoBackup(now)
            if (outcome.ok) LauncherBackupGate.markSuccess(context, now)
            val text = if (outcome.ok) {
                "自动备份完成" + (if (outcome.message.isBlank()) "" else "（" + outcome.message + "）")
            } else {
                //失败必须带上异常原文：真机上看 toast / 日志就能判断是不是没写权限
                "自动备份失败：" + outcome.message
            }
            toast(text)
        }.onFailure { YLog.debug("$TAG 自动备份自查异常", it) }
    }

    /** 布局是否加载完成：ROM 里 LauncherModel.mModelLoaded 由 LoaderTransaction.commit() 置位 */
    private fun isModelLoaded(): Boolean = runCatching {
        val context = hostContext ?: return false
        val model = launcherAppState(context)?.asResolver()
            ?.optional(true)
            ?.firstMethodOrNull { name = "getModel"; superclass() }
            ?.invoke() ?: return false
        model.asResolver().optional(true)
            .firstMethodOrNull { name = "isModelLoaded"; superclass() }
            ?.invoke() as? Boolean ?: false
    }.getOrElse {
        YLog.debug(TAG + " 读取布局加载状态失败: " + it.message)
        false
    }

    /**
     * 桌面进程的 LauncherAppState.getInstance(context)：布局状态与模型的唯一入口。
     * getInstance / getModel 在部分 ROM 上声明在父类里，所以带上 superclass()。
     */
    private fun launcherAppState(context: Context): Any? = runCatching {
        LAUNCHER_APP_STATE.toClass(launcherClassLoader())
            .resolve()
            .optional(true)
            .firstMethodOrNull { name = "getInstance"; parameters(Context::class); superclass() }
            ?.invoke(context)
    }.getOrNull()

    /** 自查重排间隔：周期最短 6 小时，按周期排太稀疏，取 15~30 分钟的窗口（不低于 15 分钟） */
    private fun selfCheckIntervalMs(): Long {
        //prefs 在 onHook 里已经读过一次，这里只是内存读
        val hours = LauncherBackupPrefs.intervalHours(
            preferences(ModulePrefs).getString(
                LauncherBackupPrefs.KEY_AUTO_INTERVAL,
                LauncherBackupPrefs.DEFAULT_INTERVAL_HOURS.toString(),
            )
        )
        return (hours * 60L).coerceIn(SELF_CHECK_MIN_MINUTES, SELF_CHECK_MAX_MINUTES) * 60_000L
    }

    /** 等布局加载的轮询间隔与上限（约 1 分钟）：超时也照做一次自查，不能因为读不到状态就永不备份 */
    private const val MODEL_LOAD_POLL_MS = 2_000L
    private const val MODEL_LOAD_MAX_TRIES = 30

    /** 自查重排间隔的上下限（分钟）：周期最短 6 小时，按周期排太稀疏，取 15~30 分钟窗口 */
    private const val SELF_CHECK_MIN_MINUTES = 15L
    private const val SELF_CHECK_MAX_MINUTES = 30L

    // ---------------- 备份 ----------------

    /** 手动/恢复前补拍：内容来自共用序列化，落盘目标是 app 已经授权的 content uri */
    private fun backup(tag: LauncherSnapshotTag, uriText: String): Outcome {
        val payload = captureSnapshot(tag)
        if (payload.isFailure) {
            return Outcome(false, payload.exceptionOrNull()?.message ?: "snapshot failed")
        }
        val snapshot = payload.getOrThrow()
        if (!writeContainerTo(uriText, snapshot.metaJson, snapshot.layoutJson)) {
            return Outcome(false, "write failed: $uriText")
        }
        YLog.debug(TAG + " 备份完成 " + snapshot.summary)
        return Outcome(true, snapshot.summary)
    }

    /**
     * 自动备份：桌面进程内通过 MediaStore.Downloads 投稿写文件。
     * 为什么不写文件路径：launcher 的 manifest 只有 READ_EXTERNAL_STORAGE，
     * appops 里 MANAGE_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE 都是 ignore，
     * 真机实测直接写 /storage/emulated/0/Download 会 Permission denied；MediaStore 投稿不需要存储权限。
     * 写成功才由调用方记「上次成功」；失败把异常原文带回去，方便真机定位。
     */
    private fun autoBackup(now: Long): Outcome {
        val context = hostContext ?: return Outcome(false, "no context")
        val payload = captureSnapshot(LauncherSnapshotTag.AUTO, now)
        if (payload.isFailure) {
            return Outcome(false, payload.exceptionOrNull()?.message ?: "snapshot failed")
        }
        val snapshot = payload.getOrThrow()
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val displayName = LauncherBackupUtils.backupFileName(now, LauncherSnapshotTag.AUTO)
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            //RELATIVE_PATH 要以 / 结尾；目录由 RELATIVE_DIR 派生，避免两处写死
            put(MediaStore.MediaColumns.RELATIVE_PATH, LauncherBackupUtils.RELATIVE_DIR + "/")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = runCatching { resolver.insert(collection, pending) }.getOrElse { throwable ->
            YLog.debug("$TAG 自动备份投稿失败", throwable)
            return Outcome(
                false,
                "insert failed: " + (throwable.message ?: throwable.javaClass.simpleName)
            )
        } ?: return Outcome(false, "insert failed: $displayName")

        val written = runCatching {
            val bytes = LauncherBackupUtils.containerBytes(snapshot.metaJson, snapshot.layoutJson)
            resolver.openOutputStream(target)?.use { output -> output.write(bytes) }
                ?: error("openOutputStream returned null")
            //写完再解除 pending，否则别的应用会先看到半份文件
            val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(target, publish, null, null)
            //回读 SIZE：投稿成功但字节为 0 说明写流没真落盘，按失败处理
            mediaSize(resolver, target) > 0L
        }.getOrElse { throwable ->
            YLog.debug("$TAG 自动备份写流失败: $target", throwable)
            deleteMedia(resolver, target)
            return Outcome(
                false,
                displayName + ": " + (throwable.message ?: throwable.javaClass.simpleName)
            )
        }
        if (!written) {
            YLog.debug("$TAG 自动备份投稿后大小为 0: $target")
            deleteMedia(resolver, target)
            return Outcome(false, "投稿后大小为 0: $displayName")
        }
        YLog.debug(TAG + " 自动备份完成 " + displayName + " " + snapshot.summary)
        evictSnapshots(resolver, collection, displayName)
        return Outcome(true, snapshot.summary)
    }

    /** 回读 MediaStore 行的大小（字节）；读不到按 0 处理，由调用方判失败 */
    private fun mediaSize(resolver: ContentResolver, uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
    }.getOrDefault(0L)

    /** 清掉投稿失败的残行：IS_PENDING=1 的孤儿行用户看不到，但会一直占着配额 */
    private fun deleteMedia(resolver: ContentResolver, uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
            .onFailure { YLog.debug("$TAG 清理投稿残行失败: $uri", it) }
    }

    /** 快照容器内容：meta.json 放概要（行数/隐藏数），layout.json 放全量数据（含 icon blob） */
    private data class SnapshotPayload(
        val metaJson: String,
        val layoutJson: String,
        val summary: String,
    )

    /**
     * 抓一份布局快照（只读库 + 序列化，不落盘）：手动备份和自动备份共用，
     * 避免两条路径各自拼一遍容器格式。失败原因放在异常 message 里，由调用方拼提示。
     */
    private fun captureSnapshot(
        tag: LauncherSnapshotTag,
        time: Long = System.currentTimeMillis(),
    ): Result<SnapshotPayload> {
        val dbFile =
            launcherDb() ?: return Result.failure(IllegalStateException("launcher.db not found"))
        val rowCounts = LinkedHashMap<String, Int>()
        val tables = JSONObject()
        val database = openDatabase(dbFile, SQLiteDatabase.OPEN_READONLY)
            ?: return Result.failure(IllegalStateException("cannot open launcher.db"))
        try {
            val existing = withRetry { queryTableNames(database) }
            LauncherBackupUtils.LAYOUT_TABLES.forEach { table ->
                if (table !in existing) return@forEach
                val rows = withRetry { dumpTable(database, table) }
                tables.put(table, rows)
                rowCounts[table] = rows.length()
            }
        } catch (throwable: Throwable) {
            YLog.debug("$TAG 导出布局数据失败", throwable)
            return Result.failure(
                IllegalStateException(
                    "dump failed: " + (throwable.message ?: throwable.javaClass.simpleName)
                )
            )
        } finally {
            runCatching { database.close() }
        }

        // 隐藏应用状态：直接导出 prefs 里的 Int map（键 pkg#userId，值 rank 原样），不再拷 xml 文件
        val hidden = currentHiddenApps()
        val metaJson = LauncherBackupUtils.buildMetaJson(
            tag = tag,
            time = time,
            packageName = appInfo.packageName,
            model = Build.MODEL.orEmpty(),
            rom = Build.DISPLAY.orEmpty(),
            launcherMode = currentLauncherMode() ?: -1,
            tableRows = rowCounts,
            hiddenCount = hidden.size,
        )
        val layoutJson = LauncherBackupUtils.buildLayoutJson(tables, hidden)
        return Result.success(
            SnapshotPayload(
                metaJson,
                layoutJson,
                "hidden=" + hidden.size + " tables=" + tables.length()
            )
        )
    }

    /** Downloads 里的一行投稿：名字、时间戳、_id（删除按 id 走 MediaStore） */
    private data class MediaSnapshot(
        override val name: String,
        override val tag: LauncherSnapshotTag,
        override val timeMillis: Long,
        val id: Long,
    ) : SnapshotCandidate

    /**
     * 保留策略：只碰本工具投稿到 launcher_backup 目录的行（selection 限定 RELATIVE_PATH），
     * 淘汰规则与 app 侧共用 planEvictions；本次刚写的那份排除在外，单条删除失败只记日志。
     *
     * 候选还要再按 OWNER_PACKAGE_NAME 过滤：app 侧用 File API 写的快照行 owner 是 app 包名，
     * 桌面进程（宿主 uid）删别人的行只会抛 SecurityException，把它们算进候选会让上限永远收敛不了。
     */
    private fun evictSnapshots(resolver: ContentResolver, collection: Uri, keepName: String) {
        val hostPackage = hostContext?.packageName ?: return
        val relativePath = LauncherBackupUtils.RELATIVE_DIR + "/"
        var skippedForeign = 0
        val rows = runCatching {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            )
            val selection = MediaStore.MediaColumns.RELATIVE_PATH + "=?"
            val entries = ArrayList<MediaSnapshot>()
            resolver.query(collection, projection, selection, arrayOf(relativePath), null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val displayName = cursor.getString(1) ?: continue
                        //名字不符合约定的（用户自己放进来的文件）不参与淘汰
                        val parsed = LauncherBackupUtils.parseSnapshotName(displayName) ?: continue
                        //owner 不是宿主桌面进程的行删不掉（app 写的 manual / pre_restore），不参与淘汰
                        if (cursor.getString(2) != hostPackage) {
                            skippedForeign++
                            continue
                        }
                        //时间取文件名里的时间戳：与 app 侧淘汰口径一致，不依赖 DATE_ADDED 的秒级截断
                        entries += MediaSnapshot(parsed.name, parsed.tag, parsed.timeMillis, id)
                    }
                }
            entries
        }.getOrElse { throwable ->
            YLog.debug("$TAG 列出投稿文件失败", throwable)
            return
        }
        if (skippedForeign > 0) {
            YLog.debug("$TAG 跳过 $skippedForeign 份非本进程投稿的旧快照（owner 不是 $hostPackage，删不掉）")
        }
        val idByName = rows.associate { it.name to it.id }
        val excluded =
            LauncherBackupUtils.parseSnapshotName(keepName)?.name?.let { setOf(it) } ?: emptySet()
        LauncherBackupUtils.planEvictions(rows, LauncherBackupUtils.MAX_SNAPSHOTS, excluded)
            .forEach { candidate ->
                val id = idByName[candidate.name] ?: return@forEach
                val removed = runCatching {
                    resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
                }.getOrDefault(0) > 0
                if (!removed) YLog.debug(TAG + " 删除旧投稿失败: " + candidate.fileName)
            }
    }

    /**
     * 把 zip 容器写到 app 给的 content uri（"wt" = 覆盖写，provider 由 app 侧 FileProvider 提供）。
     * 只有 app 已 grantUriPermission 才能打开；失败返回 false，由调用方拼提示。
     */
    private fun writeContainerTo(uriText: String, metaJson: String, layoutJson: String): Boolean =
        runCatching {
            val context = hostContext ?: error("no context")
            val uri = uriText.toUri()
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("openOutputStream returned null")
            stream.use { output ->
                LauncherBackupUtils.writeContainer(
                    output,
                    metaJson,
                    layoutJson
                )
            }
            true
        }.getOrElse { throwable ->
            YLog.debug("$TAG 写备份文件失败", throwable)
            false
        }

    // ---------------- 恢复 ----------------

    private fun restore(uriText: String): Outcome {
        val entries = readContainerFrom(uriText) ?: return Outcome(false, "备份文件无法打开")
        //两个条目缺一不可：缺了就明确说是缺哪个，不要含糊报「格式错误」
        val missing = LauncherBackupUtils.missingEntries(entries)
        if (missing.isNotEmpty()) {
            return Outcome(false, "备份文件不完整：缺少 " + missing.joinToString(", "))
        }
        val payload = LauncherBackupUtils.parseContainer(entries)
            ?: return Outcome(
                false,
                "备份文件格式不支持或已损坏（version=" + LauncherBackupUtils.PAYLOAD_VERSION + "）",
            )
        val dbFile = launcherDb() ?: return Outcome(false, "launcher.db not found")
        val database = openDatabase(dbFile, SQLiteDatabase.OPEN_READWRITE)
            ?: return Outcome(false, "cannot open launcher.db for write")
        try {
            withRetry {
                database.beginTransaction()
                try {
                    val existing = queryTableNames(database)
                    LauncherBackupUtils.LAYOUT_TABLES.forEach { table ->
                        val rows = payload.tables.optJSONArray(table) ?: return@forEach
                        if (table !in existing) {
                            YLog.debug("$TAG 当前库没有表，跳过: $table")
                            return@forEach
                        }
                        restoreTable(database, table, rows)
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            }
        } catch (throwable: Throwable) {
            YLog.debug("$TAG 写回布局数据失败", throwable)
            return Outcome(
                false,
                "restore db failed: " + (throwable.message ?: throwable.javaClass.simpleName)
            )
        } finally {
            runCatching { database.close() }
        }

        // 隐藏应用状态：必须走 DeepProtectedAppsManager.I() 官方入口（直接改 xml 无效，进程内是缓存）
        val hiddenResult = syncHiddenApps(payload.hidden)

        // 让桌面重新读库（同进程写库不会触发 provider 的自动重载）
        val reloadResult = forceReloadLayout()

        // 库已经写回成功，但桌面没重载 → 不能报成功：否则 toast 说完成而图标根本没变
        return if (reloadResult == null) {
            Outcome(true, "$hiddenResult; reload=ok")
        } else {
            Outcome(
                false,
                "restore ok but reload failed: $reloadResult; launcher restart may be required"
            )
        }
    }

    /** 读 app 给的 content uri 并解出 zip 条目；任何异常都当「读不出来」，由调用方统一报错 */
    private fun readContainerFrom(uriText: String): Map<String, String>? = runCatching {
        val context = hostContext ?: error("no context")
        val uri = uriText.toUri()
        val stream =
            context.contentResolver.openInputStream(uri) ?: error("openInputStream returned null")
        stream.use { input -> LauncherBackupUtils.readContainer(input) }
    }.getOrElse { throwable ->
        YLog.debug("$TAG 读备份文件失败", throwable)
        null
    }

    // ---------------- 隐藏应用状态同步 ----------------

    /**
     * 备份里的隐藏列表与当前状态做双向差集，只对差异项调 I(pkg, hidden, user)。
     * I() 会静默早退，所以调完必须回读校验，仍不一致的再走存储层兜底；最后汇总 ok/fail/unchanged 条数。
     */
    private fun syncHiddenApps(snapshot: Map<String, Int>): String {
        val current = currentHiddenApps()
        val sync = LauncherBackupUtils.planHiddenSync(snapshot, current)
        if (sync.toHide.isEmpty() && sync.toUnhide.isEmpty()) return "hidden=unchanged"

        val context = hostContext ?: return "hidden=skip(no context)"
        val manager = runCatching {
            DEEP_PROTECTED_MANAGER.toClass(launcherClassLoader())
                .resolve()
                .optional(true)
                .firstMethodOrNull { name = "k"; parameters(Context::class); superclass() }
                ?.invoke(context)
        }.getOrNull()
        //I(String, boolean, UserHandle)：布尔参数按原始类型 boolean 找，跟 getMethod 时的语义一致
        val method = manager?.asResolver()
            ?.optional(true)
            ?.firstMethodOrNull {
                name = "I"
                parameters(String::class, java.lang.Boolean.TYPE, UserHandle::class)
                superclass()
            }
        if (manager == null || method == null) {
            YLog.debug("$TAG 取不到 DeepProtectedAppsManager.I，隐藏应用状态未同步")
            return "hidden=skip(no entry)"
        }

        // I() 内部有两处早退（:1210 不在 launchAppList / :1224 不需要取消隐藏），invoke 不抛异常但什么都没做。
        // 所以必须回读 app_hidden_prefs 校验，仍不一致的再走存储层 HiddenDataCollection，否则会报假成功。
        val plan = sync.toHide.map { it to true } + sync.toUnhide.map { it to false }
        plan.forEach { (app, hidden) ->
            runCatching { method.invoke(app.packageName, hidden, userHandle(app.userId)) }
                .onFailure {
                    YLog.debug(
                        TAG + " I() 调用异常: " + app.packageName + "#" + app.userId,
                        it
                    )
                }
        }
        var remaining = LauncherBackupUtils.planHiddenSync(snapshot, currentHiddenApps())
        var failed = 0
        if (remaining.toHide.isNotEmpty() || remaining.toUnhide.isNotEmpty()) {
            val fallback =
                remaining.toHide.map { it to true } + remaining.toUnhide.map { it to false }
            YLog.debug(TAG + " I() 未生效 " + fallback.size + " 条，回退存储层 HiddenDataCollection")
            fallback.forEach { (app, hidden) ->
                if (!applyStorageFallback(manager, app, hidden)) failed++
            }
            remaining = LauncherBackupUtils.planHiddenSync(snapshot, currentHiddenApps())
        }
        val unchanged = remaining.toHide.size + remaining.toUnhide.size
        return "hidden ok=" + (plan.size - unchanged) + " fail=" + failed + " unchanged=" + unchanged
    }

    /**
     * 存储层兜底：DPAM 里的 HiddenDataCollection 声明了 (String, UserHandle) -> void 的 add/remove 两个方法，
     * I() 内部走的就是它们。按签名找、不按名字找（真机上字段/方法名都混淆过）。
     * 每次调用后回读实际状态，命中即停；两个方法都试过仍不对才算失败。
     */
    private fun applyStorageFallback(manager: Any, app: HiddenApp, hidden: Boolean): Boolean {
        val holders = hiddenDataHolders(manager)
        val user = userHandle(app.userId)
        holders.forEach { method ->
            runCatching { method.invoke(app.packageName, user) }
            if (isHiddenNow(app) == hidden) return true
        }
        return isHiddenNow(app) == hidden
    }

    private fun isHiddenNow(app: HiddenApp): Boolean =
        currentHiddenApps().containsKey(LauncherBackupUtils.hiddenKey(app.packageName, app.userId))

    /** 从 DPAM 实例的字段里取出持有隐藏状态缓存 + prefs 的 HiddenDataCollection 及其变更方法 */
    private fun hiddenDataHolders(manager: Any): List<MethodResolver<*>> = runCatching {
        manager.asResolver().optional(true).field { }.flatMap { field ->
            val holder = field.get() ?: return@flatMap emptyList()
            holder.asResolver().optional(true).method {
                parameterCount = 2
                parameters(String::class, UserHandle::class)
                returnType = Void.TYPE
            }
        }
    }.getOrDefault(emptyList())

    /** 用户句柄：UserHandle.of(int) 在部分 SDK 上不可直接引用，反射取；拿不到退回当前用户（真机 profile 基本都是 0） */
    private fun userHandle(userId: Int): UserHandle = runCatching {
        UserHandle::class.resolve()
            .optional(true)
            .firstMethodOrNull { name = "of"; parameters(java.lang.Integer.TYPE) }
            ?.invoke(userId) as? UserHandle
    }.getOrNull() ?: Process.myUserHandle()

    /** 当前隐藏状态：优先读进程内 prefs 缓存（DPAM 用的就是同一份），失败再解析磁盘 xml */
    private fun currentHiddenApps(): Map<String, Int> {
        val context = hostContext
        if (context != null) {
            val live = runCatching {
                context.getSharedPreferences(HIDDEN_PREFS_NAME, Context.MODE_PRIVATE).all
                    .mapNotNull { entry -> (entry.value as? Int)?.let { entry.key to it } }
                    .toMap()
            }.getOrNull()
            if (live != null) return live
        }
        val root = dataRoot ?: return emptyMap()
        val file = File(File(root, "shared_prefs"), HIDDEN_PREFS_FILE)
        return LauncherBackupUtils.parseHiddenPrefsXml(runCatching { file.readText() }.getOrNull())
    }

    // ---------------- 桌面重载 ----------------

    /**
     * 反射 LauncherAppState.getInstance(context).getModel().forceReload()。
     * 不抛异常：失败只返回原因，由调用方汇总到提示里。
     */
    private fun forceReloadLayout(): String? {
        val context = hostContext ?: return "no context"
        return runCatching {
            val appState = launcherAppState(context) ?: error("no LauncherAppState")
            val model = appState.asResolver().optional(true)
                .firstMethodOrNull { name = "getModel"; superclass() }
                ?.invoke() ?: error("no LauncherModel")
            model.asResolver().optional(true)
                .firstMethodOrNull { name = "forceReload"; superclass() }
                ?.invoke()
            null
        }.getOrElse { throwable ->
            YLog.debug("$TAG forceReload 失败", throwable)
            throwable.message ?: throwable.javaClass.simpleName
        }
    }

    /** 当前桌面模式（Standard 0 / Drawer 2 / Simple 3）；取不到返回 null */
    private fun currentLauncherMode(): Int? = runCatching {
        LAUNCHER_MODE_MANAGER.toClass(launcherClassLoader())
            .resolve()
            .optional(true)
            .firstMethodOrNull { name = "getInstance"; emptyParameters() }
            ?.invoke()
            ?.asResolver()
            ?.optional(true)
            ?.firstMethodOrNull { name = "getCurType"; superclass() }
            ?.invoke() as? Int
    }.getOrElse {
        YLog.debug("$TAG 读取 launcher_mode 失败", it)
        null
    }

    /** 宿主（launcher）的类加载器：YukiHookAPI 提供 hostClassLoader（appClassLoader 已废弃）；兜底用 hook 类自己的 */
    private fun launcherClassLoader(): ClassLoader =
        runCatching { hostClassLoader }.getOrNull() ?: javaClass.classLoader

    private const val HIDDEN_PREFS_NAME = "app_hidden_prefs"
    private const val HIDDEN_PREFS_FILE = "app_hidden_prefs.xml"
    private const val DEEP_PROTECTED_MANAGER =
        "com.android.launcher.filter.DeepProtectedAppsManager"
    private const val LAUNCHER_APP_STATE = "com.android.launcher3.LauncherAppState"
    private const val LAUNCHER_MODE_MANAGER = "com.android.launcher.mode.LauncherModeManager"

    /**
     * 单表写回：DELETE 全表后按备份行逐条 INSERT。
     * 插入列取「备份列 交 当前 PRAGMA table_info 列」，避免 launcher 版本差异导致列不匹配。
     * _id 也照原样显式写入：布局表之间有 id 引用（文件夹 card_host_id 等），重排 id 会把引用指错。
     */
    private fun restoreTable(database: SQLiteDatabase, table: String, rows: JSONArray) {
        val currentColumns = queryColumns(database, table)
        database.execSQL("DELETE FROM " + quote(table))
        if (rows.length() == 0 || currentColumns.isEmpty()) return
        val snapshotColumns = runCatching { rows.getJSONObject(0).keys().asSequence().toList() }
            .getOrDefault(emptyList())
        val columns = snapshotColumns.filter { it in currentColumns }
        if (columns.isEmpty()) {
            YLog.debug("$TAG 备份与当前库没有共同列，跳过: $table")
            return
        }
        val quotedColumns = columns.joinToString(", ") { quote(it) }
        val placeholders = columns.joinToString(", ") { "?" }
        val sql =
            "INSERT INTO " + quote(table) + " (" + quotedColumns + ") VALUES (" + placeholders + ")"
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val args = columns.map { column -> toBindArg(row.opt(column)) }.toTypedArray()
            database.execSQL(sql, args)
        }
        YLog.debug(TAG + " 写回表 " + table + " 行数 " + rows.length())
    }

    private fun quote(name: String): String = "\"" + name + "\""

    /** JSON 值 → SQLite 绑定参数（byte[] 用 base64 包裹的对象保存） */
    private fun toBindArg(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> runCatching {
            Base64.decode(value.optString("blob"), Base64.NO_WRAP)
        }.getOrNull()

        is JSONArray -> value.toString()
        else -> value
    }

    private data class Outcome(val ok: Boolean, val message: String)

    // ---------------- 数据库工具 ----------------

    /** launcher 的布局库；取不到返回 null（调用方报错） */
    private fun launcherDb(): File? {
        val root = dataRoot ?: return null
        val file = File(File(root, "databases"), "launcher.db")
        return file.takeIf { it.isFile }
    }

    /** 打开库；被占用时短重试（SQLITE_BUSY / database is locked） */
    private fun openDatabase(file: File, mode: Int): SQLiteDatabase? =
        runCatching {
            withRetry { SQLiteDatabase.openDatabase(file.absolutePath, null, mode) }
        }.getOrNull()

    private fun <T> withRetry(attempts: Int = 3, block: () -> T): T {
        var last: Throwable? = null
        repeat(attempts) { index ->
            try {
                return block()
            } catch (throwable: Throwable) {
                val message = throwable.message.orEmpty()
                val retryable = message.contains("locked", true) || message.contains("busy", true)
                if (!retryable) throw throwable
                last = throwable
                YLog.debug(TAG + " 数据库忙，重试 " + (index + 1) + ": " + message)
                runCatching { Thread.sleep(300L * (index + 1)) }
            }
        }
        throw last ?: IllegalStateException("retry exhausted")
    }

    private fun queryTableNames(database: SQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { names.add(it) }
        }
        return names
    }

    private fun queryColumns(database: SQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        database.rawQuery("PRAGMA table_info(" + quote(table) + ")", null).use { cursor ->
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { columns.add(it) }
            }
        }
        return columns
    }

    private fun dumpTable(database: SQLiteDatabase, table: String): JSONArray {
        val rows = JSONArray()
        database.rawQuery("SELECT * FROM " + quote(table), null).use { cursor ->
            val columns = cursor.columnNames
            while (cursor.moveToNext()) {
                val row = JSONObject()
                columns.forEachIndexed { index, column ->
                    row.put(column, readValue(cursor, index))
                }
                rows.put(row)
            }
        }
        return rows
    }

    /** 游标值 → JSON 值（BLOB 用 base64 包裹对象保存，恢复时还原成 byte[]） */
    private fun readValue(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(
            "blob", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)
        )

        else -> cursor.getString(index) ?: JSONObject.NULL
    }

    // ---------------- 数据目录 ----------------

    /**
     * launcher 数据根：判定标准只有一个 —— <dir>/databases/launcher.db 是文件。
     * 只判目录存在会命中 CE 目录（/data/user/0/<pkg> 也可能存在）或只建了一半的目录，
     * 随后 backup 才会报 "launcher.db not found"，属于静默选错。
     * 顺序：Context.getDataDir()（实测 device-encrypted /data/user_de/0/<pkg>）→ 同路径兜底 → 老 ColorOS 的 /data/data/<pkg>。
     */
    @SuppressLint("SdCardPath")
    private fun resolveDataRoot(context: Context, packageName: String): File? {
        val candidates = listOfNotNull(
            runCatching { context.dataDir }.getOrNull(),
            File("/data/user_de/0/$packageName"),
            File("/data/user/0/$packageName"),
            File("/data/data/$packageName"),
        )
        return candidates.firstOrNull { dir ->
            runCatching { File(dir, "databases/launcher.db").isFile }.getOrDefault(false)
        }
    }
}
