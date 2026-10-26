package com.luckyzyx.luckytool.hook.scopes.securitypermission

import android.app.Activity
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.EcmUtils
import com.luckyzyx.luckytool.hook.core.*

object AutoUnlockAppEcmPermissionRestrict : Hooker {
    override fun onHook() {
        //Source PermissionGroupsActivity
        "com.oplusos.securitypermission.permission.PermissionGroupsActivity".toClass().resolve()
            .apply {
                firstMethod { name = "onCreate" }.hook {
                    before {
                        val activity = instance<Activity>()
                        val packName = activity.intent.getStringExtra("packageName")
                            ?: activity.intent.getStringExtra("mPackageName")
                            ?: return@before
                        EcmUtils(activity).autoUnlockRestrictedSettings(packName)
                    }
                }
            }
    }
}