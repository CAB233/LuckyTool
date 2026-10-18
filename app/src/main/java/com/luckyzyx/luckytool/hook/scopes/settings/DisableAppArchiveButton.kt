package com.luckyzyx.luckytool.hook.scopes.settings

import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.intercept
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate

/**
 * 隐藏应用详情页的"归档"按钮（settings 进程）
 *
 * 注意与《自动释放应用空间》（disable_app_archiving）不是同一功能：
 * 那个开关是闲置应用的一整套自动管理策略（撤销权限/删除临时文件/停止通知/归档），
 * 这个按钮只是手动归档单个应用。
 *
 * 原生实现（Source com.oplus.settings.feature.appmanager.AppInfoFeature）：
 * - isArchiveActionEnabled 判定当前应用能否归档，结果决定按钮可用性；
 * - updateAppArchiveAndRestoreButtonStatus 刷新 mRecoverButton（同一个 TextView 在
 *   "归档"与"恢复"之间切换）：未归档时按可用性 setEnabled，已归档时 setEnabled(true)。
 *
 * 本 hook 让归档判定恒为不可用，并在刷新后把按钮置为 GONE；已归档应用需要"恢复"入口，
 * 那一支原生会 setEnabled(true)，因此保留显示。
 */
@Obfuscate
object DisableAppArchiveButton : YukiBaseHooker() {

    override fun onHook() {
        var isEnable = preferences(ModulePrefs).getBoolean("disable_app_archive_button", false)
        dataChannel.wait<Boolean>("disable_app_archive_button") { isEnable = it }

        "com.oplus.settings.feature.appmanager.AppInfoFeature".toClass().resolve().apply {
            firstMethodOrNull { name = "isArchiveActionEnabled" }?.intercept {
                if (!isEnable) return@intercept proceed()
                //判定为不可归档：原生随后会把按钮 setEnabled(false)
                false
            }
            firstMethodOrNull { name = "updateAppArchiveAndRestoreButtonStatus" }?.intercept {
                if (!isEnable) return@intercept proceed()
                //先让原生刷新按钮文案与可用性
                val ret = proceed()
                val button = firstFieldOrNull { name = "mRecoverButton" }?.of(instance)
                    ?.get<TextView>() ?: return@intercept ret
                //仅归档态（不可用）隐藏，保留已归档应用的"恢复"按钮
                if (!button.isEnabled) button.visibility = View.GONE
                ret
            }
        }
    }
}
