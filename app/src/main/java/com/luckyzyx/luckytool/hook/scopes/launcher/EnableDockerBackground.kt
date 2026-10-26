package com.luckyzyx.luckytool.hook.scopes.launcher

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object EnableDockerBackground : Hooker {
    override fun onHook() {
        //Source ScreenUtils
        "com.android.common.util.ScreenUtils".toClass().resolve().apply {
            firstMethod { name = "isSupportDockerExpandScreen" }.hook {
                replaceTo(true)
            }
            //OplusTaskHeaderView showSplitWindowIcon
//            firstMethod { name = "hasLargeDisplayFeatures" }.hook {
//                replaceToTrue()
//            }
        }
    }
}