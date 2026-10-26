package com.luckyzyx.luckytool.hook.scopes.mms

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveMmsBottomInputBoxMenu : Hooker {
    override fun onHook() {
        //Source MenuInfoBaseBean
        "com.opos.smart.mms.interfaces.netmsg.menu.MenuInfoBaseBean".toClass().resolve().apply {
            firstMethod {
                name = "getMenus"
                returnType = List::class
            }.hook {
                replaceTo(ArrayList<Any>())
            }
        }
    }
}