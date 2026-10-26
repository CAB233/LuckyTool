package com.luckyzyx.luckytool.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 模块 App 侧接入框架 service 的唯一入口（libxposed service 原生实现）。
 *
 * 迁移前该角色由 YukiHook 门面（`XposedServiceManager`）承担，迁移移除 YukiHook 后改为直接注册
 * [XposedServiceHelper] 监听：整个进程只有这一处注册，不再有「静态监听槽被占用」的问题。
 *
 * - [isAvailable]：框架 service 已绑定。远端 prefs 只在它为 true 时才是真的 remote。
 * - [isModuleActive]：service 已绑定 **且** scope 非空，与迁移前语义一致。
 * - [preferences]：远端 prefs（宿主进程同一数据源），不可用时返回 null，由调用方回落本地 prefs。
 *
 * 激活检测是「观察」而不是「问一次」：绑定是异步的，绑定/断开都经回调送达，
 * 所以 UI 侧要等 [isModuleActive] 出结果再读 prefs，等待期间只组合加载层（见 ActivationLoadingLayer）。
 */
object XposedServiceManager {

    private const val TAG = "Xposed Service Manager"

    /** 迁移状态记录：每个 prefs 组只把旧本地值补进远端一次 */
    private const val MIGRATION_STATE = "XposedServiceMigration"

    /** 宿主侧真正读写的远端组；[SettingsPrefs]/[OtherPrefs] 是本地专用（见 SPUtils.LocalOnlyPrefs） */
    private val REMOTE_PREFS = listOf(ModulePrefs, IntentPrefs)

    @Volatile
    private var initialized = false

    @Volatile
    private var service: XposedService? = null

    /** 框架 service 是否可用：回调直接给出的值，不含 scope 判定 */
    @Volatile
    var isAvailable = false
        private set

    private val _isModuleActive = MutableStateFlow(false)

    /**
     * 模块激活态：框架服务已绑定且 scope 非空。
     * false 同时覆盖「未激活」与「绑定还没完成」（异步），调用方按超时区分两者。
     */
    val isModuleActive: StateFlow<Boolean> = _isModuleActive.asStateFlow()

    private val remotePreferences = mutableMapOf<String, SharedPreferences>()

    /** 注册框架 service 监听；重复调用无副作用 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                this@XposedServiceManager.service = service
                isAvailable = true
                runCatching { migrateLegacyPreferences(app, service) }
                    .onFailure { LogUtils.e(TAG, "migrateLegacyPreferences", "$it", true) }
                updateActiveState()
                LogUtils.d(
                    TAG,
                    "onServiceBind",
                    "framework ${service.frameworkName} ${service.frameworkVersion} (${service.frameworkVersionCode}), API ${service.apiVersion}",
                    true
                )
            }

            override fun onServiceDied(service: XposedService) {
                if (this@XposedServiceManager.service === service) {
                    this@XposedServiceManager.service = null
                    isAvailable = false
                    updateActiveState()
                    LogUtils.d(TAG, "onServiceDied", "framework service disconnected", true)
                }
            }
        })
    }

    /** scope 是 Binder 调用：框架进程异常时会抛，读失败按未激活处理并记日志 */
    private fun updateActiveState() {
        _isModuleActive.value = isAvailable && runCatching {
            service?.scope?.isNotEmpty() == true
        }.onFailure { LogUtils.e(TAG, "updateActiveState", "read module scope failed: $it", true) }
            .getOrDefault(false)
    }

    /**
     * remote prefs（宿主进程同一数据源）。
     * 按名字缓存实例：调用方（[PrefState]）用实例身份判断数据源是否切换、
     * 并把变更监听器注册在拿到的实例上，每次新建包装对象会让它每次都当成「数据源变了」而全量重读。
     */
    /** 框架服务实例：仅供热重载等少量调用点使用 */
    internal fun currentService(): XposedService? = service

    fun preferences(name: String): SharedPreferences? {
        if (!isAvailable) return null
        val current = service ?: return null
        return runCatching {
            synchronized(remotePreferences) {
                remotePreferences.getOrPut(name) {
                    RemotePreferences(current.getRemotePreferences(name))
                }
            }
        }.onFailure { LogUtils.e(TAG, "preferences", "open remote prefs failed: $it", true) }
            .getOrNull()
    }

