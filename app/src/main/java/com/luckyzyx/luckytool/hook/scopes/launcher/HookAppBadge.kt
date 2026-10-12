package com.luckyzyx.luckytool.hook.scopes.launcher

import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

@Obfuscate
class HookAppBadge(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 30) loadHooker(AppBadge(dexKitBridge))
        else loadHooker(AppBadgeC13)
    }

    @Obfuscate
    class AppBadge(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            val isShortcut = preferences(ModulePrefs).getBoolean("remove_app_shortcut_badge", false)
            val isWork = preferences(ModulePrefs).getBoolean("remove_app_work_badge", false)
            val isClone = preferences(ModulePrefs).getBoolean("remove_app_clone_badge", false)

            //Source BitmapInfo
            "com.android.launcher3.icons.BitmapInfo".toClass().resolve().apply {
                firstMethod { name = "applyFlags" }.hook {
                    before {
                        val drawableCreationFlags = arg(args.indexOfFirst { it is Int }).get<Int>() ?: 0
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

            // CacheUtils.getCloneAppDrawable：C16 及以下类名与方法名均未混淆，优先按名字定位；
            // C17 起类名被混淆为 b(UserHandle)，名字定位失败时才回退到 DexKit。
            // 两版都找不到时只记日志，不再抛异常中断其他 hook。
            val cloneAppDrawable = "com.android.common.util.CacheUtils".toClassOrNull()?.resolve()
                ?.firstMethodOrNull {
                    name = "getCloneAppDrawable"
                    returnType = Drawable::class
                }
            if (cloneAppDrawable != null) cloneAppDrawable.hook {
                after {
                    if (isClone) result = null
                }
            } else {
                val method = dexKitBridge.findMethod {
                    matcher {
                        paramTypes(classOf<UserHandle>())
                        returnType(classOf<Drawable>())
                    }
                }.checkDataList("getCloneAppDrawable").firstOrNull()
                    ?.getMethodInstance(hostClassLoader!!)
                if (method != null) method.hook {
                    after {
                        if (isClone) result = null
                    }
                } else YLog.error(
                    "getCloneAppDrawable -> findMethod isNullOrEmpty", tag = "LuckyTool"
                )
            }
        }
    }

    @Obfuscate
    object AppBadgeC13 : YukiBaseHooker() {
        override fun onHook() {
            val isShortcut = preferences(ModulePrefs).getBoolean("remove_app_shortcut_badge", false)
            val isWork = preferences(ModulePrefs).getBoolean("remove_app_work_badge", false)
            val isClone = preferences(ModulePrefs).getBoolean("remove_app_clone_badge", false)

            //Source BitmapInfo
            "com.android.launcher3.icons.BitmapInfo".toClass().resolve().apply {
                firstMethod { name = "applyFlags"; parameterCount = 3 }.hook {
                    before {
                        val drawableCreationFlags = lastArg().get<Int>() ?: 0
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