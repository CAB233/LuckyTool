package com.luckyzyx.luckytool.hook.scopes.settings

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object DisableAccessibilityDialog : Hooker {
    override fun onHook() {
        //Source SpecialPermRiskConfirmHelper
        "com.oplus.settings.applications.specialaccess.SpecialPermRiskConfirmHelper".toClass()
            .resolve().apply {
                firstMethod {
                    name = "show"
                    returnType = Boolean::class
                }.replaceAny {
                    val runnale = args().last().any() as? Runnable
                    runnale?.run()
                    true
                }
            }
    }
}