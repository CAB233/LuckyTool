package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.A13
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object RemoveFolderPreviewBackground : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 34) loadHooker(FolderPreviewBackground)
        else loadHooker(FolderPreviewBackgroundV14)
    }

    object FolderPreviewBackground : Hooker {
        override fun onHook() {
            //Source OplusPreviewBackground
            "com.android.launcher3.folder.OplusPreviewBackground".toClass().resolve().apply {
                firstMethod { name = "setBackground" }.hook {
                    before {
                        firstField { name = "mBgDrawable" }.of(instance).set(null)
                    }
                }
            }
        }
    }

    object FolderPreviewBackgroundV14 : Hooker {
        override fun onHook() {
            //Source OplusPreviewBackground folder_icon_bg big_folder_bg
            "com.android.launcher3.folder.OplusPreviewBackground".toClass().resolve().apply {
                method { name = "setup" }.hookAll {
                    after {
                        firstField { name = "mBgDrawable" }.of(instance).set(null)
                    }
                }
                firstMethod { name = "drawBackground" }.hook {
                    intercept()
                }
            }
            if (SDK < A13) return
            //Source OplusFolderAnimationManager
            "com.android.launcher3.folder.OplusFolderAnimationManager".toClass().resolve().apply {
                firstMethod { name = "getFolderBackgroundAnimator" }.hook {
                    intercept()
                }
            }
        }
    }
}