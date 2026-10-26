package com.luckyzyx.luckytool.hook.scopes.externalstorage

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.hook.core.*

object RemoveStorageLimit : Hooker {
    override fun onHook() {
        //Source ExternalStorageProvider
        "com.android.externalstorage.ExternalStorageProvider".toClass().resolve().apply {
            firstMethodOrNull { name = "shouldBlockDirectoryFromTree" }?.hook {
                replaceTo(false)
            }
        }
    }
}