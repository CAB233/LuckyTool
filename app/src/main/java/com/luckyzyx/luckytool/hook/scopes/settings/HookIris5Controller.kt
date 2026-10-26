package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object HookIris5Controller : Hooker {
    override fun onHook() {
        val isVideoFrameInsertion = true
        //prefs(ModulePrefs).getBoolean("video_display_enhancement_support_2K120", false)
        val isVideoDisplayEnhancement = true
        //prefs(ModulePrefs).getBoolean("video_super_resolution_support_2K120", false)
        val isVideoSuperResolution = true

        //Source Iris5MotionFluencySwitchController
        "com.oplus.settings.feature.display.controller.Iris5MotionFluencySwitchController".toClass()
            .resolve().apply {
                firstMethod { name = "is2kReject" }.hook {
                    if (isVideoFrameInsertion) {
                        replaceTo(false)
                    }
                }
                firstMethod { name = "isSupport120With2K" }.hook {
                    if (isVideoFrameInsertion) {
                        replaceTo(true)
                    }
                }
            }
        //Source Iris5MotionFluencyController
        "com.oplus.settings.feature.display.controller.Iris5MotionFluencyController".toClass()
            .resolve().apply {
                firstMethod { name = "is2kReject" }.hook {
                    if (isVideoFrameInsertion) {
                        replaceTo(false)
                    }
                }
                firstMethod { name = "isSupport120With2K" }.hook {
                    if (isVideoFrameInsertion) {
                        replaceTo(true)
                    }
                }
            }
        //Source Iris5VideoDisplayEnhancementController
        "com.oplus.settings.feature.display.controller.Iris5VideoDisplayEnhancementController".toClass()
            .resolve().apply {
                firstMethod { name = "is2kReject" }.hook {
                    if (isVideoDisplayEnhancement) {
                        replaceTo(false)
                    }
                }
                firstMethod { name = "isSupport120With2K" }.hook {
                    if (isVideoDisplayEnhancement) {
                        replaceTo(true)
                    }
                }
            }
        //Source Iris5VideoSuperResolutionController
        "com.oplus.settings.feature.display.controller.Iris5VideoSuperResolutionController".toClass()
            .resolve().apply {
                firstMethod { name = "is2kReject" }.hook {
                    if (isVideoSuperResolution) {
                        replaceTo(false)
                    }
                }
                firstMethod { name = "isSupport120With2K" }.hook {
                    if (isVideoSuperResolution) {
                        replaceTo(true)
                    }
                }
            }
    }
}