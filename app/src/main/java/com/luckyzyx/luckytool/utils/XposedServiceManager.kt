package com.luckyzyx.luckytool.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.luckyzyx.luckytool.hook.core.XLog
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** 应用侧服务与远程配置，应用进程仅依赖 libxposed service。 */
object XposedServiceManager {
    @Volatile
    private var service: XposedService? = null
    private val mutableStatus = MutableLiveData<Boolean?>(null)
    val status: LiveData<Boolean?> = mutableStatus
    val isActive: Boolean get() = service != null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(context: Context) {
        val app = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                runCatching {
                    require(service.apiVersion >= 101)
                    require(service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L)
                    migratePreferences(app, service)
                    this@XposedServiceManager.service = service
                    mainHandler.post { mutableStatus.value = true }
                }.onFailure { XLog.e("Xposed service initialization failed", t = it) }
            }

            override fun onServiceDied(service: XposedService) {
                if (this@XposedServiceManager.service === service) {
                    this@XposedServiceManager.service = null
                    mainHandler.post { mutableStatus.value = false }
                }
            }
        })
        // 框架可从 Binder 线程异步交付服务，等待交付后再检查激活状态。
        mainHandler.postDelayed({ if (!isActive) mutableStatus.value = false }, 3000)
    }

    fun preferences(context: Context, group: String): SharedPreferences =
        service?.getRemotePreferences(group) ?: context.getSharedPreferences(group, Context.MODE_PRIVATE)

    fun frameworkVersion(): String? = runCatching {
        service?.let { "${it.frameworkName} ${it.frameworkVersion} (${it.frameworkVersionCode}), API ${it.apiVersion}" }
    }.getOrNull()

    private fun migratePreferences(context: Context, target: XposedService) {
        val migrated = context.getSharedPreferences("libxposed_migration", Context.MODE_PRIVATE)
        for (group in listOf(ModulePrefs, SettingsPrefs, IntentPrefs, OtherPrefs)) {
            if (migrated.getBoolean(group, false)) continue
            val remote = target.getRemotePreferences(group)
            val editor = remote.edit()
            context.getSharedPreferences(group, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (!remote.contains(key)) editor.putPreferenceValue(key, value)
            }
            check(editor.commit()) { "Failed to migrate $group" }
            check(migrated.edit().putBoolean(group, true).commit())
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun SharedPreferences.Editor.putPreferenceValue(key: String, value: Any?): SharedPreferences.Editor =
    when (value) {
        null -> remove(key)
        is String -> putString(key, value)
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Set<*> -> putStringSet(key, value as Set<String>)
        else -> error("Unsupported preference value: ${value.javaClass.name}")
    }
