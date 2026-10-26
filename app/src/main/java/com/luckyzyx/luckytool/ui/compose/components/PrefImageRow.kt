package com.luckyzyx.luckytool.ui.compose.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片路径预览缩略图：把本地绝对路径解码成一张圆角缩略图，用在 [PrefRow] 的 trailing 槽位（行尾）；
 * 行首留给条目标题/图标。
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
    val targetPx = with(LocalDensity.current) { height.roundToPx() }.coerceAtLeast(1)
    val bitmap = previewImage(path, targetPx).value ?: return
    Box(
        modifier = modifier
            .height(height)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
            .clip(shape)
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 图片路径条目：整行可点，行尾是 [PrefImagePreview] 缩略图，摘要默认显示路径（空路径显示 `Null`）。
 *
 * 与颜色预览色块（[PrefColorPreview]）同一定位：`custom()` 逃生舱里的行渲染原语，只管「标题 + 预览 + 点击」；
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
            trailing = { PrefImagePreview(path = path, height = previewHeight) },
            onClick = onClick,
        )
    }
}

/** 缩略图解码结果缓存：滑出/滑回视口、或行重组都不会重新读盘解码。24 张 40dp 缩略图的量级。 */
private val previewCache = LruCache<String, ImageBitmap>(24)

/**
 * 缩略图的解码状态。
 *
 * 命中缓存时首帧就拿到位图；未命中时在 [Dispatchers.IO] 上解码，主线程只做一次 map 查询。
 * 原先用 remember { decodePreviewImage(...) } 在组合期同步解码（一次 bounds 读 + 一次整图解码，
 * PNG 还是整张解出来再降采样），行第一次进入视口的那一帧被这段读盘+解码占住 ——
 * 快速滑动滑到自定义 OTA 卡片时的那一下停顿就来自这里。
 */
@Composable
private fun previewImage(path: String, targetPx: Int): State<ImageBitmap?> {
    val key = if (path.isBlank()) null else "$targetPx@$path"
    val cached = remember(key) { key?.let { previewCache.get(it) } }
    return produceState<ImageBitmap?>(initialValue = cached, path, targetPx) {
        if (key == null) {
            value = null
            return@produceState
        }
        value = previewCache.get(key)
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { decodePreviewImage(path, targetPx) }
            ?.also { previewCache.put(key, it) }
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
