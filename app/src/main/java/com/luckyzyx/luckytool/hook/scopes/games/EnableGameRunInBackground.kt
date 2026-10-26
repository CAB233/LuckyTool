package com.luckyzyx.luckytool.hook.scopes.games

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.IntentUtils
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.utils.startMirageWindow
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class EnableGameRunInBackground(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode

        //Source HangUpUtil
        dexKitBridge.findClass {
            matcher {
                addFieldForType(classOf<List<*>>())
                addMethod { paramCount(0);returnType(classOf<Boolean>()) }
                addMethod { paramCount(0);returnType(Void.TYPE) }
                addMethod { paramTypes(classOf<Context>());returnType(Void.TYPE) }
                usingStrings("HangUpUtil", "isSupportBackgroundHangUp")
            }
        }.apply {
            checkDataList("EnableGameRunInBackground Cls")
            findMethod {
                matcher {
                    paramCount(0)
                    returnType(classOf<Boolean>())
                    usingStrings("isSupportBackgroundHangUp")
                }
            }.apply {
                checkDataList("EnableGameRunInBackground Support")
                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        emptyParameters()
                        returnType = Boolean::class
                    }.hook {
                        replaceTo(true)
                    }
                    firstMethod {
                        parameters(Context::class)
                        returnType = Void.TYPE
                    }.hook {
                        before {
                            if (osCode >= 34) {
                                startMirageWindow(null)
                            } else {
                                val context = args().first().cast<Context>() ?: return@before
                                IntentUtils(context).startBackgroundRunServiceV14()
                            }
                            result = null
                        }
                    }
                }
            }
        }
    }
}