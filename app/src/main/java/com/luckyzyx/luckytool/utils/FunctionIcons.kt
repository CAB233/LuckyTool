package com.luckyzyx.luckytool.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import me.zhanghai.android.appiconloader.iconloaderlib.BaseIconFactory
import me.zhanghai.android.appiconloader.iconloaderlib.ShadowGenerator

/** 功能列表使用系统应用原图；只归一化尺寸、裁透明圆角并生成阴影，不添加底色或底板。 */
internal object FunctionIcons {
    fun render(context: Context, drawable: Drawable, size: Int): Bitmap =
        BaseIconFactory(context, context.resources.configuration.densityDpi, size).use { factory ->
            factory.disableColorExtraction()
            // 保留旧图标的尺寸，不添加白色底板。
            val icon = factory.createScaledBitmapWithoutShadow(drawable, false)
            roundVisibleCorners(icon)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
                ShadowGenerator(size).recreateIcon(icon, Canvas(output))
            }
        }

    private fun roundVisibleCorners(icon: Bitmap) {
        val pixels = IntArray(icon.width * icon.height)
        icon.getPixels(pixels, 0, icon.width, 0, 0, icon.width, icon.height)
        var left = icon.width
        var top = icon.height
        var right = 0
        var bottom = 0
        var opaquePixels = 0
        pixels.forEachIndexed { index, pixel ->
            if (Color.alpha(pixel) >= 128) {
                opaquePixels++
                val x = index % icon.width
                val y = index / icon.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        if (left >= right || top >= bottom) return
        // 只裁有完整底色的图块；机器人、盾牌等透明轮廓保留原图，避免切掉边缘细节。
        if (opaquePixels < (right - left) * (bottom - top) * 0.75f) return
        // 在可见内容边缘裁剪，原图自带透明留边也有效；被裁掉的角保持透明。
        val bounds = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        val radius = minOf(bounds.width(), bounds.height()) * 0.25f
        val outside = Path().apply {
            addRoundRect(bounds, radius, radius, Path.Direction.CW)
            fillType = Path.FillType.INVERSE_WINDING
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        Canvas(icon).drawPath(outside, clear)
    }
}
