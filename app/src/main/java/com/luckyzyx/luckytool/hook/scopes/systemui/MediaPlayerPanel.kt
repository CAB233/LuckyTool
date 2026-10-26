package com.luckyzyx.luckytool.hook.scopes.systemui

import android.annotation.SuppressLint
import android.content.res.Resources
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.hook.utils.sysui.DependencyUtils
import com.luckyzyx.luckytool.hook.utils.sysui.MediaPlayerDataUtils
import com.luckyzyx.luckytool.utils.A13
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.safeOfNull
import com.luckyzyx.luckytool.hook.core.*

object MediaPlayerPanel : Hooker {
    override fun onHook() {
        //自动显示媒体播放器
        val isAutoDisplay = VariousClass(
            "com.oplusos.systemui.qs.OplusQSTileMediaContainer", //C13.1
            "com.oplus.systemui.qs.OplusQSTileMediaContainer" //C14
        ).toClassOrNull()?.let {
            it.resolve().firstMethodOrNull { name = "setMediaMode" } != null
        } ?: true

        if (isAutoDisplay) loadHooker(MediaPlayerDisplayMode)
        else loadHooker(MediaPlayerDisplayModePermanent)

        //强制开启媒体切换按钮
        if (prefs(ModulePrefs).getBoolean("force_enable_media_toggle_button", false)) {
            if (SDK == A13) loadHooker(ForceEnableMediaToggleButton)
        }
    }

    object MediaPlayerDisplayMode : Hooker {
        override fun onHook() {
            var mode = prefs(ModulePrefs).getString("set_media_player_display_mode", "0")
            Channel.wait<String>("set_media_player_display_mode") { mode = it }

            //期望状态：null 表示不干预原实现
            fun wishStatus(): Boolean? = when (mode) {
                "1" -> true
                "2" -> false
                "3" -> safeOfNull { getMediaData() != null }
                else -> null
            }

            //把期望状态补推给媒体模式监听器：宿主自己只在 无媒体↔有媒体 翻转时才回调
            fun callbackWish(listener: Any?) {
                val status = wishStatus() ?: return
                listener?.asResolver()?.firstMethodOrNull { name = "onChanged" }?.invoke(status)
            }

            //Source OplusQsMediaCarouselController（C13.0~C16）
            //该类存在时沿用「只把期望状态推给监听器」的老路径：C13~C16 上验证过的就是这条，不要额外去改容器参数
            val carousel =
                "com.oplus.systemui.qs.media.OplusQsMediaCarouselController".toClassOrNull()
            if (carousel != null) {
                carousel.resolve().apply {
                    firstMethodOrNull { name = "setCurrentMediaData" }?.hook {
                        replaceAny {
                            val original = callOriginal()
                            callbackWish(
                                firstField { name = "mediaModeChangeListener" }.of(instance).get()
                            )
                            original
                        }
                    }
                    firstMethodOrNull { name = "setMediaModeChangeListener" }?.hook {
                        replaceAny {
                            val original = callOriginal()
                            callbackWish(args().first().any())
                            original
                        }
                    }
                }
                return
            }

            //Source OplusQsMediaControllerExImpl（C17）：取代上面的类，注册监听时会立刻回调一次当前状态
            //走到这里说明宿主已经没有 carousel 了（C17+），容器侧不会再被监听器正确驱动，只能自己兜
            "com.oplus.systemui.qs.media.OplusQsMediaControllerExImpl".toClass().resolve().apply {
                firstMethodOrNull { name = "setMediaModeChangeListener" }?.hook {
                    replaceAny {
                        val original = callOriginal()
                        callbackWish(args().first().any())
                        original
                    }
                }
            }

            //容器侧的最终开关，只改参数不接管实现
            fun hookMediaPanelShown(classNames: List<String>, methodName: String) {
                classNames.firstNotNullOfOrNull { it.toClassOrNull() }?.resolve()
                    ?.firstMethodOrNull { name = methodName }?.hook {
                        replaceAny {
                            val status = wishStatus()
                            if (status != null) args[0] = status
                            callOriginal()
                        }
                    }
            }

            //Source OplusQSContainerImpl（C13~C16 兜底，C17 已忽略参数）
            hookMediaPanelShown(
                listOf(
                    "com.oplusos.systemui.qs.OplusQSContainerImpl", //C13.1
                    "com.oplus.systemui.qs.OplusQSContainerImpl" //C14
                ), "setQsMediaPanelShown"
            )
            //Source OplusQSTileMediaContainerController
            hookMediaPanelShown(
                listOf(
                    "com.oplusos.systemui.qs.OplusQSTileMediaContainerController", //C13.1
                    "com.oplus.systemui.qs.OplusQSTileMediaContainerController" //C14+
                ), "setQsMediaPanelShown"
            )
            //Source OplusQSTileMediaContainer
            hookMediaPanelShown(
                listOf(
                    "com.oplusos.systemui.qs.OplusQSTileMediaContainer", //C13.1
                    "com.oplus.systemui.qs.OplusQSTileMediaContainer" //C14+
                ), "setMediaMode"
            )
        }
    }

