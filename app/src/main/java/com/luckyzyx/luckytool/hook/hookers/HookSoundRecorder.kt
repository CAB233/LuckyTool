package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.globals.HookGlobalSystemProperties
import com.luckyzyx.luckytool.hook.scopes.soundrecorder.HookBaseUtil
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookSoundRecorder : Hooker {
    override fun onHook() {
        loadHooker(HookGlobalSystemProperties)

        val osCode = getOSVersionCode


        //启用三方应用通话录音
        if (prefs(ModulePrefs).getBoolean("enable_record_calls_on_third_party_apps", false)) {
            if (osCode == 30) loadHooker(HookBaseUtil)
        }
    }
}