package com.luckyzyx.luckytool.hook.scopes.launcher

import android.view.View
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.hook.core.*

object RemoveLauncherCardName : Hooker {
    override fun onHook() {
        val osCode = getOSVersionCode
        if (osCode >= 30) loadHooker(LauncherCardName)
        else loadHooker(LauncherCardNameV13)
    }

    object LauncherCardName : Hooker {
        override fun onHook() {
            //Source CardNameHelper
            "com.android.launcher3.card.utils.CardNameHelper".toClass().resolve().apply {
                firstMethod { name = "initCardName" }.hook {
                    after {
                        val cardName = args().first().cast<View>() ?: return@after
                        cardName.isVisible = false
                        cardName.asResolver().firstMethod {
                            name = "setTextVisibility"; parameters(Boolean::class)
                        }.invoke(false)

                        val card = firstField { name = "card" }.of(instance).get<View>()
                            ?: return@after
                        card.asResolver().firstMethodOrNull {
                            name = "setTextVisible"; superclass()
                        }?.invoke(false)
                    }
                }
            }
        }
    }

    object LauncherCardNameV13 : Hooker {
        override fun onHook() {
            //Source TitleCardView
            "com.android.launcher3.card.TitleCardView".toClass().resolve().apply {
                firstMethod { name = "initCardName" }.hook {
                    after {
                        val cardName = firstField { name = "cardName" }.of(instance).get<View>()
                            ?: return@after
                        cardName.isVisible = false
                        cardName.asResolver().firstMethod {
                            name = "setTextVisibility"; parameters(Boolean::class)
                        }.invoke(false)
                    }
                }
            }

            //Source USCardContainerView
            "com.android.launcher3.card.uscard.USCardContainerView".toClass().resolve().apply {
                firstMethod { name = "initCardName" }.hook {
                    after {
                        val cardName = firstField { name = "cardName" }.of(instance).get<View>()
                            ?: return@after
                        cardName.isVisible = false
                        cardName.asResolver().firstMethod {
                            name = "setTextVisibility"; parameters(Boolean::class)
                        }.invoke(false)
                    }
                }
            }
        }
    }
}