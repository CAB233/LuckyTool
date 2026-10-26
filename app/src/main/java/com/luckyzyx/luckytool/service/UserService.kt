package com.luckyzyx.luckytool.service

import android.content.Context
import android.content.Intent
import android.content.pm.UserInfo
import android.os.IBinder
import android.os.IUserManager
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemProperties
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.IUserServiceController
import com.luckyzyx.luckytool.service.base.BaseControllerService
import com.luckyzyx.luckytool.utils.LogUtils
import com.topjohnwu.superuser.ipc.RootService
import java.util.LinkedList

object UserService : BaseControllerService<IUserServiceController>() {
    override val TAG: String = "UserService"
    override var controllerService: Class<*> = classOf<UserControllerService>()

    private var um: IUserManager? = null
    private var binder: IBinder? = null

    private val recipient = object : IBinder.DeathRecipient {
        override fun binderDied() {
            LogUtils.w(TAG, "DeathRecipient", "is dead", true)
            binder?.unlinkToDeath(this, 0)
            binder = null
            um = null
        }
    }

    override fun getController(iBinder: IBinder?): IUserServiceController? {
        return IUserServiceController.Stub.asInterface(iBinder)
    }

    class UserControllerService : RootService() {
        override fun onBind(intent: Intent) = object : IUserServiceController.Stub() {
            override fun getUsers(): MutableList<UserInfo> {
                return UserService.getUsers()
            }

            override fun getUserInfo(userId: Int): UserInfo? {
                return UserService.getUserInfo(userId)
            }

            override fun isUserUnlocked(userId: Int): Boolean {
                return UserService.isUserUnlocked(userId)
            }

        }
    }

    private fun getUserManager(): IUserManager? {
        if (binder == null || um == null) {
            binder = ServiceManager.getService(Context.USER_SERVICE)
            if (binder == null) return null
            try {
                binder!!.linkToDeath(recipient, 0)
            } catch (e: RemoteException) {
                LogUtils.e(TAG, "getUserManager", e.toString(), true)

            }
            um = IUserManager.Stub.asInterface(binder)
        }
        return um
    }

    fun getUsers(): MutableList<UserInfo> {
        val um = getUserManager() ?: return LinkedList()
        // IUserManager.getUsers 只有单参数重载，宿主内部即 getUsersInternal(true, excludeDying, true)，
        // 调用多参数重载会在设备上抛 NoSuchMethodError 并导致 root 进程崩溃
        val users = um.getUsers(true)
        val isLENOVO = SystemProperties.get("ro.lenovo.region").isNotBlank()
        if (isLENOVO) { // lenovo hides user [900, 910) for app cloning
            val gotUsers = BooleanArray(10)
            for (user in users) {
                val residual = user.id - 900
                if (residual in 0..9) gotUsers[residual] = true
            }
            for (i in 900..909) {
                val user = um.getUserInfo(i)
                if (user != null && !gotUsers[i - 900]) {
                    users.add(user)
                }
            }
        }
        return users
    }

    fun getUserInfo(userId: Int): UserInfo? {
        val um = getUserManager() ?: return null
        return um.getUserInfo(userId)
    }

    fun isUserUnlocked(userId: Int): Boolean {
        val um = getUserManager() ?: return false
        return um.isUserUnlocked(userId)
    }

}