package com.luckyzyx.luckytool.hook.scopes.gallery

import android.text.Spanned
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class GalleryWaterMarkWordDialog(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 30) loadHooker(WaterMarkWordDialog(dexKitBridge))
        else loadHooker(WaterMarkWordLimit(dexKitBridge))
    }

    class WaterMarkWordDialog(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source CustomInfoEditDialogHelper

        }
    }

    class WaterMarkWordLimit(val dexKitBridge: DexKitBridge) : Hooker {
        override fun onHook() {
            //Source CustomInfoEditDialogHelper -> picture_editor_text_watermark_character_limit_toast
            dexKitBridge.findMethod {
                matcher {
                    name("filter")
                    paramTypes(
                        classOf<CharSequence>(), classOf<Int>(), classOf<Int>(),
                        classOf<Spanned>(), classOf<Int>(), classOf<Int>()
                    )
                    returnType(classOf<CharSequence>())
                    usingNumbers(0, 1, 2)
                    usingStrings("")
                }
            }.apply {
                checkDataList("RemoveGalleryWaterMarkWordLimit")
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