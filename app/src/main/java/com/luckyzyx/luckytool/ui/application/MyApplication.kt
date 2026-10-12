package com.luckyzyx.luckytool.ui.application

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.luckyzyx.luckytool.ui.service.XposedServiceBridge
import com.luckyzyx.luckytool.ui.shell.ShellSettingsController
import com.luckyzyx.luckytool.utils.PredictiveBackUtils
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
class MyApplication : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()

        // 激活检测走 XposedServiceBridge（自己持有 libxposed service 监听）：
        // XposedServiceHelper 只有一个监听槽，YukiHook 的 LibXposedService 在 super.onCreate()
        // 里已经占过一次，这里再注册会把它顶掉，所以不能同时用 YukiHook.Status.observeFrameworkService
        XposedServiceBridge.init()
        // 预测性返回手势：把偏好同步到 ApplicationInfo（对齐 KernelSU 启动期行为，best-effort）
        PredictiveBackUtils.apply(this, ShellSettingsController.get(this).predictiveBack)
    }

}


























