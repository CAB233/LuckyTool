package com.luckyzyx.luckytool.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格（迁移自 KernelSU `ui/UiMode.kt`，取值字符串完全一致，便于偏好互通）。
 *
 * - [Miuix]：Miuix 外观线（KernelSU 默认）
 * - [Material]：Material 3 Expressive 外观线
 *
 * 默认值取 [Miuix]（对齐 KernelSU 默认观感）；LuckyTool 目前只有主题页 / 底部导航等部分界面
 * 提供了 Miuix 实现，其余页面仍由 Material 组件渲染，用户可在「设置 → 界面风格」中切回 [Material]。
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

/** 当前界面风格（对齐 KernelSU `LocalUiMode`） */
val LocalUiMode = staticCompositionLocalOf { UiMode.fromValue(UiMode.DEFAULT_VALUE) }
