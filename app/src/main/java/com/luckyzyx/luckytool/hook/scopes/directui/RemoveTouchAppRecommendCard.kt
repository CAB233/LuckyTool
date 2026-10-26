package com.luckyzyx.luckytool.hook.scopes.directui

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveTouchAppRecommendCard : Hooker {
    override fun onHook() {
        //Source DirectUIMainViewMode -> AppBean
        "com.coloros.directui.repository.datasource.AppBean".toClass().resolve().apply {
            firstMethod { name = "toCardUIInfo" }.hook {
                intercept()
            }
        }
    }
}