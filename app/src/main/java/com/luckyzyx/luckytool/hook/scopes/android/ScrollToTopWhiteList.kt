package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.hook.core.*

object ScrollToTopWhiteList : Hooker {
    override fun onHook() {
        val mode = prefs(ModulePrefs).getString("set_click_statusbar_scroll_to_top_mode", "0")

        //Source OplusScrollToTopRusHelper -> OplusScrollToTopSystemManager
        "com.android.server.OplusScrollToTopRusHelper".toClass().resolve().apply {
            firstMethodOrNull { name = "isInWhiteList" }?.hook {
                before {
                    when (mode) {
                        "1" -> result = false
                        "2" -> result = true
                    }
                }
            }
        }
    }
}