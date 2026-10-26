package com.luckyzyx.luckytool.hook.scopes.systemui

import android.view.View
import android.view.ViewGroup
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.VariousClass
import com.luckyzyx.luckytool.hook.utils.sysui.MediaPlayerDataUtils
import com.luckyzyx.luckytool.hook.utils.sysui.QSFeatureOptionUtils
import com.luckyzyx.luckytool.utils.A13
import com.luckyzyx.luckytool.utils.ModulePrefs
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.getOSVersionCode
import com.luckyzyx.luckytool.utils.getScreenOrientation
import com.luckyzyx.luckytool.hook.core.*

object ControlCenterTiles : Hooker {
    var callback: ((key: String, value: String) -> Unit)? = null

    override fun onHook() {
        val isEnable = prefs(ModulePrefs).getBoolean("control_center_tile_enable", false)
        if (!isEnable) return
        if (SDK >= A13) loadHooker(ControlCenterTilesLayout)
        else loadHooker(ControlCenterTilesLayoutC12)
    }

    object ControlCenterTilesLayout : Hooker {
        override fun onHook() {
            val osCode = getOSVersionCode

            val columnUnexpandedVerticalC13 =
                prefs(ModulePrefs).getInt("tile_unexpanded_columns_vertical_c13", 5)
            val rowExpandedVerticalC13 =
                prefs(ModulePrefs).getInt("tile_expanded_rows_vertical_c13", 3)
            val columnExpandedVerticalC13 =
                prefs(ModulePrefs).getInt("tile_expanded_columns_vertical_c13", 4)
            val columnHorizontal = prefs(ModulePrefs).getInt("tile_columns_horizontal_c13", 4)
            //媒体播放器模式
            var mediaMode = prefs(ModulePrefs).getString("set_media_player_display_mode", "0")
            //自动扩展
            var autoExpandTile = osCode >= 30 && prefs(ModulePrefs).getBoolean(
                "auto_expand_tile_rows_horizontal", false
            )
            Channel.wait<Boolean>("auto_expand_tile_rows_horizontal") { autoExpandTile = it }

            callback = { key: String, value: String ->
                when (key) {
                    "set_media_player_display_mode" -> mediaMode = value
                }
            }

            //期望行数：竖屏取设置值，横屏按自动扩展规则，返回 null 表示不改
            val resolveRows: (View) -> Int? = { view ->
                var vertical = true
                getScreenOrientation(view) { vertical = it }
                if (vertical) rowExpandedVerticalC13 else when {
                    !autoExpandTile -> null
                    QSFeatureOptionUtils(hostClassLoader!!).isSupportVolumeSeekBar() -> null
                    mediaMode == "2" -> 2
                    mediaMode == "3" -> {
                        if (MediaPlayerDataUtils(hostClassLoader!!).getMediaDataStatus() == null) 2
                        else null
                    }
                    else -> null
                }
            }

            //Source QuickQSPanel
            VariousClass(
                "com.android.systemui.qs.QuickQSPanel",
                "com.android.systemui.qs.deprecated.QuickQSPanel"
            ).toClass().resolve().apply {
                firstMethod { name = "getNumQuickTiles" }.replaceAny {
                    columnUnexpandedVerticalC13
                }
            }

            //Source TileLayout
            VariousClass(
                "com.android.systemui.qs.TileLayout",
                "com.android.systemui.qs.deprecated.TileLayout"
            ).toClass().resolve().apply {
                val updateMaxRowsMethod = firstMethodOrNull { name = "updateMaxRows" }
                if (updateMaxRowsMethod != null) {
                    val mRows = firstField { name = "mRows" }
                    updateMaxRowsMethod.replaceAny {
                        val newRows = resolveRows(instance<ViewGroup>())
                            ?: return@replaceAny callOriginal()
                        val field = mRows.copy().of(instance)
                        val oldRows = field.get<Int>() ?: return@replaceAny callOriginal()
                        field.set(newRows)
                        oldRows != newRows
                    }
                } else {
                    //ColorOS17 移除了 updateMaxRows：行数改由 PagedTileLayout.onMeasure 按可用高度计算，
                    //再夹取到 [mMinRows, mMaxAllowedRows]，故测量前临时改写首页这两个字段，测量后还原
                    //mMinRows/mMaxAllowedRows 声明在 TileLayout，页面实例本身是 TileLayout 子类，
                    //故这两个字段从 TileLayout 取，只有 mPages 来自 PagedTileLayout
                    val mMinRows = firstFieldOrNull { name = "mMinRows" } ?: return@apply
                    val mMaxAllowedRows =
                        firstFieldOrNull { name = "mMaxAllowedRows" } ?: return@apply
                    val pagedScope = VariousClass(
                        "com.android.systemui.qs.PagedTileLayout",
                        "com.android.systemui.qs.deprecated.PagedTileLayout"
                    ).toClassOrNull()?.resolve() ?: return@apply
                    val mPages = pagedScope.firstFieldOrNull { name = "mPages" } ?: return@apply
                    pagedScope.firstMethod { name = "onMeasure" }.replaceAny {
                        //onMeasure 无返回值，恒走原实现，仅在测量期间临时改字段
                        val view = instanceOrNull<ViewGroup>() ?: return@replaceAny callOriginal()
                        val newRows = resolveRows(view) ?: return@replaceAny callOriginal()
                        val page = mPages.copy().of(instance).get<ArrayList<*>>()
                            ?.firstOrNull() ?: return@replaceAny callOriginal()
                        val minRows = mMinRows.copy().of(page)
                        val maxAllowedRows = mMaxAllowedRows.copy().of(page)
                        val oldMinRows = minRows.get<Int>() ?: return@replaceAny callOriginal()
                        val oldMaxAllowedRows =
                            maxAllowedRows.get<Int>() ?: return@replaceAny callOriginal()
                        //已是目标行数时不必重写字段，直接走原实现
                        if (oldMinRows == newRows && oldMaxAllowedRows == newRows) {
                            return@replaceAny callOriginal()
                        }
                        minRows.set(newRows)
                        maxAllowedRows.set(newRows)
                        try {
                            callOriginal()
                        } finally {
                            minRows.set(oldMinRows)
                            maxAllowedRows.set(oldMaxAllowedRows)
                        }
                    }
                }

                val mColumns = firstField { name = "mColumns" }
                (firstMethodOrNull { name = "updateColumns" }
                    ?: firstMethod { name = "setMaxColumns" }).replaceAny {
                    val view = instance<ViewGroup>()
                    var vertical = true
                    getScreenOrientation(view) { vertical = it }
                    val newColumns =
                        if (vertical) columnExpandedVerticalC13 else columnHorizontal
                    val field = mColumns.copy().of(view)
                    val oldColumns = field.get<Int>() ?: return@replaceAny callOriginal()
                    field.set(newColumns)
                    oldColumns != newColumns
                }
            }
        }
    }

