package com.luckyzyx.luckytool.hook.hookers

import com.luckyzyx.luckytool.hook.scopes.opluskeyboard.ForceEnableOplusKeyboardBlur
import com.luckyzyx.luckytool.utils.DexkitUtils
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object HookOplusKeyboard : Hooker {
    override fun onHook() {
        //强制启用模糊效果
        val forceEnableBlur =
            prefs(ModulePrefs).getBoolean("force_enable_oplus_keyboard_blur", false)
        //诊断日志：确认 hooker 是否进到键盘进程、读到的开关值以及宿主 APK 路径
        XLog.info(
            "OplusKeyboard hook: forceEnableBlur = $forceEnableBlur, sourceDir = ${appInfo.sourceDir}",
            tag = "LuckyTool"
        )
        if (forceEnableBlur) {
            DexkitUtils.create(appInfo.sourceDir) { loadHooker(ForceEnableOplusKeyboardBlur(it)) }
        }
    }
}
