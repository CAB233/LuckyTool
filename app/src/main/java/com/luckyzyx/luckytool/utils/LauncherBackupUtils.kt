package com.luckyzyx.luckytool.utils

import android.content.Context
import com.luckyzyx.luckytool.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 桌面布局备份/恢复的共享纯逻辑（app 进程与 launcher 进程的 hook 都能调用，不碰 shell、不用 root）。
 *
 * 数据源与落盘方式（用户已确认的决策）：
 * - 布局真源 launcher 的 databases/launcher.db，只导出白名单表为 JSON（不直接拷 db 文件，
 *   避免同 uid 拷贝时拿到写入中途的撕裂数据）。
 * - 隐藏应用状态在 shared_prefs/app_hidden_prefs.xml 与 app_hidden_settings_prefs.xml，按原文拷贝。
 * - 快照根 <launcherDataDir>/luckytool_backup/<yyyyMMdd_HHmmss>_<tag>/（同 uid，无需 root）。
 * - 恢复由 hook 在 launcher 进程内完成，写完调 LauncherModel.forceReload() 让桌面重读库
 *   （同进程写库不会触发 LauncherProvider.reloadLauncherIfExternal，必须自己调），不杀进程。
 */
object LauncherBackupUtils {

    /** 快照上限（超过时先删最旧的 auto） */
    const val MAX_SNAPSHOTS = 20

    /** 快照根目录名（位于 launcher 数据目录下，launcher 自己的 uid 可读写） */
    const val BACKUP_DIR_NAME = "luckytool_backup"

    /** 快照内的布局数据文件名（表 → 行数组，列名 → 值） */
    const val FILE_LAYOUT = "layout.json"

    /** 快照内的元信息文件名 */
    const val FILE_META = "meta.json"

    /**
     * 参与备份/恢复的布局表白名单：三种桌面模式（Standard 0 / Drawer 2 / Simple 3）各一套。
     * 只碰这六张布局表：绝不 DELETE 白名单外的表。
     * 不含 desktopappedit（编辑态草稿）与 recommendmarketinfo（网络拉来的市场推荐，非布局数据），
     * 恢复时对它们 DELETE 全表再回灌没有任何收益，只会制造副作用。
     */
    val LAYOUT_TABLES = listOf(
        "singledesktopitems",
        "singledesktopitems_draw",
        "singledesktopitems_simple",
        "singledesktopscreens",
        "singledesktopscreens_draw",
        "singledesktopscreens_simple",
    )

    /** 按原文拷贝的 prefs 文件（隐藏应用开关在这两个里） */
    val PREFS_FILES = listOf("app_hidden_prefs.xml", "app_hidden_settings_prefs.xml")

    /**
     * launcher 包名候选：本机实测 com.android.launcher；老 ColorOS 为 com.oppo.launcher
     * （与 hook/hookers/MainHook.kt 加载的作用域包名保持一致，否则 app 侧下发指令会直接找不到人）。
     */
    val LAUNCHER_CANDIDATES = listOf("com.android.launcher", "com.oplus.launcher", "com.oppo.launcher")

    /** app → hook 的指令 pref 键（值形如 action|arg|token） */
    const val KEY_COMMAND = "launcher_backup_command"

    /** hook → app 的回传 pref 键（JSON，由 receiver 写入） */
    const val KEY_STATE = "launcher_backup_state"

    /**
     * 最近一条已下发指令的 token。
     * receiver 是 exported 的（launcher 以别的 uid 发广播，必须导出），所以只认「自己刚下发的 token」，
     * 否则任何 app 都能构造 payload 覆盖回传内容与快照列表。
     */
    const val KEY_PENDING_TOKEN = "launcher_backup_pending_token"

    const val ACTION_BACKUP = "backup"
    const val ACTION_RESTORE = "restore"
    const val ACTION_LIST = "list"
    const val ACTION_DELETE = "delete"

    val ACTIONS = listOf(ACTION_BACKUP, ACTION_RESTORE, ACTION_LIST, ACTION_DELETE)