    object MediaPlayerDisplayModePermanent : Hooker {
        @SuppressLint("DiscouragedApi")
        override fun onHook() {
            var mode = prefs(ModulePrefs).getString("set_media_player_display_mode", "0")
            Channel.wait<String>("set_media_player_display_mode") {
                mode = it
                ControlCenterTiles.callback?.invoke("set_media_player_display_mode", it)
            }

            //Source updateQsMediaPanelView
            VariousClass(
                "com.oplusos.systemui.qs.OplusQSTileMediaContainer", //C13.1
                "com.oplus.systemui.qs.OplusQSTileMediaContainer" //C14
            ).toClass().resolve().apply {
                firstMethod { name = "setListening" }.hook {
                    after {
                        firstMethod { name = "updateResources" }.of(instance).invoke()
                    }
                }
                firstMethod { name = "updateQsMediaPanelView" }.hook {
                    before {
                        val status = when (mode) {
                            "1" -> 0
                            "2" -> 8
                            "3" -> if (getMediaData() == null) 8 else 0
                            else -> return@before
                        }
                        val res = args().first().cast<Resources>() ?: return@before
                        val bool = args().last().cast<Boolean>() ?: return@before
                        val linear = firstField { name = "mQsMediaPanelContainer" }.of(instance)
                            .get<LinearLayout>() ?: return@before
                        val mTmpConstraintSet =
                            firstField { name = "mTmpConstraintSet" }.of(instance).get()
                                ?: return@before
                        val smallHeight = res.getIdentifier(
                            "oplus_qs_media_panel_height_smallspace", "dimen", packageName
                        ).takeIf { it != 0 } ?: return@before
                        val height = res.getIdentifier(
                            "oplus_qs_media_panel_height", "dimen", packageName
                        ).takeIf { it != 0 } ?: return@before
                        val heightSize = safeOfNull {
                            res.getDimensionPixelSize(if (bool) smallHeight else height)
                        } ?: return@before
                        mTmpConstraintSet.setVisibilitySet(linear.id, status)
                        if (status == 0) mTmpConstraintSet.constrainHeightSet(
                            linear.id, heightSize
                        )
                        result = null
                    }
                }
                firstMethod { name = "updateQsSecondTileContainer" }.hook {
                    before {
                        val isShow = when (mode) {
                            "1" -> true
                            "2" -> false
                            "3" -> getMediaData() != null
                            else -> return@before
                        }
                        val res = args().first().cast<Resources>() ?: return@before
                        val bool = args().last().cast<Boolean>() ?: return@before
                        val linear = firstField { name = "mSecondTileContainer" }.of(instance)
                            .get<LinearLayout>() ?: return@before
                        val mTmpConstraintSet =
                            firstField { name = "mTmpConstraintSet" }.of(instance).get()
                                ?: return@before
                        val smallSideMargin = res.getIdentifier(
                            "qs_footer_hl_tile_side_margin_smallspace", "dimen", packageName
                        ).takeIf { it != 0 } ?: return@before
                        val sideMargin = res.getIdentifier(
                            "qs_footer_hl_tile_side_margin", "dimen", packageName
                        ).takeIf { it != 0 } ?: return@before
                        val sideSize = safeOfNull {
                            res.getDimensionPixelSize(if (bool) smallSideMargin else sideMargin)
                        } ?: return@before
                        val guideLine = res.getIdentifier(
                            "guide_line", "id", packageName
                        ).takeIf { it != 0 } ?: return@before
                        if (isShow) {
                            val firstTile = firstField { name = "mFirstTileContainer" }.of(instance)
                                .get<LinearLayout>() ?: return@before
                            val smallContainerMargin = res.getIdentifier(
                                "qs_footer_hl_tile_two_container_margin_top_smallspace",
                                "dimen",
                                packageName
                            ).takeIf { it != 0 } ?: return@before
                            val containerMargin = res.getIdentifier(
                                "qs_footer_hl_tile_two_container_margin_top", "dimen", packageName
                            ).takeIf { it != 0 } ?: return@before
                            val containerSize = safeOfNull {
                                res.getDimensionPixelSize(if (bool) smallContainerMargin else containerMargin)
                            } ?: return@before
                            mTmpConstraintSet.connectSet(linear.id, 6, 0, 6, 0)
                            mTmpConstraintSet.connectSet(linear.id, 7, guideLine, 6, sideSize)
                            mTmpConstraintSet.connectSet(
                                linear.id, 3, firstTile.id, 4, containerSize
                            )
                        } else {
                            mTmpConstraintSet.connectSet(linear.id, 6, guideLine, 7, sideSize)
                            mTmpConstraintSet.connectSet(linear.id, 7, 0, 7, 0)
                            mTmpConstraintSet.connectSet(linear.id, 3, 0, 3, 0)
                        }
                        result = null
                    }
                }
            }
        }
    }

