package com.luckyzyx.luckytool.hook.scopes.launcher

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.util.Base64
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.BuildConfig
import com.luckyzyx.luckytool.utils.HiddenApp
import com.luckyzyx.luckytool.utils.LauncherBackupUtils
import com.luckyzyx.luckytool.utils.LauncherCommand
import com.luckyzyx.luckytool.utils.LauncherSnapshot
import com.luckyzyx.luckytool.utils.LauncherSnapshotTag
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.lsparanoid.Obfuscate
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * 桌面布局备份/恢复（执行层在 launcher 进程里，用 launcher 自己的 uid，不走 root）
 *
 * 为什么放在 hook 侧：
 *  - launcher 数据在 device-encrypted 目录 /data/user_de/0/<pkg>/，模块 app 没有权限读写，
 *    而 hook 跑在 launcher 进程内，直接以 launcher uid 访问，无需 root。
 *  - 备份不直接拷 launcher.db 文件（同 uid 拷贝可能拿到写入中途的撕裂数据），
 *    改用 SQLiteDatabase 只读打开，把布局白名单表导出成 JSON。
 *  - 恢复写盘后必须自己调 LauncherModel.forceReload()：LauncherProvider.reloadLauncherIfExternal
 *    只在「调用方 pid != launcher pid」时生效，我们就在 launcher 进程里写，不会自动重载。
 *    forceReload() 内部会 cancelLastLoader 后再 startLoader，真正重新 query 数据库（不杀进程）。
 *  - 隐藏应用状态不在数据库里，权威在系统服务的 type_hide 列表；恢复必须走
 *    DeepProtectedAppsManager.I(pkg, hidden, user) 这个官方入口，直接改 prefs xml 无效（进程内是缓存）。
 *
 * 指令协议（app 写 ModulePrefs，hook 用 dataChannel 监听）：
 *  launcher_backup_command = <action>|<arg>|<token>，action 属于 backup/restore/list/delete，
 *  token 用于去重（同一条指令只执行一次）。
 * 结果回传：显式广播 <applicationId>.LAUNCHER_BACKUP_RESULT（extra: payload=JSON）。
 */
@Obfuscate
object LauncherBackupHooker : YukiBaseHooker() {

    private const val TAG = "LuckyTool_LauncherBackup"

