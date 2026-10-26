package com.luckyzyx.luckytool.hook

import android.annotation.SuppressLint
import com.luckyzyx.luckytool.hook.core.Env
import com.luckyzyx.luckytool.hook.core.HookRouter
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.io.File

/**
 * Modern Xposed API 101 入口：框架通过 `META-INF/xposed/java_init.list` 加载本类。
 *
 * 迁移前这里是 YukiHook 的 `@YukiHookLibXposedEntry` 入口，`onHook()` 里用
 * `loadSystem` / `loadApp` 静态注册；现在拆成两部分：
 * - 入口只负责注入 [Env] 上下文并把包回调交给 [HookRouter]
 * - 静态注册表搬到 [HookRouterInit]
 */
class MainHook : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Env.attach(
            base = this,
            processName = param.processName,
            frameworkProperties = frameworkProperties,
            moduleAppInfo = moduleApplicationInfo,
            isSystemServer = param.isSystemServer,
        )
        HookRouterInit.register()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (isMasterEnabled()) return
        HookRouter.dispatch(param.packageName, param.classLoader, param.applicationInfo)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (isMasterEnabled()) return
        HookRouter.dispatch("android", param.classLoader, null)
    }

    companion object {
        const val TAG = "LuckyTool"

        /** `/sdcard/disable_lt` 存在时整体停用（与上游一致：读取失败按未停用处理） */
        @SuppressLint("SdCardPath")
        fun isMasterEnabled(): Boolean {
            return try {
                File("/sdcard/disable_lt").exists()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
