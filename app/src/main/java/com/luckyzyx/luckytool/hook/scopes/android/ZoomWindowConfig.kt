package com.luckyzyx.luckytool.hook.scopes.android

import android.os.Bundle
import android.util.ArraySet
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

class ZoomWindowConfig : Hooker {

    var callback: ((key: String, value: Any) -> Unit)? = null

    var mode = "0"
    val list = ArraySet<String>()

    var multiWindow = false
    var multiNum = 2

    fun loadData() {
        mode = prefs(ModulePrefs).getString("custom_app_floating_window_display_mode", "0")
        list.addAll(prefs(ModulePrefs).getStringSet("zoom_window_support_list", ArraySet()))

        Channel.wait<String>("custom_app_floating_window_display_mode") {
            mode = it
            XLog.debug("update zoom window configs status -> $it")
        }

        Channel.wait("zoom_window_support_list") {
            val new = prefs(ModulePrefs).getStringSet("zoom_window_support_list", ArraySet())
            XLog.debug("update zoom window whitelist configs -> ${list.size} | ${new.size}")
            list.clear()
            list.addAll(new)
        }

        multiWindow = prefs(ModulePrefs).getBoolean("force_enable_multi_window_mode", false)
        Channel.wait<Boolean>("force_enable_multi_window_mode") { multiWindow = it }
        multiNum = prefs(ModulePrefs).getInt("custom_multi_window_display_upper_limit", 2)
        Channel.wait<Int>("custom_multi_window_display_upper_limit") { multiNum = it }

        XLog.debug("init zoom window configs success -> ${list.size}")
    }

    override fun onHook() {
        loadData()

        val osCode = getOSVersionCode
        loadHooker(HookZoomWindow())
        if (osCode >= 33) loadHooker(HookFlexibleWindow())
    }

    inner class HookZoomWindow : Hooker {
        override fun onHook() {
            //Source OplusZoomWindowConfig
            "com.android.server.wm.OplusZoomWindowConfig".toClass().resolve().apply {
                firstMethod {
                    name = "isSupportZoomMode"
                    parameters(String::class, Int::class, String::class, Bundle::class)
                }.hook {
                    before {
                        when (mode) {
                            "1" -> result = false
                            "2" -> result = true
                            "3" -> {
                                val target = args().first().cast<String>() ?: ""
                                val packName = if (target.contains("/").not()) target
                                else target.split("/")[0]
                                if (list.contains(packName)) result = true
                            }
                        }
                    }
                }
            }
        }
    }

    inner class HookFlexibleWindow : Hooker {
        override fun onHook() {
            //Source FlexibleWindowUtils
            "com.android.server.wm.FlexibleWindowUtils".toClassOrNull()?.resolve()?.apply {
                firstMethod {
                    name = "isSupportFlexibleWindow"
                    parameters(String::class, String::class)
                }.hook {
                    before {
                        when (mode) {
                            "1" -> result = false
                            "2" -> result = true
                            "3" -> {
                                val target = args().first().cast<String>() ?: ""
                                val packName = if (target.contains("/").not()) target
                                else target.split("/")[0]
                                if (list.contains(packName)) result = true
                            }
                        }
                    }
                }
            }

            //Source FlexibleWindowManagerService
            "com.android.server.wm.FlexibleWindowManagerService".toClassOrNull()?.resolve()?.apply {
                firstMethod {
                    name = "getMaxWinNum"
                    returnType = Int::class
                }.hook {
                    after {
//                        val scenario = args().first().cast<Int>() ?: 0
//                        val num = result<Int>() ?: -1
//                        XLog.debug("${method.name} -> $scenario -> $num")
                        if (multiWindow && multiNum > 0) result = multiNum
                    }
                }
            }
        }
    }
}