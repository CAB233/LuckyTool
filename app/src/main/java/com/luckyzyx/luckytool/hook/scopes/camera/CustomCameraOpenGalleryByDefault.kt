package com.luckyzyx.luckytool.hook.scopes.camera

import android.net.Uri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class CustomCameraOpenGalleryByDefault(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val gallery = prefs(ModulePrefs).getString("custom_camera_open_gallery_by_default", "")
        if (gallery.isBlank()) return

        //Source GalleryUtil
        dexKitBridge.findClass {
            matcher {
                addFieldForType(classOf<Uri>())
                addFieldForType(classOf<Boolean>())
                addMethod { paramCount(0);returnType(classOf<String>()) }
                usingStrings("content://com.color.provider.removableapp", "removableapp")
            }
        }.apply {
            checkDataList("CustomCameraOpenGalleryByDefault Clazz")
            findMethod {
                matcher {
                    paramCount(0)
                    returnType(classOf<String>())
                    usingStrings("com.oplus.gallery.base")
                }
            }.apply {
                checkDataList("CustomCameraOpenGalleryByDefault Method")
                single().className.toClass().resolve().apply {
                    firstMethod {
                        name = single().methodName
                        emptyParameters()
                        returnType = String::class
                    }.hook {
                        before {
                            if (gallery.isNotBlank()) result = gallery
                        }
                    }
                }
            }
        }
    }
}