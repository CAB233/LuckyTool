package com.luckyzyx.luckytool.hook.hookers

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.hook.scopes.opluskeyboard.ForceEnableOplusKeyboardBlur
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
object HookOplusKeyboard : YukiBaseHooker() {
    override fun onHook() {
        //强制启用模糊效果
        if (preferences(ModulePrefs).getBoolean("force_enable_oplus_keyboard_blur", false)) {
            loadHooker(ForceEnableOplusKeyboardBlur)
        }
    }
}
