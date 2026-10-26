package com.luckyzyx.luckytool.hook.scopes.speechassist

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object ForceEnableAISpeechAssistCall : Hooker {
    override fun onHook() {
        //Source AiCallCommonBean
        "com.heytap.speechassist.aicall.setting.config.AiCallCommonBean".toClass().resolve().apply {
            firstMethod { name = "getSupportAiCall" }.hook {
                replaceTo(true)
            }
            firstMethod { name = "getSupportAiCallV2" }.hook {
                replaceTo(true)
            }
        }
    }
}