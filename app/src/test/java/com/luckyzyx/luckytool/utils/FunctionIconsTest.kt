package com.luckyzyx.luckytool.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FunctionIconsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `padded legacy icon receives no extra background or frame`() {
        // ColorOS 已成型图标带透明外沿，不能因为四角透明而再次加白色底板。
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        Canvas(source).drawRoundRect(12f, 12f, 108f, 108f, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
        })
        val icon = FunctionIcons.render(context, BitmapDrawable(context.resources, source), 144)
        assertEquals(0, Color.alpha(icon.getPixel(0, 0)))
        assertTrue(coloredPixelCount(icon, Color.BLUE) > 144 * 144 / 2)
        assertEquals(0, coloredPixelCount(icon, Color.WHITE))
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            val pixel = icon.getPixel(x, y)
            if (Color.alpha(pixel) > 200) {
                assertEquals(0, Color.red(pixel))
                assertEquals(0, Color.green(pixel))
            }
        }
    }

    @Test
    fun `adaptive app keeps its own layers without an additional plate`() {
        val source = AdaptiveIconDrawable(ColorDrawable(Color.GREEN), null)
        val icon = FunctionIcons.render(context, source, 96)
        assertEquals(Color.GREEN, icon.getPixel(48, 48))
        assertEquals(0, coloredPixelCount(icon, Color.WHITE))
        assertEquals(0, Color.alpha(icon.getPixel(0, 0)))
    }

    @Test
    fun `padded square has transparent rounded corners instead of a white plate`() {
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        Canvas(source).drawRect(12f, 12f, 108f, 108f, Paint().apply { color = Color.GREEN })
        val icon = FunctionIcons.render(context, BitmapDrawable(context.resources, source), 144)
        val left = (0 until icon.width).first { icon.getPixel(it, 72) == Color.GREEN }
        val top = (0 until icon.height).first { icon.getPixel(72, it) == Color.GREEN }
        assertTrue(Color.alpha(icon.getPixel(left + 3, top + 3)) < 30)
        assertEquals(Color.GREEN, icon.getPixel(72, 72))
        assertEquals(0, coloredPixelCount(icon, Color.WHITE))
    }

    @Test
    fun `wide legacy logo keeps its proportions and transparent surround`() {
        val source = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        Canvas(source).drawColor(Color.RED)
        val icon = FunctionIcons.render(context, BitmapDrawable(context.resources, source), 144)
        val redPixels = buildList {
            for (y in 0 until icon.height) for (x in 0 until icon.width) {
                if (icon.getPixel(x, y) == Color.RED) add(x to y)
            }
        }
        assertTrue(redPixels.isNotEmpty())
        val width = redPixels.maxOf { it.first } - redPixels.minOf { it.first } + 1
        val height = redPixels.maxOf { it.second } - redPixels.minOf { it.second } + 1
        assertEquals(2f, width.toFloat() / height, 0.15f)
        assertEquals(0, Color.alpha(icon.getPixel(72, 0)))
        assertEquals(0, coloredPixelCount(icon, Color.WHITE))
    }

    @Test
    fun `transparent glyph keeps details near its bounding corners`() {
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val glyph = Path().apply {
            moveTo(0f, 0f)
            lineTo(120f, 0f)
            lineTo(0f, 120f)
            close()
        }
        Canvas(source).drawPath(glyph, Paint().apply { color = Color.RED })
        val icon = FunctionIcons.render(context, BitmapDrawable(context.resources, source), 144)
        var left = icon.width
        var top = icon.height
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            if (icon.getPixel(x, y) == Color.RED) {
                left = minOf(left, x)
                top = minOf(top, y)
            }
        }
        assertEquals(Color.RED, icon.getPixel(left + 3, top + 3))
        assertEquals(0, coloredPixelCount(icon, Color.WHITE))
    }

    private fun coloredPixelCount(bitmap: Bitmap, color: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) == color) count++
        }
        return count
    }
}
