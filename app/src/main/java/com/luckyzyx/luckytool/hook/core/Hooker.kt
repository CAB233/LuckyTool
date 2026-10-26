package com.luckyzyx.luckytool.hook.core

import android.content.pm.ApplicationInfo
import android.content.Context
import com.highcapable.kavaref.extension.VariousClass
import android.util.Log

/**
 * Hooker 基接口：对应迁移前 YukiBaseHooker 的常用成员面，
 * 使既有 hook 文件只需替换基类与 import。
 */
interface Hooker {

    fun onHook()

    /** 同形 YukiBaseHooker.packageName：该 Hooker 分发时的宿主包名（wrapper 语义，不随共享进程漂移） */
    val packageName: String get() = HookRouter.contextOf(this)?.packageName ?: Env.packageName

    val processName: String get() = Env.processName

    /** 同形 YukiBaseHooker.classLoader：该 Hooker 分发上下文的 appClassLoader */
    val classLoader: ClassLoader?
        get() = HookRouter.contextOf(this)?.appClassLoader ?: Env.activeClassLoader()

    /** 同形 YukiBaseHooker.appClassLoader：非空版（legacy 中即非空） */
    val appClassLoader: ClassLoader
        get() = classLoader
            ?: Env.activeClassLoader()
            ?: error("classLoader not attached")

    /** 同形 YukiBaseHooker.appInfo：该 Hooker 分发时的宿主包信息（App 分组必非空） */
    val appInfo: ApplicationInfo
        get() = HookRouter.contextOf(this)?.appInfo
            ?: Env.appInfo
            ?: appContext?.applicationInfo
            ?: error("appInfo is null for non-app host")

    val appContext: Context? get() = Env.hostContext()

    @Suppress("UNCHECKED_CAST")
    fun String.toClass(loader: ClassLoader? = appClassLoader, initialize: Boolean = false): Class<Any> =
        Class.forName(this, initialize, loader ?: appClassLoader) as Class<Any>

    fun String.toClassOrNull(loader: ClassLoader? = appClassLoader, initialize: Boolean = false): Class<Any>? =
        runCatching { toClass(loader, initialize) }.getOrNull()

    fun VariousClass.toClass(loader: ClassLoader? = appClassLoader, initialize: Boolean = false): Class<Any> =
        load(loader, initialize)

    fun VariousClass.toClassOrNull(loader: ClassLoader? = appClassLoader, initialize: Boolean = false): Class<Any>? =
        loadOrNull(loader, initialize)

    /** 同形 YukiBaseHooker.dataChannel：实时配置推送的远程偏好等价物 */
    val dataChannel: Channel get() = Channel

    /** 同形 prefs(ModulePrefs)：远程偏好读取（非空 getter 包装，对齐 legacy YukiHookPrefsBridge） */
    fun prefs(name: String): Env.NonNullPrefs = Env.prefs(name)

    /** 同形 loadHooker：装载子 Hooker（继承父 Hooker 的分发上下文，异常隔离） */
    fun loadHooker(hooker: Hooker) {
        runCatching {
            val parent = HookRouter.contextOf(this)
            HookRouter.assignContext(hooker, parent)
            val context = parent?.let { PackageContext(it.packageName, hooker.classLoader, it.appInfo) }
            HookRouter.assignContext(hooker, context)
            HookRouter.withContext(context) { hooker.onHook() }
        }.onFailure {
            Env.log(Log.ERROR, "HookRouter", "loadHooker ${hooker::class.java.name} failed", it)
        }
    }
}
