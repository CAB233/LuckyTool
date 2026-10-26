package com.luckyzyx.luckytool.hook.scopes.screenshot

import android.graphics.Bitmap
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class EnablePNGSaveFormat(val dexKitBridge: DexKitBridge) : Hooker {

    override fun onHook() {
        //Source ImageFileFormat -> JPEG / PNG
        dexKitBridge.findClass {
            matcher {
                fields {
                    addForType(classOf<String>())
                    addForType(classOf<Bitmap.CompressFormat>())
                }
                methods {
                    add { name("values") }
                    add { returnType(classOf<String>()) }
                    add { returnType(classOf<Bitmap.CompressFormat>()) }
                }
                usingStrings("image/jpeg", "image/png")
            }
        }.apply {
            checkDataList("EnablePNGSaveFormat")
            single().name.toClass().resolve().apply {
                method { returnType = String::class }.hookAll {
                    after {
                        result = when (result<String>()) {
                            "image/jpeg" -> "image/png"
                            ".jpg" -> ".png"
                            else -> return@after
                        }
                    }
                }
                firstMethod { returnType = classOf<Bitmap.CompressFormat>() }.hook {
                    after {
                        result = when (result<Bitmap.CompressFormat>()) {
                            Bitmap.CompressFormat.JPEG -> Bitmap.CompressFormat.PNG
                            else -> return@after
                        }
                    }
                }
            }
        }
    }
}