package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.speechassist.ForceEnableAISpeechAssistCall
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookSpeechAssist : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //强制启用小布通话
        if (prefs(ModulePrefs).getBoolean("force_enable_ai_speechassist_call", false)) {
            if (osCode >= 30) loadHooker(ForceEnableAISpeechAssistCall)
        }
    }
}