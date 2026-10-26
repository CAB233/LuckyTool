package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.audioeffectcenter.FixRecordCallsOnThirdPartyAppsError
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookAudioEffectCenter : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //启用三方应用通话录音
        if (prefs(ModulePrefs).getBoolean("enable_record_calls_on_third_party_apps", false)) {
            if (osCode == 30) loadHooker(FixRecordCallsOnThirdPartyAppsError)
        }
    }
}