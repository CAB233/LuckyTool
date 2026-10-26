package com.luckyzyx.luckytool.hook.scopes.gallery

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.luckypray.dexkit.DexKitBridge
import com.luckyzyx.luckytool.hook.core.*

class ReplaceOnePlusModelWatermark(val dexKitBridge: DexKitBridge) : Hooker {
    override fun onHook() {
        val waterMark = prefs(ModulePrefs).getString("custom_model_watermark", "None")

        //Source WatermarkContent
        "com.oplus.tbluniformeditor.plugins.watermark.data.WatermarkContent".toClass().resolve()
            .apply {
                firstMethod { name = "getMake" }.hook {
                    replaceTo("")
                }
            }

        //Source MarketNameInfo -> com.oplus.camera -> ro.vendor.oplus.market.name / ro.vendor.oplus.market.enname
        dexKitBridge.findClass {
            matcher {
            addFieldForType(classOf<String>())
                addMethod { paramCount(0);returnType(classOf<String>()) }
                usingStrings(
                    "MarketNameInfo",
                    "ro.vendor.oplus.market.name",
                    "ro.vendor.oplus.market.enname"
                )
            }
        }.apply {
            checkDataList("ReplaceOnePlusModelWatermark MarketNameInfo")
            single().name.toClass().resolve().apply {
                method {
                    emptyParameters()
                    returnType = String::class
                }.hookAll {
                    before {
                        if (waterMark.isBlank() || waterMark == "None") return@before
                        result = waterMark
                    }
                }
            }
        }
    }
}