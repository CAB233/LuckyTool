package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object RemoveAppUpdateGreenDot : Hooker {
    override fun onHook() {
        val mode = prefs(ModulePrefs).getString("set_app_update_dot_display_mode", "0")
        if (mode != "2") return

        //Source BubbleTextView
        "com.android.launcher3.BubbleTextView".toClass().resolve().apply {
            firstMethod { name = "isShouldShowGreenDot" }.hook {
                replaceTo(false)
            }
        }
    }
}