package com.luckyzyx.luckytool.hook.core

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.luckyzyx.luckytool.utils.ChannelEventCodec
import com.luckyzyx.luckytool.utils.ChannelPrefs
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SettingsPrefs
import com.luckyzyx.luckytool.utils.IntentPrefs
import com.luckyzyx.luckytool.utils.OtherPrefs
import java.util.concurrent.CopyOnWriteArrayList

/** 监听配置变更和定向事件；注册时保留现有 Hooker 已读取的初始值。 */
object Channel {
    // SharedPreferences 弱引用监听器，模块持有强引用直至宿主进程退出。
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    internal var dispatch: (() -> Unit) -> Unit = { block ->
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    inline fun <reified T> wait(key: String, noinline block: (T) -> Unit) {
        subscribe(key) { value -> (value as? T)?.let(block) }
    }

    fun wait(key: String, block: () -> Unit) = subscribe(key) { block() }

    @PublishedApi
    internal fun subscribe(key: String, block: (Any?) -> Unit) {
        val context = HookRouter.currentContext()
        val target = context?.packageName ?: Env.packageName
        fun deliver(value: Any?) {
            // 框架从 Binder 线程通知，原通道的控件更新在宿主主线程执行。
            dispatch {
                runCatching { HookRouter.withContext(context) { block(value) } }
                    .onFailure { XLog.e("Preference callback failed: $key", t = it) }
            }
        }
        // 同时监听全部配置组，覆盖用户首次设置、键尚未存在的情况。
        for (group in listOf(ModulePrefs, SettingsPrefs, IntentPrefs, OtherPrefs)) {
            val prefs = Env.prefs(group)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, changedKey ->
                if (changedKey == key) deliver(changed.all[key])
            }
            listeners += listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }
        val events = Env.prefs(ChannelPrefs)
        val eventKey = ChannelEventCodec.key(target, key)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, changedKey ->
            if (changedKey == eventKey) {
                runCatching { ChannelEventCodec.decode(changed.getString(eventKey, "") ?: "") }
                    .onSuccess { event ->
                        if (event.target == target || event.target == "system" && target == "android") {
                            deliver(event.value)
                        }
                    }
                    .onFailure { XLog.e("Invalid preference event: $key", t = it) }
            }
        }
        listeners += listener
        events.registerOnSharedPreferenceChangeListener(listener)
    }
}