    object ControlCenterTilesLayoutC12 : Hooker {
        override fun onHook() {
            val columnUnexpandedVertical =
                prefs(ModulePrefs).getInt("tile_unexpanded_columns_vertical", 6)
            val columnUnexpandedHorizontal =
                prefs(ModulePrefs).getInt("tile_unexpanded_columns_horizontal", 6)
            val columnExpandedVertical =
                prefs(ModulePrefs).getInt("tile_expanded_columns_vertical", 4)
            val columnExpandedHorizontal =
                prefs(ModulePrefs).getInt("tile_expanded_columns_horizontal", 6)

            //Source QuickQSPanel
            "com.android.systemui.qs.QuickQSPanel".toClass().resolve().apply {
                firstMethod { name = "getNumQuickTiles" }.hook {
                    before {
                        getScreenOrientation(instance<View>()) {
                            result = if (it) columnUnexpandedVertical
                            else columnUnexpandedHorizontal
                        }
                    }
                }
            }

            //Source TileLayout
            "com.android.systemui.qs.TileLayout".toClass().resolve().apply {
                firstMethod { name = "updateColumns" }.hook {
                    before {
                        instance<ViewGroup>().apply {
                            getScreenOrientation(this) {
                                val mColumns =
                                    firstField { name = "mColumns" }.of(instance).get<Int>()
                                val newColumns = if (it) columnExpandedVertical
                                else columnExpandedHorizontal
                                firstField { name = "mColumns" }.of(instance).set(newColumns)
                                result = mColumns != newColumns
                            }
                        }
                    }
                }
            }
        }
    }
}

