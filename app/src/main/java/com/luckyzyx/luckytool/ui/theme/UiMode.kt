package com.luckyzyx.luckytool.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格及其持久化值。
 *
 * - [Miuix]：Miuix 外观线
 * - [Material]：Material 3 Expressive 外观线
 *
 * 默认值取 [Miuix]，用户可在「设置 → 界面风格」中切换为 [Material]。
 */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Material.value -> Material
            else -> Miuix
        }

        val DEFAULT_VALUE = Miuix.value
    }
}

/** 当前界面风格 */
val LocalUiMode = staticCompositionLocalOf { UiMode.fromValue(UiMode.DEFAULT_VALUE) }
