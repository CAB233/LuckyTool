package com.luckyzyx.luckytool.hook.scopes.camera

import android.text.Spanned
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.A13
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.SDK
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class RemoveWatermarkWordLimit(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        //Source CameraSubSettingFragment -> camera_namelength_outofrange -> filter
        //Source CameraSloganSettingFragment -> camera_namelength_outofrange -> filter
        dexKitBridge.findMethod {
            matcher {
                name("filter")
                paramTypes(
                    classOf<CharSequence>(), classOf<Int>(), classOf<Int>(),
                    classOf<Spanned>(), classOf<Int>(), classOf<Int>()
                )
                returnType(classOf<CharSequence>())
                usingStrings("")
                addInvoke {
                    paramCount(2..3)
                    returnType(Void.TYPE)
                }
            }
        }.apply {
            val onlyOne = SDK >= A13
            checkDataList("RemoveWatermarkWordLimit", onlyOne)
            if (onlyOne.not() && size == 2) {
                forEach {
                    it.className.toClass().resolve().apply {
                        firstMethod {
                            name = "filter"
                            parameters(
                                CharSequence::class, Int::class, Int::class,
                                Spanned::class, Int::class, Int::class
                            )
                            returnType = CharSequence::class
                        }.hook {
                            before {
                                result = args().first().cast<CharSequence>() ?: return@before
                            }
                        }
                    }
                }
            } else {
                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = "filter"
                        parameters(
                            CharSequence::class, Int::class, Int::class,
                            Spanned::class, Int::class, Int::class
                        )
                        returnType = CharSequence::class
                    }.hook {
                        before {
                            result = args().first().cast<CharSequence>() ?: return@before
                        }
                    }
                }
            }
        }
    }
}



