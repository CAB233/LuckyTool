package com.luckyzyx.luckytool.hook.scopes.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object HookAlertSlider : Hooker {
    override fun onHook() {
        //Source AlertSliderAudioPolicy
        "com.android.server.audio.AlertSliderAudioPolicy".toClass().resolve().apply {
            firstMethod { name = "setUp" }.hook {
                before {
                    XLog.debug("${method.name} is call")
                    result = null
                }
            }
            firstMethod { name = "setMiddle" }.hook {
                before {
                    XLog.debug("${method.name} is call")
                    result = null
                }
            }
            firstMethod { name = "setDown" }.hook {
                before {
                    XLog.debug("${method.name} is call")
                    result = null
                }
            }
        }
    }
}