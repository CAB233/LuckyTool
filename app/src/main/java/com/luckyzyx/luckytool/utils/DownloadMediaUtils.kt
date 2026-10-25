package com.luckyzyx.luckytool.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** 设置页备份的预设目录（MediaStore 的 RELATIVE_PATH 值，不带结尾斜杠） */
const val DOWNLOAD_TOOL_DIR = "Download/LuckyTool"

/**
 * 通过 MediaStore.Downloads 把 [bytes] 投稿成 Download/LuckyTool 下的 [fileName]。
 *
 * 为什么不直接写文件路径：Android 10+ 分区存储下 app 自己写 /storage/emulated/0/Download 需要
 * MANAGE_EXTERNAL_STORAGE 或 SAF 授权，而向 MediaStore.Downloads 投稿自己创建的行不需要任何存储权限
 * （桌面备份的自动快照走的是同一条路）。目录靠 RELATIVE_PATH 参数交给 provider，写不到别处。
 *
 * 写法：insert(IS_PENDING=1) → 写流 → update(IS_PENDING=0) → 回读 SIZE；
 * 中途失败删掉残行，绝不留下 0 字节的半份备份让用户以为备份成功。
 *
 * @return 落盘成功返回该行的 uri，失败返回 null（已记日志）
 */
fun Context.writeDownloadFile(fileName: String, mimeType: String, bytes: ByteArray): Uri? {
    val resolver = contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        // RELATIVE_PATH 必须以 / 结尾，否则文件会落到 Download 根目录
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$DOWNLOAD_TOOL_DIR/")
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = runCatching { resolver.insert(collection, values) }.getOrElse { t ->
        LogUtils.e("DownloadMedia", "insert $fileName", "$t", false)
        return null
    } ?: run {
        LogUtils.e("DownloadMedia", "insert $fileName", "null uri", false)
        return null
    }
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("openOutputStream null")
        // 写完再解除 pending，否则别的应用会先看到半份文件
        val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)
        // 回读 SIZE：投稿成功但 0 字节说明写流没真落盘
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            c.moveToFirst() && !c.isNull(0) && c.getLong(0) > 0L
        } ?: false
    }.getOrElse { t ->
        LogUtils.e("DownloadMedia", "write $fileName", "$t", false)
        false
    }
    if (!written) {
        runCatching { resolver.delete(uri, null, null) }
        return null
    }
    return uri
}
