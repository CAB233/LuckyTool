package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.keyguardpersonality.KeyGuardPersonalityRedMode
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookKeyguardPersonality : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //锁屏红一
        if (osCode >= 40) loadHooker(KeyGuardPersonalityRedMode)

    }
}