    fun getMediaData(): Any? {
        return MediaPlayerDataUtils(hostClassLoader!!).getMediaDataStatus()
    }

    fun Any.connectSet(startId: Int, startSide: Int, endId: Int, endSide: Int, margin: Int) {
        asResolver().firstMethod {
            name = "connect"
            parameterCount = 5
        }.invoke(startId, startSide, endId, endSide, margin)
    }

    fun Any.constrainHeightSet(viewId: Int, height: Int) {
        asResolver().firstMethod {
            name = "constrainHeight"
            parameterCount = 2
        }.invoke(viewId, height)
    }

    fun Any.setVisibilitySet(viewId: Int, visibility: Int) {
        asResolver().firstMethod {
            name = "setVisibility"
            parameterCount = 2
        }.invoke(viewId, visibility)
    }

    object ForceEnableMediaToggleButton : Hooker {
        override fun onHook() {
            //Source OplusQsMediaPanelView
            "com.oplus.systemui.qs.media.OplusQsMediaPanelView".toClass().resolve().apply {
                firstMethod { name = "bindMediaData" }.hook {
                    after {
                        args().first().any() ?: firstField { name = "mMediaOutputBtn" }.of(instance)
                            .get<ImageButton>()?.setMediaOutputBtn()
                    }
                }
            }
            //Source OplusQsMediaOutputDialog
            "com.oplus.systemui.qs.media.OplusQsMediaOutputDialog".toClass().resolve().apply {
                firstMethod { name = "bindMediaView" }.hook {
                    after {
                        args().first().any() ?: firstField { name = "mMediaOutputBtn" }.of(instance)
                            .get<ImageButton>()?.setMediaOutputBtn()
                    }
                }
            }
        }
    }

    private fun ImageButton.setMediaOutputBtn() {
        isVisible = true
        isEnabled = true
        setOnClickListener {
            val clazz = "com.android.systemui.media.dialog.MediaOutputDialogFactory".toClass()
            val mMediaOutputDialogFactory = DependencyUtils(hostClassLoader!!).getDependency(clazz)
            mMediaOutputDialogFactory?.asResolver()
                ?.firstMethod { name = "create"; parameterCount = 3 }
                ?.invoke("", true, null)
        }
    }
}