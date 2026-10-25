package com.luckyzyx.luckytool.hook.scopes.systemui

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
object RemoveRotateScreenButton : YukiBaseHooker() {
    override fun onHook() {
        //Source FloatingRotationButton
        VariousClass(
            "com.android.systemui.statusbar.phone.FloatingRotationButton", //A11
            "com.android.systemui.navigationbar.gestural.FloatingRotationButton", //A12
            "com.android.systemui.shared.rotation.FloatingRotationButton" //C13 C14 C15 C16 C17
        ).toClass().resolve().apply {
            //C13-C17 都有 updateDimensionResources，且调用点在 mKeyButtonView 赋值之后，
            //在这里隐藏即可；A11 A12 没有该方法，只能退回构造器（这两个版本的
            //mKeyButtonView 在构造器内部就已赋值）。
            //C17 的 FloatingRotationButton 连构造器都没有，逻辑内联在 NavigationBarView 里，
            //所以构造器钩子必须用 OrNull：firstConstructor 匹配不到会直接抛异常。
            if (firstMethodOrNull { name = "updateDimensionResources"; emptyParameters() } != null) {
                firstMethod { name = "updateDimensionResources"; emptyParameters() }.hook {
                    after {
                        firstField { name = "mKeyButtonView" }.of(instance).get<View>()
                            ?.isVisible = false
                    }
                }
            } else {
                firstConstructorOrNull {
                    parameters { it.isNotEmpty() && it[0] == classOf<Context>() }
                }?.hook {
                    after {
                        firstField { name = "mKeyButtonView" }.of(instance).get<View>()
                            ?.isVisible = false
                    }
                }
            }
        }
    }
}