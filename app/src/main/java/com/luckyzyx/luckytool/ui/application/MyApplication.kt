package com.luckyzyx.luckytool.ui.application

import android.app.Application
import com.luckyzyx.luckytool.ui.shell.ShellSettingsController
import com.luckyzyx.luckytool.utils.PredictiveBackUtils
import com.luckyzyx.luckytool.utils.XposedServiceManager

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 激活检测走 XposedServiceManager（直接注册 libxposed service 监听，
        // 迁移移除 YukiHook 后进程内只剩这一处注册）。
        XposedServiceManager.init(this)
        // 预测性返回手势：启动时尝试把偏好同步到 ApplicationInfo。
        PredictiveBackUtils.apply(this, ShellSettingsController.get(this).predictiveBack)
    }

}
