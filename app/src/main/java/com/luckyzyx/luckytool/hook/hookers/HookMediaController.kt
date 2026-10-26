package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.mediacontroller.ForceEnableMediaMusicFluidCloudRipple
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object HookMediaController : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //强制启用媒体音乐流体云波纹
        if (osCode >= 33) loadHooker(ForceEnableMediaMusicFluidCloudRipple)
    }
}