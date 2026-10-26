package com.luckyzyx.luckytool.hook.scopes.settings

import android.app.AppOpsManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

/**
 * 《自动释放应用空间》默认关闭 - 设置页开关 UI 显示（settings 进程）
 *
 * 配合 framework 侧 DisableAppArchiving（实际归档判定）使用：
 * 原生 UI 显示逻辑 isPackageHibernationExemptByUser：默认态(op=3)且 targetSdk>29 的新应用
 * 显示开关为"开"，与 framework 判定不一致；本 hook 将 UI 显示改为与判定一致——
 * 仅 MODE_ALLOWED(0)（用户明确打开）显示开，默认态与显式关闭均显示关
 */
object DisableAppArchivingUI : Hooker {

    override fun onHook() {
        var isEnable = prefs(ModulePrefs).getBoolean("disable_app_archiving", true)
        Channel.wait<Boolean>("disable_app_archiving") { isEnable = it }

        //Source HibernationSwitchPreferenceController（设置应用详情页开关 UI）
        "com.android.settings.applications.appinfo.HibernationSwitchPreferenceController".toClass()
            .resolve().apply {
                firstMethod { name = "isPackageHibernationExemptByUser" }.replaceAny {
                    if (!isEnable) return@replaceAny callOriginal()
                    val uid = firstField { name = "mPackageUid" }.of(instance).get<Int>()
                        ?: return@replaceAny callOriginal()
                    val packageName =
                        firstField { name = "mPackageName" }.of(instance).get<String>()
                            ?: return@replaceAny callOriginal()
                    //控制器自身就有 mAppOpsManager 字段，不必再从 Context 取
                    val appOps = firstField { name = "mAppOpsManager" }.of(instance).get<AppOpsManager>()
                        ?: return@replaceAny callOriginal()
                    //默认态(3)与显式关闭(1)均显示关，仅用户明确打开(0)才显示开
                    try {
                        appOps.checkOpNoThrow(
                            "android:auto_revoke_permissions_if_unused", uid, packageName
                        ) != AppOpsManager.MODE_ALLOWED
                    } catch (_: Throwable) {
                        callOriginal()  //异常兜底：走原生显示
                    }
                }
            }
    }
}
