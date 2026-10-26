package com.luckyzyx.luckytool.hook.scopes.launcher

import android.view.View
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveBottomAppIconOfRecentTaskList : Hooker {
    override fun onHook() {
        //Source DockView
        "com.oplus.quickstep.dock.DockView".toClass().resolve().apply {
            firstMethodOrNull { name = "setVisibilityAlpha" }?.hook {
                after {
                    instance<View>().isVisible = false
                }
            } ?: run {
                constructor { }.hookAll {
                    after {
                        instance<View>().isVisible = false
                    }
                }
            }
            firstMethodOrNull { name = "hideDockView" }?.hook {
                before {
                    args().first().set(true)
                }
            }
        }

        //Source DockViewController C14+
        "com.oplus.quickstep.dock.DockViewController".toClassOrNull()?.resolve()?.apply {
            firstMethod { name = "onRecentsViewOrientationChange" }.hook {
                before {
                    args().first().set(false)
                }
            }
            firstMethod { name = "updateOnTaskDisplayModeChange" }.hook {
                before {
                    args().first().set(true)
                }
            }
            firstMethod { name = "updateOnLauncherMultiWindowChange" }.hook {
                before {
                    args().first().set(true)
                }
            }
        }
    }
}