    /** 单线程执行器：备份/恢复全部落盘动作都在这里，不占 launcher 主线程 */
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LuckyTool-LauncherBackup").apply { isDaemon = true }
    }

    /** 上次处理过的指令 token（进程重启时从 prefs 读回，避免重放旧指令） */
    @Volatile
    private var lastToken: String? = null

    @Volatile
    private var hostContext: Context? = null

    @Volatile
    private var dataRoot: File? = null

    @Volatile
    private var backupRoot: File? = null

    override fun onHook() {
        val context = hostApplication ?: return
        val packageName = appInfo.packageName
        val root = resolveDataRoot(context, packageName) ?: run {
            YLog.debug(TAG + " 取不到 launcher 数据目录，功能不可用")
            return
        }
        hostContext = context
        dataRoot = root
        backupRoot = File(root, LauncherBackupUtils.BACKUP_DIR_NAME)
        lastToken = LauncherBackupUtils.parseCommand(
            preferences(ModulePrefs).getString(LauncherBackupUtils.KEY_COMMAND, "")
        )?.token
        YLog.debug(TAG + " 初始化: pkg=" + packageName + " dataRoot=" + root.absolutePath)
        dataChannel.wait<String>(LauncherBackupUtils.KEY_COMMAND) { raw ->
            handleCommand(raw)
        }
    }

    // ---------------- 指令分发 ----------------

    private fun handleCommand(raw: String?) {
        val command = LauncherBackupUtils.parseCommand(raw)
        if (command == null) {
            YLog.debug(TAG + " 忽略非法指令: " + raw)
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
                Outcome(false, throwable.message ?: throwable.javaClass.simpleName, command.arg)
            }
            // 恢复后的重载由 restore() 内部调 forceReload() 完成，这里不杀进程
            sendResult(command, outcome)
        }
    }

    private fun dispatch(command: LauncherCommand): Outcome = when (command.action) {
        //tag 由指令 arg 指定：自动备份传 auto，手动传 manual
        LauncherBackupUtils.ACTION_BACKUP ->
            backup(LauncherSnapshotTag.ofId(command.arg) ?: LauncherSnapshotTag.MANUAL)
        LauncherBackupUtils.ACTION_LIST -> Outcome(true, "list ok", "")
        LauncherBackupUtils.ACTION_RESTORE -> restore(command.arg)
        LauncherBackupUtils.ACTION_DELETE -> delete(command.arg)
        else -> Outcome(false, "unknown action", "")
    }

    // ---------------- 备份 ----------------

    private fun backup(tag: LauncherSnapshotTag, protected: String? = null): Outcome {
        val root = backupRoot ?: return Outcome(false, "launcher data dir unavailable", "")
        val dbFile = File(File(dataRoot, "databases"), "launcher.db")
        if (!dbFile.isFile) return Outcome(false, "launcher.db not found: " + dbFile.absolutePath, "")
        val name = LauncherBackupUtils.snapshotName(System.currentTimeMillis(), tag)
        val dir = File(root, name)
        if (dir.exists()) return Outcome(false, "snapshot already exists: " + name, "")
        if (!dir.mkdirs()) return Outcome(false, "cannot create snapshot dir: " + dir.absolutePath, "")

        val rowCounts = JSONObject()
        val database = openDatabase(dbFile, SQLiteDatabase.OPEN_READONLY)
            ?: return failAndClean(dir, "cannot open launcher.db")
        try {
            val tables = JSONObject()
            val existing = withRetry { queryTableNames(database) }
            LauncherBackupUtils.LAYOUT_TABLES.forEach { table ->
                if (table !in existing) return@forEach
                val rows = withRetry { dumpTable(database, table) }
                tables.put(table, rows)
                rowCounts.put(table, rows.length())
            }
            File(dir, LauncherBackupUtils.FILE_LAYOUT).writeText(
                JSONObject().apply {
                    put("version", 1)
                    put("package", appInfo.packageName)
                    put("time", System.currentTimeMillis())
                    put("tables", tables)
                }.toString()
            )
        } catch (throwable: Throwable) {
            YLog.debug(TAG + " 导出布局数据失败", throwable)
            return failAndClean(dir, "dump failed: " + (throwable.message ?: throwable.javaClass.simpleName))
        } finally {
            runCatching { database.close() }
        }

        // 隐藏应用状态：按原文拷贝这两个 prefs 文件
        val prefsCopied = JSONArray()
        LauncherBackupUtils.PREFS_FILES.forEach { fileName ->
            val source = File(File(dataRoot, "shared_prefs"), fileName)
            if (!source.isFile) return@forEach
            runCatching {
                source.copyTo(File(dir, fileName), overwrite = true)
                prefsCopied.put(fileName)
            }.onFailure { YLog.debug(TAG + " 拷贝 prefs 失败: " + fileName, it) }
        }

        // 隐藏应用条目数（值本身是 rank，只统计条数用于界面提示；不归一化、不改写）
        val hiddenCount = LauncherBackupUtils.parseHiddenPrefsXml(
            runCatching { File(dir, HIDDEN_PREFS_FILE).readText() }.getOrNull()
        ).size

        runCatching {
            File(dir, LauncherBackupUtils.FILE_META).writeText(
                JSONObject().apply {
                    put("version", 1)
                    put("name", name)
                    put("tag", tag.id)
                    put("time", System.currentTimeMillis())
                    put("package", appInfo.packageName)
                    put("model", Build.MODEL)
                    put("rom", Build.DISPLAY)
                    // 当前桌面模式（Standard 0 / Drawer 2 / Simple 3）：三套表一起备份，但要记下用户当时用哪套
                    put("launcherMode", currentLauncherMode() ?: -1)
                    put("hidden", hiddenCount)
                    put("rows", rowCounts)
                    put("prefs", prefsCopied)
                }.toString()
            )
        }

        // 恢复流程里的补拍会传 protected = 目标快照名：它可能正好是最旧的 auto，绝不能被保留策略删掉
        evict(root, protected)
        return Outcome(true, "backup ok", name)
    }

    // ---------------- 恢复 ----------------

    private fun restore(snapshotArg: String): Outcome {
        val root = backupRoot ?: return Outcome(false, "launcher data dir unavailable", snapshotArg)
        val snapshot = LauncherBackupUtils.parseSnapshotName(snapshotArg)
            ?: return Outcome(false, "invalid snapshot name: " + snapshotArg, snapshotArg)
        val dir = File(root, snapshot.name)
        if (!dir.isDirectory) return Outcome(false, "snapshot not found: " + snapshot.name, snapshotArg)

        // 1) 先补拍一份 pre_restore，保证任何恢复都有回滚点；补拍失败直接中止
        val preRestore = backup(LauncherSnapshotTag.PRE_RESTORE, protected = snapshot.name)
        if (!preRestore.ok) return Outcome(false, "pre_restore failed: " + preRestore.message, snapshotArg)

        val layoutFile = File(dir, LauncherBackupUtils.FILE_LAYOUT)
        if (!layoutFile.isFile || layoutFile.length() <= 0L) {
            return Outcome(false, "layout.json missing in " + snapshot.name, snapshotArg)
        }
        val payload = runCatching { JSONObject(layoutFile.readText()) }.getOrNull()
            ?: return Outcome(false, "layout.json unreadable", snapshotArg)
        val tables = payload.optJSONObject("tables")
            ?: return Outcome(false, "layout.json has no tables", snapshotArg)

        val dbFile = File(File(dataRoot, "databases"), "launcher.db")
        if (!dbFile.isFile) return Outcome(false, "launcher.db not found", snapshotArg)
        val database = openDatabase(dbFile, SQLiteDatabase.OPEN_READWRITE)
            ?: return Outcome(false, "cannot open launcher.db for write", snapshotArg)
        try {
            // 2) 单事务写回：只动白名单表，且只写「快照列 交 当前列」，兼容版本差异
            withRetry {
                database.beginTransaction()
                try {
                    val existing = queryTableNames(database)
                    LauncherBackupUtils.LAYOUT_TABLES.forEach { table ->
                        val rows = tables.optJSONArray(table) ?: return@forEach
                        if (table !in existing) {
                            YLog.debug(TAG + " 当前库没有表，跳过: " + table)
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
            YLog.debug(TAG + " 写回布局数据失败", throwable)
            return Outcome(false, "restore db failed: " + (throwable.message ?: throwable.javaClass.simpleName), snapshotArg)
        } finally {
            runCatching { database.close() }
        }

        // 3) 隐藏应用状态：必须走 DeepProtectedAppsManager.I() 官方入口，直接改 xml 无效（进程内是缓存）
        //    app_hidden_settings_prefs.xml 在 OplusLauncher 里零命中，仅「存在才同名覆盖」，不做任何语义依赖
        val settingsFile = File(dir, HIDDEN_SETTINGS_FILE)
        if (settingsFile.isFile) {
            runCatching {
                settingsFile.copyTo(File(File(dataRoot, "shared_prefs"), HIDDEN_SETTINGS_FILE), overwrite = true)
            }.onFailure { YLog.debug(TAG + " 覆盖 prefs 失败: " + HIDDEN_SETTINGS_FILE, it) }
        }
        val hiddenResult = syncHiddenApps(dir)

        // 4) 让桌面重新读库（同进程写库不会触发 provider 的自动重载）
        val reloadResult = forceReloadLayout()

        // 库已经写回成功，但桌面没重载 → 不能报成功：否则界面显示「已完成」而图标根本没变
        return if (reloadResult == null) {
            Outcome(true, "restore ok; " + hiddenResult + "; reload=ok", snapshot.name)
        } else {
            Outcome(
                false,
                "restore ok but reload failed: " + reloadResult + "; launcher restart may be required",
                snapshot.name,
            )
        }
    }

    // ---------------- 隐藏应用状态同步 ----------------

    /**
     * 快照 xml 与当前隐藏状态做双向差集，只对差异项调 I(pkg, hidden, user)。
     * I() 会静默早退，所以调完必须回读校验，仍不一致的再走存储层兜底；最后汇总 ok/fail/unchanged 条数。
     * I() 完全取不到时回退「原样写回快照 xml（rank 不改写）」并提示需要重启桌面。
     */
    private fun syncHiddenApps(dir: File): String {
        val snapshotFile = File(dir, HIDDEN_PREFS_FILE)
        if (!snapshotFile.isFile) return "hidden=skip(no file)"
        val snapshot = LauncherBackupUtils.parseHiddenPrefsXml(
            runCatching { snapshotFile.readText() }.getOrNull()
        )
        val current = currentHiddenApps()
        val sync = LauncherBackupUtils.planHiddenSync(snapshot, current)
        if (sync.toHide.isEmpty() && sync.toUnhide.isEmpty()) return "hidden=unchanged"

        val context = hostContext ?: return "hidden=skip(no context)"
        val manager = runCatching {
            val clazz = Class.forName(DEEP_PROTECTED_MANAGER, false, launcherClassLoader())
            clazz.getMethod("k", Context::class.java).invoke(null, context)
        }.getOrNull()
        val method = manager?.let { instance ->
            runCatching {
                instance.javaClass.getMethod(
                    "I", String::class.java, Boolean::class.javaPrimitiveType, UserHandle::class.java
                )
            }.getOrNull()
        }
        if (manager == null || method == null) {
            YLog.debug(TAG + " 取不到 DeepProtectedAppsManager.I，回退为原样写回 xml")
            val target = File(File(dataRoot, "shared_prefs"), HIDDEN_PREFS_FILE)
            val copied = runCatching { snapshotFile.copyTo(target, overwrite = true) }.isSuccess
            return if (copied) {
                "hidden=file-copy(needs launcher restart)"
            } else {
                "hidden=file-copy-failed"
            }
        }

        // I() 内部有两处早退（:1210 不在 launchAppList / :1224 不需要取消隐藏），invoke 不抛异常但什么都没做。
        // 所以必须回读 app_hidden_prefs 校验，仍不一致的再走存储层 HiddenDataCollection，否则会报假成功。
        val plan = sync.toHide.map { it to true } + sync.toUnhide.map { it to false }
        plan.forEach { (app, hidden) ->
            runCatching { method.invoke(manager, app.packageName, hidden, userHandle(app.userId)) }
                .onFailure { YLog.debug(TAG + " I() 调用异常: " + app.packageName + "#" + app.userId, it) }
        }
        var remaining = LauncherBackupUtils.planHiddenSync(snapshot, currentHiddenApps())
        var failed = 0
        if (remaining.toHide.isNotEmpty() || remaining.toUnhide.isNotEmpty()) {
            val fallback = remaining.toHide.map { it to true } + remaining.toUnhide.map { it to false }
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
        holders.forEach { (holder, methods) ->
            methods.forEach { method ->
                runCatching { method.invoke(holder, app.packageName, user) }
                if (isHiddenNow(app) == hidden) return true
            }
        }
        return isHiddenNow(app) == hidden
    }

    private fun isHiddenNow(app: HiddenApp): Boolean =
        currentHiddenApps().containsKey(LauncherBackupUtils.hiddenKey(app.packageName, app.userId))

    /** 从 DPAM 实例的字段里取出持有隐藏状态缓存 + prefs 的 HiddenDataCollection 及其变更方法 */
    private fun hiddenDataHolders(manager: Any): List<Pair<Any, List<Method>>> = runCatching {
        manager.javaClass.declaredFields.mapNotNull { field ->
            field.isAccessible = true
            val holder = runCatching { field.get(manager) }.getOrNull() ?: return@mapNotNull null
            val methods = holder.javaClass.declaredMethods.filter { method ->
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == UserHandle::class.java &&
                    method.returnType == Void.TYPE
            }
            if (methods.isEmpty()) null else holder to methods
        }
    }.getOrDefault(emptyList())

    /** 用户句柄：UserHandle.of(int) 在部分 SDK 上不可直接引用，反射取；拿不到退回当前用户（真机 profile 基本都是 0） */
    private fun userHandle(userId: Int): UserHandle = runCatching {
        UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType).invoke(null, userId) as? UserHandle
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
        val file = File(File(dataRoot, "shared_prefs"), HIDDEN_PREFS_FILE)
        return LauncherBackupUtils.parseHiddenPrefsXml(runCatching { file.readText() }.getOrNull())
    }

    // ---------------- 桌面重载 ----------------

    /**
     * 反射 LauncherAppState.getInstance(context).getModel().forceReload()。
     * 不抛异常：失败只返回原因，由调用方汇总到回传消息里。
     */
    private fun forceReloadLayout(): String? {
        val context = hostContext ?: return "no context"
        return runCatching {
            val appStateClass = Class.forName(LAUNCHER_APP_STATE, false, launcherClassLoader())
            val appState = appStateClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            val model = appState.javaClass.getMethod("getModel").invoke(appState)
            model.javaClass.getMethod("forceReload").invoke(model)
            null
        }.getOrElse { throwable ->
            YLog.debug(TAG + " forceReload 失败", throwable)
            throwable.message ?: throwable.javaClass.simpleName
        }
    }

    /** 当前桌面模式（Standard 0 / Drawer 2 / Simple 3）；取不到返回 null */
    private fun currentLauncherMode(): Int? = runCatching {
        val clazz = Class.forName(LAUNCHER_MODE_MANAGER, false, launcherClassLoader())
        val instance = clazz.getMethod("getInstance").invoke(null)
        (instance.javaClass.getMethod("getCurType").invoke(instance) as? Int)
    }.getOrElse {
        YLog.debug(TAG + " 读取 launcher_mode 失败", it)
        null
    }

    /** 宿主（launcher）的类加载器：YukiHookAPI 提供 hostClassLoader（appClassLoader 已废弃）；兜底用 hook 类自己的 */
    private fun launcherClassLoader(): ClassLoader =
        runCatching { hostClassLoader }.getOrNull() ?: javaClass.classLoader

    private const val HIDDEN_PREFS_NAME = "app_hidden_prefs"
    private const val HIDDEN_PREFS_FILE = "app_hidden_prefs.xml"
    private const val HIDDEN_SETTINGS_FILE = "app_hidden_settings_prefs.xml"
    private const val DEEP_PROTECTED_MANAGER = "com.android.launcher.filter.DeepProtectedAppsManager"
    private const val LAUNCHER_APP_STATE = "com.android.launcher3.LauncherAppState"
    private const val LAUNCHER_MODE_MANAGER = "com.android.launcher.mode.LauncherModeManager"

    /**
     * 单表写回：DELETE 全表后按快照行逐条 INSERT。
     * 插入列取「快照列 交 当前 PRAGMA table_info 列」，避免 launcher 版本差异导致列不匹配。
     */
    private fun restoreTable(database: SQLiteDatabase, table: String, rows: JSONArray) {
        val currentColumns = queryColumns(database, table)
        database.execSQL("DELETE FROM " + quote(table))
        if (rows.length() == 0 || currentColumns.isEmpty()) return
        val snapshotColumns = runCatching { rows.getJSONObject(0).keys().asSequence().toList() }
            .getOrDefault(emptyList())
        val columns = snapshotColumns.filter { it in currentColumns }
        if (columns.isEmpty()) {
            YLog.debug(TAG + " 快照与当前库没有共同列，跳过: " + table)
            return
        }
        val quotedColumns = columns.joinToString(", ") { quote(it) }
        val placeholders = columns.joinToString(", ") { "?" }
        val sql = "INSERT INTO " + quote(table) + " (" + quotedColumns + ") VALUES (" + placeholders + ")"
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

    // ---------------- 清单与保留策略 ----------------

    private fun delete(snapshotArg: String): Outcome {
        val root = backupRoot ?: return Outcome(false, "launcher data dir unavailable", snapshotArg)
        val snapshot = LauncherBackupUtils.parseSnapshotName(snapshotArg)
            ?: return Outcome(false, "invalid snapshot name: " + snapshotArg, snapshotArg)
        val dir = File(root, snapshot.name)
        if (!dir.isDirectory) return Outcome(false, "snapshot not found: " + snapshot.name, snapshotArg)
        return if (dir.deleteRecursively()) Outcome(true, "delete ok", snapshot.name)
        else Outcome(false, "delete failed: " + snapshot.name, snapshotArg)
    }

    /** 扫描快照目录（meta.json 自描述，缺 meta 时用目录名兜底） */
    private fun listSnapshots(root: File): List<LauncherSnapshot> {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val parsed = LauncherBackupUtils.parseSnapshotName(dir.name) ?: return@mapNotNull null
            val meta = runCatching {
                JSONObject(File(dir, LauncherBackupUtils.FILE_META).readText())
            }.getOrNull()
            var rows = 0
            meta?.optJSONObject("rows")?.let { counts ->
                counts.keys().forEach { rows += counts.optInt(it) }
            }
            LauncherSnapshot(
                name = parsed.name,
                tag = parsed.tag,
                timeMillis = meta?.optLong("time")?.takeIf { it > 0L } ?: parsed.timeMillis,
                sizeBytes = dirSize(dir),
                rows = rows,
            )
        }.sortedByDescending { it.timeMillis }
    }

    private fun dirSize(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    /** 超过上限时按保留策略删旧快照（先删最旧的 auto）；protected 是正在恢复的目标快照，永远不删 */
    private fun evict(root: File, protected: String? = null) {
        val excluded = if (protected == null) emptySet() else setOf(protected)
        LauncherBackupUtils.planEvictions(listSnapshots(root), excluded = excluded).forEach { snapshot ->
            runCatching { File(root, snapshot.name).deleteRecursively() }
                .onFailure { YLog.debug(TAG + " 删除旧快照失败: " + snapshot.name, it) }
        }
    }

    // ---------------- 结果回传 ----------------

    private fun sendResult(command: LauncherCommand, outcome: Outcome) {
        val context = hostContext ?: return
        val root = backupRoot
        val snapshots = if (root == null) emptyList() else listSnapshots(root)
        val state = LauncherBackupUtils.buildStateJson(
            ok = outcome.ok,
            action = command.action,
            message = outcome.message,
            backupRoot = root?.absolutePath.orEmpty(),
            token = command.token,
            snapshots = snapshots,
            snapshot = outcome.snapshot,
        )
        val intent = Intent(LauncherBackupUtils.resultAction()).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra(LauncherBackupUtils.EXTRA_PAYLOAD, state)
        }
        runCatching { context.sendBroadcast(intent) }
            .onFailure { YLog.debug(TAG + " 回传失败", it) }
    }

    private data class Outcome(val ok: Boolean, val message: String, val snapshot: String)

    // ---------------- 数据库工具 ----------------

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
    private fun resolveDataRoot(context: Context, packageName: String): File? {
        val candidates = listOfNotNull(
            runCatching { context.dataDir }.getOrNull(),
            File("/data/user_de/0/" + packageName),
            File("/data/user/0/" + packageName),
            File("/data/data/" + packageName),
        )
        return candidates.firstOrNull { dir ->
            runCatching { File(dir, "databases/launcher.db").isFile }.getOrDefault(false)
        }
    }

    private fun failAndClean(dir: File, message: String): Outcome {
        runCatching { dir.deleteRecursively() }
        return Outcome(false, message, "")
    }
}
