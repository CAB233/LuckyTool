package com.luckyzyx.luckytool.hook.scopes.android

import android.content.Intent
import android.content.IntentFilter

/** 只匹配本地 APK 的安装入口，不处理卸载、安装会话或其他文件类型。 */
internal object InstallerRoutingPolicy {
    const val PREFERENCE_KEY = "allow_third_party_installer"
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"

    fun matches(intent: Intent?): Boolean {
        if (intent == null || intent.selector != null) return false
        if (intent.data?.scheme !in setOf("content", "file")) return false
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.type == APK_MIME_TYPE
            ACTION_INSTALL_PACKAGE -> intent.type == null || intent.type == APK_MIME_TYPE
            else -> false
        }
    }

    fun matches(filter: IntentFilter?): Boolean {
        if (filter == null) return false
        if (!filter.hasAction(Intent.ACTION_VIEW) && !filter.hasAction(ACTION_INSTALL_PACKAGE)) return false
        // 通配类型不能代表用户正在选择 APK 安装器。
        return (0 until filter.countDataTypes()).any { filter.getDataType(it) == APK_MIME_TYPE }
    }
}