    /** hook 回传广播的 action 后缀（完整 action 是 applicationId 加上它） */
    const val ACTION_RESULT_SUFFIX = ".LAUNCHER_BACKUP_RESULT"

    /** 回传广播里承载 JSON 的 extra 键 */
    const val EXTRA_PAYLOAD = "payload"

    private const val TIME_FORMAT = "yyyyMMdd_HHmmss"

    // ---------------- 快照命名 ----------------

    /** 快照目录名：yyyyMMdd_HHmmss 加下划线加 tag（只含数字、下划线与 ASCII 标签） */
    fun snapshotName(now: Long, tag: LauncherSnapshotTag): String =
        SimpleDateFormat(TIME_FORMAT, Locale.US).format(Date(now)) + "_" + tag.id

    /** 解析快照目录名；不是本工具的快照目录则返回 null */
    fun parseSnapshotName(name: String): LauncherSnapshot? {
        val tag = LauncherSnapshotTag.entries.firstOrNull { name.endsWith("_" + it.id) } ?: return null
        val stamp = name.removeSuffix("_" + tag.id)
        val time = runCatching {
            SimpleDateFormat(TIME_FORMAT, Locale.US).parse(stamp)?.time
        }.getOrNull() ?: return null
        return LauncherSnapshot(name, tag, time)
    }

    // ---------------- 指令协议 ----------------

    /** 生成指令串（token 保证每次不同，hook 侧据此去重） */
    fun buildCommand(action: String, arg: String = "", token: String = newToken()): String =
        action + "|" + arg + "|" + token

    fun newToken(): String = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(8)

