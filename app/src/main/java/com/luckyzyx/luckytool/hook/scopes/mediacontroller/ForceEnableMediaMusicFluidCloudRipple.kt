package com.luckyzyx.luckytool.hook.scopes.mediacontroller

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.json.JSONObject
import com.luckyzyx.luckytool.hook.core.*

object ForceEnableMediaMusicFluidCloudRipple : Hooker {
    override fun onHook() {
        var isEnable =
            prefs(ModulePrefs).getBoolean("force_enable_media_music_fluid_cloud_ripple", false)
        Channel.wait<Boolean>("force_enable_media_music_fluid_cloud_ripple") { isEnable = it }

        //Source SeedlingTool
        "com.oplus.pantanal.seedling.util.SeedlingTool".toClass().resolve().apply {
            method {
                name { it.startsWith("update") && it.endsWith("Data") }
            }.hookAll {
                before {
                    if (!isEnable) return@before
                    val json = args(1).any() ?: return@before
                    if (json is JSONObject) {
                        val staticVoicePrintShow = json.optBoolean("staticVoicePrintShow", true)
                        if (staticVoicePrintShow) {
                            json.put("staticVoicePrintShow", false)
                        }
                    }
                }
            }
        }
    }
}