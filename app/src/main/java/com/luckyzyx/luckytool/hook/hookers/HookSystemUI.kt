package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.systemui.HookSystemUIFeature
import com.luckyzyx.luckytool.utils.DexkitUtils
import com.luckyzyx.luckytool.hook.core.*

object HookSystemUI : Hooker {
    override fun onHook() {

        DexkitUtils.create(appInfo.sourceDir) { dexKitBridge ->
            //系统界面Feature
            loadHooker(HookSystemUIFeature(dexKitBridge))

            //状态栏功能
            loadHooker(HookSystemUIStatusBar(dexKitBridge))
        }

        //锁屏
        loadHooker(HookSystemUILockScreen)

        //对话框相关
        loadHooker(HookSystemUIDialog)

        //全面屏手势相关
        loadHooker(HookSystemUIGesture)

        //指纹相关
        loadHooker(HookSystemUIFingerPrint)

        //杂项
        loadHooker(HookSystemUiMiscellaneous)

        //自启
        loadHooker(HookSystemUIAutoStart)

    }
}