    /**
     * 解析指令串。
     * 格式 action|arg|token；action 必须在 [ACTIONS] 内，restore / delete 必须带 arg。
     */
    fun parseCommand(raw: String?): LauncherCommand? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split("|")
        if (parts.size < 3) return null
        val action = parts[0].trim()
        val arg = parts[1].trim()
        // token 里不含竖线，但保险起见把剩余段并回去
        val token = parts.drop(2).joinToString("|").trim()
        if (action !in ACTIONS || token.isEmpty()) return null
        if ((action == ACTION_RESTORE || action == ACTION_DELETE) && arg.isEmpty()) return null
        return LauncherCommand(action, arg, token)
    }

    /** 同一条指令只执行一次：token 与上次相同即视为重复 */
    fun isDuplicateToken(lastToken: String?, token: String): Boolean =
        !lastToken.isNullOrBlank() && lastToken == token

    // ---------------- 清单 JSON ----------------

    /** 快照清单 → JSON 数组字符串 */
    fun buildManifestJson(snapshots: List<LauncherSnapshot>): String {
        val array = JSONArray()
        snapshots.sortedWith(compareByDescending<LauncherSnapshot> { it.timeMillis }.thenByDescending { it.name })
            .forEach { snapshot ->
                array.put(JSONObject().apply {
                    put("name", snapshot.name)
                    put("tag", snapshot.tag.id)
                    put("time", snapshot.timeMillis)
                    put("size", snapshot.sizeBytes)
                    put("rows", snapshot.rows)
                })
            }
        return array.toString()
    }

    /** 清单 JSON → 快照列表（解析失败返回空表，不抛异常） */
    fun parseManifestJson(json: String?): List<LauncherSnapshot> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            val tag = LauncherSnapshotTag.ofId(item.optString("tag"))
                ?: parseSnapshotName(name)?.tag
                ?: LauncherSnapshotTag.MANUAL
            val time = item.optLong("time").takeIf { it > 0 } ?: parseSnapshotName(name)?.timeMillis ?: 0L
            LauncherSnapshot(
                name = name,
                tag = tag,
                timeMillis = time,
                sizeBytes = item.optLong("size"),
                rows = item.optInt("rows"),
            )
        }
    }.getOrDefault(emptyList())

    /** hook → app 的一次回传（清单 + 最近一次操作结果） */
    fun buildStateJson(
        ok: Boolean,
        action: String,
        message: String,
        backupRoot: String,
        token: String,
        snapshots: List<LauncherSnapshot>,
        snapshot: String = "",
        time: Long = System.currentTimeMillis(),
    ): String = JSONObject().apply {
        put("ok", ok)
        put("action", action)
        put("message", message)
        put("backupRoot", backupRoot)
        put("token", token)
        put("snapshot", snapshot)
        put("time", time)
        put("snapshots", JSONArray(buildManifestJson(snapshots)))
    }.toString()

    /** 解析 hook 回传（解析失败返回 null，界面据此提示「未收到回传」） */
    fun parseStateJson(json: String?): LauncherState? = runCatching {
        val text = json?.trim().orEmpty()
        if (text.isEmpty()) return null
        val obj = JSONObject(text)
        LauncherState(
            ok = obj.optBoolean("ok"),
            action = obj.optString("action"),
            message = obj.optString("message"),
            backupRoot = obj.optString("backupRoot"),
            token = obj.optString("token"),
            snapshot = obj.optString("snapshot"),
            time = obj.optLong("time"),
            snapshots = parseManifestJson(obj.optJSONArray("snapshots")?.toString()),
        )
    }.getOrNull()

    // ---------------- 保留策略 ----------------

    /**
     * 保留策略：最多 [limit] 份，超限时先删最旧的 auto，再删最旧的 manual / pre_restore。
     * 返回待删除的快照（调用方逐个删目录）。
     */
    fun planEvictions(
        snapshots: List<LauncherSnapshot>,
        limit: Int = MAX_SNAPSHOTS,
        excluded: Set<String> = emptySet(),
    ): List<LauncherSnapshot> {
        if (limit <= 0) return emptyList()
        // 超限份数按「包含受保护快照」的总数算，但受保护的那份永远不会被选中
        val excess = snapshots.size - limit
        if (excess <= 0) return emptyList()
        val byAge = snapshots
            .filter { it.name !in excluded }
            .sortedWith(compareBy({ it.timeMillis }, { it.name }))
        val picked = byAge.filter { it.tag == LauncherSnapshotTag.AUTO }.take(excess).toMutableList()
        if (picked.size < excess) {
            picked += byAge.filter { it.tag != LauncherSnapshotTag.AUTO }.take(excess - picked.size)
        }
        return picked
    }

    // ---------------- 隐藏应用状态 ----------------

    /**
     * 键规则来自 DeepProtectedAppsManager.k(userId, pkg)：pkg + "#" + userId。
     * 值不是开关而是隐藏项的 rank（键存在即已隐藏，-1 表示无记录）。
     */
    fun hiddenKey(packageName: String, userId: Int): String = packageName + "#" + userId

    /** 解析 <pkg>#<userId>；不含分隔符或 userId 非数字返回 null */
    fun parseHiddenKey(key: String?): HiddenApp? {
        val text = key?.trim().orEmpty()
        val index = text.lastIndexOf('#')
        if (index <= 0 || index == text.length - 1) return null
        val userId = text.substring(index + 1).toIntOrNull() ?: return null
        return HiddenApp(text.substring(0, index), userId)
    }

    /**
     * 解析共享偏好 XML 里的 <int name="pkg#0" value="2" />。
     * 这里只做「原样取值」，绝不归一化：rank 一旦被改写就会打乱隐藏列表顺序。
     */
    fun parseHiddenPrefsXml(xml: String?): Map<String, Int> {
        val text = xml.orEmpty()
        if (text.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        val regex = Regex("""<int\s+name="([^"]+)"\s+value="(-?\d+)"""")
        regex.findAll(text).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (parseHiddenKey(key) != null) result[key] = value
        }
        return result
    }

    /**
     * 隐藏状态双向差集：快照里有、当前没有 → 要隐藏；当前有、快照里没有 → 要取消隐藏。
     * 值（rank）不参与比较，由 launcher 自己的 I() 入口重新排列。
     */
    fun planHiddenSync(snapshot: Map<String, Int>, current: Map<String, Int>): HiddenSync {
        val toHide = snapshot.keys
            .filter { it !in current }
            .mapNotNull { parseHiddenKey(it) }
            .sortedWith(compareBy({ it.packageName }, { it.userId }))
        val toUnhide = current.keys
            .filter { it !in snapshot }
            .mapNotNull { parseHiddenKey(it) }
            .sortedWith(compareBy({ it.packageName }, { it.userId }))
        return HiddenSync(toHide, toUnhide)
    }

    // ---------------- app 侧便捷封装 ----------------

    /** 回传广播的完整 action（hook 发、app 的 receiver 收） */
    fun resultAction(): String = BuildConfig.APPLICATION_ID + ACTION_RESULT_SUFFIX

    /** 探测已安装的 launcher 包名（app 侧 QUERY_ALL_PACKAGES 可见系统应用），找不到返回 null */
    fun detectLauncherPackage(context: Context): String? {
        val manager = context.packageManager ?: return null
        return LAUNCHER_CANDIDATES.firstOrNull { pkg ->
            runCatching { manager.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /**
     * 把指令推给 hook（与其它功能同一条 dataChannel/prefs 通道）。
     * 下发前先记下 token：receiver 只接受带这个 token 的回传，防止外部伪造广播。
     */
    fun sendCommand(context: Context, command: String, launcherPackage: String?): Boolean {
        val pkg = launcherPackage ?: detectLauncherPackage(context) ?: return false
        val token = parseCommand(command)?.token
        if (token.isNullOrBlank()) return false
        return runCatching {
            PrefState.of(context, ModulePrefs).set(KEY_PENDING_TOKEN, token)
            context.sendPrefsValue(pkg, KEY_COMMAND, command)
            true
        }.getOrDefault(false)
    }
}

/** 一条隐藏应用记录（键 pkg#userId，值是隐藏项的 rank） */
data class HiddenApp(val packageName: String, val userId: Int)

/** 隐藏状态需要的双向改动 */
data class HiddenSync(val toHide: List<HiddenApp>, val toUnhide: List<HiddenApp>)

/** 快照标签：auto 是自动备份，manual 是界面手动，pre_restore 是恢复前的自动补拍 */
enum class LauncherSnapshotTag(val id: String) {
    AUTO("auto"),
    MANUAL("manual"),
    PRE_RESTORE("pre_restore"),
    ;

    companion object {
        fun ofId(id: String?): LauncherSnapshotTag? = entries.firstOrNull { it.id == id }
    }
}

/** 一份快照：目录名、标签、时间、占用大小、布局行数（后两项来自 hook 回传） */
data class LauncherSnapshot(
    val name: String,
    val tag: LauncherSnapshotTag,
    val timeMillis: Long,
    val sizeBytes: Long = 0L,
    val rows: Int = 0,
)

/** 一条指令：action|arg|token */
data class LauncherCommand(val action: String, val arg: String, val token: String)

/** hook 的一次回传 */
data class LauncherState(
    val ok: Boolean,
    val action: String,
    val message: String,
    val backupRoot: String,
    val token: String,
    val snapshot: String,
    val time: Long,
    val snapshots: List<LauncherSnapshot>,
)

/** 自动备份的 prefs 键与取值约定 */
object LauncherBackupPrefs {

    /** 自动备份开关 */
    const val KEY_AUTO_ENABLED = "launcher_backup_auto_enabled"

    /** 自动备份周期（小时，字符串存值，与 list 控件一致） */
    const val KEY_AUTO_INTERVAL = "launcher_backup_auto_interval"

    /** 可选周期（小时） */
    val INTERVAL_HOURS = intArrayOf(6, 12, 24, 48)

    /** 默认周期 24 小时 */
    const val DEFAULT_INTERVAL_HOURS = 24

    /** 把存储值（字符串）解析成小时；非法值回落默认 */
    fun intervalHours(value: String?): Int {
        val hours = value?.trim()?.toIntOrNull() ?: return DEFAULT_INTERVAL_HOURS
        //只接受界面提供的周期，脏值（7、-6 之类）回落默认，避免派出计划外的周期任务
        return if (hours in INTERVAL_HOURS) hours else DEFAULT_INTERVAL_HOURS
    }
}
