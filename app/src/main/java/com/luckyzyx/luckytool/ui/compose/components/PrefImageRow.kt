package com.luckyzyx.luckytool.ui.compose.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图片路径预览缩略图：把本地绝对路径解码成一张圆角缩略图，用在 [PrefRow] 的 leading 槽位。
 *
 * 高度固定为 [height]、宽度按原图比例算，所以同一行既能显示 1.4:1 的宽图，也能显示 2.65:1 的窄图，
 * 都不会把行高撑起来。路径为空或解码失败时什么都不画（槽位宽度为 0，行退化成纯文本行）。
 * 解码按行高降采样（inSampleSize），不把原图整张读进内存再缩放。
 */
@Composable
fun PrefImagePreview(
    path: String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val density = LocalDensity.current
    val targetPx = with(density) { height.roundToPx() }.coerceAtLeast(1)
    // 只在路径或目标高度变化时重新解码，重组不重复读盘
    val image = remember(path, targetPx) { decodePreviewImage(path, targetPx) }
    if (image == null) return
    Box(
        modifier = modifier
            .height(height)
            .aspectRatio(image.width.toFloat() / image.height)
            .clip(shape)
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 图片路径条目：整行可点，行首是 [PrefImagePreview] 缩略图，摘要默认显示路径（空路径显示 `Null`）。
 *
 * 与颜色预览圆点（[ColorDot]）同一定位：`custom()` 逃生舱里的行渲染原语，只管「标题 + 预览 + 点击」；
 * 选图 / 裁剪这些依赖 Activity Result launcher 的逻辑留在页面侧，本文件不引入 activity 依赖。
 */
@Composable
fun PrefImageRow(
    title: String,
    path: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    previewHeight: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    PrefRowInset(modifier) {
        PrefRow(
            title = title,
            summary = summary ?: path.ifBlank { "Null" },
            enabled = enabled,
            leading = { PrefImagePreview(path = path, height = previewHeight) },
            onClick = onClick,
        )
    }
}

/** 按目标高度降采样解码本地图片；读不到或不是图片时返回 null（不抛异常、不打日志）。 */
private fun decodePreviewImage(path: String, targetPx: Int): ImageBitmap? {
    if (path.isBlank()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )?.asImageBitmap()
        }
    }.getOrNull()
}