    /**
     * 首次绑定后把旧版本落在应用私有目录的可读配置补进远端组：
     * 远端已有同名键时以远端为准，每组只迁移一次。
     */
    private fun migrateLegacyPreferences(context: Context, target: XposedService) {
        val state = context.getSharedPreferences(MIGRATION_STATE, Context.MODE_PRIVATE)
        for (group in REMOTE_PREFS) {
            if (state.getBoolean(group, false)) continue
            val remote = target.getRemotePreferences(group)
            val editor = remote.edit()
            context.getSharedPreferences(group, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (!remote.contains(key)) editor.putPreferenceValue(key, value)
            }
            if (editor.commit()) state.edit().putBoolean(group, true).apply()
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun SharedPreferences.Editor.putPreferenceValue(
    key: String,
    value: Any?
): SharedPreferences.Editor = when (value) {
    null -> remove(key)
    is String -> putString(key, value)
    is Boolean -> putBoolean(key, value)
    is Int -> putInt(key, value)
    is Long -> putLong(key, value)
    is Float -> putFloat(key, value)
    is Set<*> -> putStringSet(key, HashSet(value as Set<String>))
    else -> error("Unsupported preference value: ${value.javaClass.name}")
}

/**
 * 把框架的远端 prefs 适配成 [SharedPreferences]：
 * 读直接委托，写入在 commit/apply 之后通知注册在本实例上的监听器
 * （远端来源不会推送外部变更，因此只有经本实例写入才会有通知）。
 */
private class RemotePreferences(private val prefs: SharedPreferences) : SharedPreferences {

    private val listeners =
        Collections.synchronizedMap(WeakHashMap<OnSharedPreferenceChangeListener, Any>())

    override fun getAll(): MutableMap<String, *> = HashMap<String, Any?>(prefs.all)

    override fun getString(key: String, defValue: String?): String? =
        if (defValue != null) prefs.getString(key, defValue)
        else prefs.getString(key, null).takeIf { prefs.contains(key) }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        if (defValues != null) prefs.getStringSet(key, defValues)?.toMutableSet()
        else prefs.getStringSet(key, null).takeIf { prefs.contains(key) }?.toMutableSet()

    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = prefs.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        prefs.getBoolean(key, defValue)

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun edit(): SharedPreferences.Editor = NotifyingEditor(prefs.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) listeners[listener] = Unit
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: OnSharedPreferenceChangeListener?
    ) {
        if (listener != null) listeners.remove(listener)
    }

    /**
     * 按 key 通知本次改动。[cleared] 为真时先以 key == null 通知一次
     * （与 SPUtils.clearPrefs 的整份清空约定一致，最坏只是让监听方多整体重读一次）。
     */
    private fun notifyChanged(cleared: Boolean, changedKeys: Set<String>) {
        val snapshot = synchronized(listeners) { listeners.keys.toList() }
        if (snapshot.isEmpty()) return
        snapshot.forEach { listener ->
            if (cleared) listener.onSharedPreferenceChanged(this, null)
            changedKeys.forEach { listener.onSharedPreferenceChanged(this, it) }
        }
    }

    /** 记录改动过的键，在 commit/apply 成功后统一通知 */
    private inner class NotifyingEditor(
        private val delegate: SharedPreferences.Editor
    ) : SharedPreferences.Editor {

        private val changedKeys = linkedSetOf<String>()
        private var cleared = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            if (value == null) remove(key) else track(key) { delegate.putString(key, value) }

        override fun putStringSet(
            key: String, values: MutableSet<String>?
        ): SharedPreferences.Editor = if (values == null) remove(key) else track(key) {
            // 复制成 java.util 实现再交给框架侧反序列化（Kotlin 单例/不可变集合会解析不到）
            delegate.putStringSet(key, HashSet(values))
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            track(key) { delegate.putInt(key, value) }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            track(key) { delegate.putLong(key, value) }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            track(key) { delegate.putFloat(key, value) }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            track(key) { delegate.putBoolean(key, value) }

        override fun remove(key: String): SharedPreferences.Editor = track(key) {
            delegate.remove(key)
        }

        override fun clear(): SharedPreferences.Editor {
            cleared = true
            delegate.clear()
            return this
        }

        override fun commit(): Boolean {
            val committed = delegate.commit()
            notifyChanged(cleared, changedKeys)
            return committed
        }

        override fun apply() {
            delegate.apply()
            notifyChanged(cleared, changedKeys)
        }

        private fun track(key: String, block: () -> Unit): SharedPreferences.Editor {
            changedKeys.add(key)
            block()
            return this
        }
    }
}

/**
 * 等激活检测出结果：检测到激活返回 true，[timeoutMillis] 内仍未激活返回 false。
 *
 * UI 侧（MainActivity）靠它决定「继续显示加载层」还是「弹未激活阻塞门」。
 * [first] 只在值变化时唤醒，不轮询；超时返回 false 不代表就此结束——调用方仍继续观察，
 * 激活迟到到达时撤掉阻塞门。
 */
internal suspend fun StateFlow<Boolean>.awaitActivation(timeoutMillis: Long): Boolean =
    withTimeoutOrNull(timeoutMillis.milliseconds) { first { it } } != null
