package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.health.RemoveHealthRootCheck
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object HookHealth : Hooker {
    override fun onHook() {

        //移除Root检测对话框
        if (prefs(ModulePrefs).getBoolean("remove_health_root_check_dialog", false)) {
            loadHooker(RemoveHealthRootCheck)
        }

    }
}