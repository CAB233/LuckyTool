package com.luckyzyx.luckytool.hook.scopes.phonemanager

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveVirusRiskNotificationInPhoneManager(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source VirusScanNotifyListener
        dexKitBridge.findClass {
            matcher {
                fields {
                addForType(classOf<Context>())
                    addForType(classOf<String>())
                }
                methods {
                add { paramTypes(classOf<ArrayList<*>>()) }
                    add { returnType(classOf<Int>()) }
                    add { returnType(classOf<String>()) }
                }
                usingStrings("VirusScanNotifyListener")
            }
        }.apply {
            checkDataList("RemoveVirusRiskNotificationInPhoneManager")
            single().name.toClass().resolve().apply {
                method { parameters(ArrayList::class) }.hookAll {
                    intercept()
                }
            }
        }
    }
}