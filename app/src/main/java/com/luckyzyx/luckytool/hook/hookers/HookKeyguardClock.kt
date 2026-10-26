package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.keyguardclock.KeyGuardcLockRedMode
import com.luckyzyx.luckytool.utils.DexkitUtils
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookKeyguardClock : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        DexkitUtils.create(appInfo.sourceDir) { dexKitBridge ->
            //锁屏时钟
            if (osCode < 40) loadHooker(KeyGuardcLockRedMode(dexKitBridge))
        }

    }
}
