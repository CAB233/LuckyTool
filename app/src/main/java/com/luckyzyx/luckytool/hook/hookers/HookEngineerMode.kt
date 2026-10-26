package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.engineermode.UnlockSomeHiddenOptions
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object HookEngineerMode : Hooker {
    override fun onHook() {
        //解锁部分隐藏选项
        if (prefs(ModulePrefs).getBoolean("unlock_some_hidden_options",false)) {
            loadHooker(UnlockSomeHiddenOptions)
        }
    }
}