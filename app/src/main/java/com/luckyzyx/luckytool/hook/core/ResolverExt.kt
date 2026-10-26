package com.luckyzyx.luckytool.hook.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.makeAccessible

/**
 * 同形 YukiHookAPI 的 instance<T>()：非空强转（legacy 语义，as T）。
 * HookCall 的成员属性 [HookCall.instance] 提供裸可空引用，instanceOrNull 提供可空取值。
 */
inline fun <reified T> HookCall.instance(): T = instance as T

/** 使用本次回调的宿主类加载器，支持共享进程中同时运行的多个包。 */
@Suppress("UNCHECKED_CAST")
fun String.toClass(loader: ClassLoader? = Env.activeClassLoader(), initialize: Boolean = false): Class<Any> =
    Class.forName(this, initialize, loader ?: Env.activeClassLoader()) as Class<Any>

fun String.toClassOrNull(loader: ClassLoader? = Env.activeClassLoader(), initialize: Boolean = false): Class<Any>? =
    runCatching { toClass(loader, initialize) }.getOrNull()

/**
 * 同形 legacy YukiBaseHooker 成员扩展的 VariousClass.toClass()：委托 KavaRef load()。
 * legacy 里 VariousClass 没有 toClass 扩展，由 PackageParam 成员扩展提供。
 * 返回 Class<Any>（非 *），保持 resolve() 结果不变性，.of(instance) 调用点才能匹配。
 */
fun VariousClass.toClass(
    loader: ClassLoader? = Env.activeClassLoader(), initialize: Boolean = false
): Class<Any> = load(loader, initialize)

/** 同形 legacy 的 VariousClass.toClassOrNull()：委托 KavaRef loadOrNull() */
fun VariousClass.toClassOrNull(
    loader: ClassLoader? = Env.activeClassLoader(), initialize: Boolean = false
): Class<Any>? = loadOrNull(loader, initialize)

/**
 * 注入模块资源（同形 YukiHookAPI injectModuleAppResources）：
 * 对齐 legacy AppParasitics 的实现——直接反射宿主 Resources 的 AssetManager，
 * addAssetPath(模块 APK 路径)，使宿主 Resources 原生可解析模块资源 id。
 * 不能用 packageManager.getResourcesForApplication：框架隐藏模块包名，宿主 PM 查不到。
 */
@SuppressLint("DiscouragedPrivateApi")
fun Context.injectModuleAppResources() {
    val modulePath = Env.moduleAppInfo?.sourceDir ?: run {
        XLog.w("injectModuleAppResources failed: moduleAppInfo unavailable")
        return
    }
    runCatching {
        val assets = resources.assets
        classOf<AssetManager>()
            .getDeclaredMethod("addAssetPath", classOf<String>())
            .apply { makeAccessible() }
            .invoke(assets, modulePath)
    }.onFailure { XLog.w("injectModuleAppResources failed", t = it) }
}
