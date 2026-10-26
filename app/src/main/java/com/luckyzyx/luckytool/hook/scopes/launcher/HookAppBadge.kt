package com.luckyzyx.luckytool.hook.scopes.launcher

import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class HookAppBadge(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 30) loadHooker(AppBadge(dexKitBridge))
        else loadHooker(AppBadgeC13)
    }

    class AppBadge(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            val osCode = getOSVersionCode
            val isShortcut = prefs(ModulePrefs).getBoolean("remove_app_shortcut_badge", false)
            val isWork = prefs(ModulePrefs).getBoolean("remove_app_work_badge", false)
            val isClone = prefs(ModulePrefs).getBoolean("remove_app_clone_badge", false)

            //Source BitmapInfo
            "com.android.launcher3.icons.BitmapInfo".toClass().resolve().apply {
                firstMethod { name = "applyFlags" }.hook {
                    before {
                        val drawableCreationFlags = args(arguments.indexOfFirst { it is Int }).cast<Int>() ?: 0
                        val badgeInfo = firstField { name = "badgeInfo" }.of(instance).get()
                        val flag = firstField { name = "flags" }.of(instance).get<Int>()
                            ?: return@before
                        //flag & 2 != 0 -> ic_instant_app_badge 即时应用程序
                        //flag & 16 != 0 -> ic_archive_app_badge 存档应用程序
                        //flag & 4 != 0 -> ic_oplus_clone_app_badge 分身应用程序
                        //flag & 1 != 0 -> ic_work_app_badge 工作应用程序
                        if ((drawableCreationFlags and 2) == 0) {
                            if (badgeInfo != null) {
                                if (isShortcut) result = null
                            }
                            if ((flag and 2) != 0) {
                                //ic_instant_app_badge
                                //result = null
                            }
                            if ((flag and 16) != 0) {
                                //ic_archive_app_badge
                                //result = null
                            }
                            if ((flag and 4) == 0) {
                                if ((flag and 1) != 0) {
                                    //ic_work_app_badge
                                    if (isWork) result = null
                                } else if ((flag and 4) != 0) {
                                    //ic_clone_app_badge
                                    if (isClone) result = null
                                }
                            } else {
                                //ic_oplus_clone_app_badge_new
                                if (isClone) result = null
                            }
                        }
                    }
                }
            }

            //Source CacheUtils
            //C16 及以下类名与方法名均未混淆，按名字定位；C17 起类名被混淆为 b(UserHandle)，只能经 DexKit 定位
            if (osCode >= 40) {
                dexKitBridge.findMethod {
                    matcher {
                        paramTypes(classOf<UserHandle>())
                        returnType(classOf<Drawable>())
                    }
                }.apply {
                    checkDataList("getCloneAppDrawable")
                }.single().getMethodInstance(hostClassLoader!!).hook {
                    after {
                        if (isClone) result = null
                    }
                }
            } else {
                "com.android.common.util.CacheUtils".toClass().resolve()
                    .firstMethod {
                        name = "getCloneAppDrawable"
                        returnType = Drawable::class
                    }.hook {
                        after {
                            if (isClone) result = null
                        }
                    }
            }
        }
    }

    object AppBadgeC13 : Hooker {
        override fun onHook() {
            val isShortcut = prefs(ModulePrefs).getBoolean("remove_app_shortcut_badge", false)
            val isWork = prefs(ModulePrefs).getBoolean("remove_app_work_badge", false)
            val isClone = prefs(ModulePrefs).getBoolean("remove_app_clone_badge", false)

            //Source BitmapInfo
            "com.android.launcher3.icons.BitmapInfo".toClass().resolve().apply {
                firstMethod { name = "applyFlags"; parameterCount = 3 }.hook {
                    before {
                        val drawableCreationFlags = args().last().cast<Int>() ?: 0
                        val badgeInfo = firstField { name = "badgeInfo" }.of(instance).get()
                        val flag = firstField { name = "flags" }.of(instance).get<Int>()
                            ?: return@before
                        if ((drawableCreationFlags and 2) == 0) {
                            if (badgeInfo != null) {
                                if (isShortcut) result = null
                            } else if ((flag and 2) != 0) {
                                //ic_instant_app_badge
                                //result = null
                            } else if ((flag and 1) != 0) {
                                //ic_work_app_badge
                                if (isWork) result = null
                            } else if ((flag and 4) != 0) {
                                //ic_oplus_clone_app_badge
                                if (isClone) result = null
                            }
                        }
                    }
                }
            }
        }
    }
}