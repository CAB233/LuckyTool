package com.luckyzyx.luckytool.utils

import android.app.Activity
import androidx.annotation.StringRes
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.base.IPermission
import com.hjq.permissions.permission.PermissionLists
import com.luckyzyx.luckytool.R
import org.lsposed.lsparanoid.Obfuscate
import java.lang.ref.WeakReference

/**
 * 模块自身权限（权限阻塞门的判定集合）：4 项全部授予才算通过。
 *
 * `POST_NOTIFICATIONS` 刻意不在此列 —— 它仍只由冷启动自动申请处理，不参与门禁判定。
 */
enum class AppPermission(@StringRes val labelRes: Int) {
    MANAGE_EXTERNAL_STORAGE(R.string.all_files_access_permission),
    REQUEST_INSTALL_PACKAGES(R.string.install_app_permission),
    GET_INSTALLED_APPS(R.string.installed_apps_permission),
    NOTIFICATION_SERVICE(R.string.send_notification_permission);

    /** 权限库对象（不缓存实例，避免把库内部状态挂在枚举常量上） */
    fun toPermission(): IPermission = when (this) {
        MANAGE_EXTERNAL_STORAGE -> PermissionLists.getManageExternalStoragePermission()
        REQUEST_INSTALL_PACKAGES -> PermissionLists.getRequestInstallPackagesPermission()
        GET_INSTALLED_APPS -> PermissionLists.getGetInstalledAppsPermission()
        NOTIFICATION_SERVICE -> PermissionLists.getNotificationServicePermission()
    }
}

@Obfuscate
class PermissionUtils(val activity: Activity) {

    /**
     * 冷启动自动申请（含 `POST_NOTIFICATIONS` 共 5 项），完成后回调 [onFinished]，
     * 参数是 4 项判定集合是否全部授予。
     *
     * 申请进行中重入直接 return（[requesting] 互斥）：配合 [startAutoRequestOnce] 消除
     * 「从系统设置页返回 → onResume → 再申请 → 再跳转」的重复请求 + 重复跳转死循环。
     */
    fun start(onFinished: (allGranted: Boolean) -> Unit = {}) {
        if (!beginRequest()) return
        XXPermissions.setCheckMode(false)
        XXPermissions.with(activity).apply {
            permission(PermissionLists.getManageExternalStoragePermission())
            permission(PermissionLists.getRequestInstallPackagesPermission())
            permission(PermissionLists.getGetInstalledAppsPermission())
            permission(PermissionLists.getNotificationServicePermission())
            permission(PermissionLists.getPostNotificationsPermission())
        }.request { _, deniedList ->
            finishRequest()
            // 冷启动这一次保留原有的「被永久拒绝则跳转一次系统设置页」行为（循环已由只申请一次消除）
            if (deniedList.isNotEmpty() &&
                XXPermissions.isDoNotAskAgainPermissions(activity, deniedList)
            ) {
                XXPermissions.startPermissionActivity(activity, deniedList)
            }
            onFinished(areAllGranted(activity))
        }
    }

    /**
     * 页面内逐项申请：**绝不自动跳转**，被永久拒绝的行由页面显式提供「去系统设置」按钮
     * （见 [isDoNotAskAgain] / [openSystemPermissionSettings]），与 [start] 共用 [requesting] 互斥。
     */
    fun request(kind: AppPermission, onResult: (granted: Boolean) -> Unit = {}) {
        if (!beginRequest()) return
        XXPermissions.setCheckMode(false)
        XXPermissions.with(activity)
            .permission(kind.toPermission())
            .request { _, deniedList ->
                finishRequest()
                onResult(deniedList.isEmpty())
            }
    }

    /** 用户手势触发的跳转：只允许权限页的按钮调用它，检测路径不得触碰 */
    fun openSystemPermissionSettings(kind: AppPermission) {
        XXPermissions.startPermissionActivity(activity, listOf(kind.toPermission()))
    }

    /**
     * 进入申请前的检查，两个拒入条件（都静默忽略，不加提示 UI）：
     * - Activity 已结束：权限库会直接 return，回调不会来，置标记只会把它永久卡住；
     * - 本实例已有在途请求：连点同一行属正常情况，忽略是正确行为。
     *
     * 反过来，在途请求的发起者若是**已被销毁的旧实例**（MainActivity.restart() 的 recreate()、
     * 配置变更、进程存活下的后台回收 —— 旧实例的 ActivityResultLauncher 随实例消亡、回调永不抵达），
     * 必须视为陈旧状态允许重新发起；否则 [requesting] 会在进程内永久为 true，让阻塞页的逐项申请
     * 静默失效，而冷启动自动申请又被 [autoRequested] 挡住，用户只能「退出应用」或杀进程。
     */
    private fun beginRequest(): Boolean {
        if (activity.isDestroyed || activity.isFinishing) return false
        if (requesting && requester?.get() === activity) return false
        requesting = true
        requester = WeakReference(activity)
        return true
    }

    /**
     * 复位在途标记：**只在发起者仍是本实例时复位**。
     * 回调按实例注册，陈旧实例的回调不该清掉更新实例的在途状态。
     */
    private fun finishRequest() {
        if (requester?.get() === activity) {
            requesting = false
            requester = null
        }
    }

    private fun toastDenied(permission: String) {
        activity.showToast(activity.getString(R.string.permission_denied_toast, permission))
    }

    private fun toastError(permission: String) {
        activity.showToast(activity.getString(R.string.permission_error_toast, permission))
    }

    companion object {
        /** 申请互斥标记：[start] 与 [request] 共用（进程内） */
        private var requesting = false

        /** 在途请求的发起者：复位绑定到它的生命周期，宿主实例消亡即在途请求随之作废 */
        private var requester: WeakReference<Activity>? = null

        /** 冷启动自动申请是否已发生过：进程内一次，onResume 不再触发申请 */
        private var autoRequested = false

        /**
         * 宿主实例销毁时撤销属于它的在途请求（MainActivity.onDestroy 调用）：
         * 该实例的 ActivityResultLauncher 已随之消亡、回调不会再来，标记留 true 会把阻塞页卡死。
         * 只清理属于该实例的请求，不碰更新实例的在途状态。
         */
        fun onHostDestroyed(activity: Activity) {
            val current = requester?.get()
            if (current == null || current === activity) {
                requesting = false
                requester = null
            }
        }

        /** 冷启动自动申请（进程内最多一次）：只在 MainActivity.onCreate 调用 */
        fun startAutoRequestOnce(activity: Activity, onFinished: (Boolean) -> Unit = {}) {
            if (autoRequested) return
            autoRequested = true
            PermissionUtils(activity).start(onFinished)
        }

        /**
         * 纯查询：4 项权限是否全部授予。**无任何副作用** —— 不申请、不跳转，
         * 供 onResume 与权限页驱动阻塞门（见 MainActivity.refreshPermissionGate）。
         */
        fun areAllGranted(activity: Activity): Boolean = XXPermissions.isGrantedPermissions(
            activity,
            AppPermission.values().map { it.toPermission() },
        )

        /** 纯查询：单项是否已授予 */
        fun isGranted(activity: Activity, kind: AppPermission): Boolean =
            XXPermissions.isGrantedPermission(activity, kind.toPermission())

        /** 纯查询：单项是否被「不再询问」永久拒绝（页面据此决定是否给「去系统设置」入口） */
        fun isDoNotAskAgain(activity: Activity, kind: AppPermission): Boolean =
            XXPermissions.isDoNotAskAgainPermission(activity, kind.toPermission())
    }
}
