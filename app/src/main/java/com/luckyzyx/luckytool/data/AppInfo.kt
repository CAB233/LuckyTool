package com.luckyzyx.luckytool.data

import android.graphics.drawable.Drawable
import java.io.Serializable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val size: Long,
    val versionName: String,
    val versionCode: Long,
    val installTime: Long,
    val lastInstallTime: Long,
    val target: Int,
    var isSystem: Boolean,
    var isOverlay: Boolean,
    var isEnable: Boolean
) : Serializable