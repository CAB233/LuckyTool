package com.luckyzyx.luckytool.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import java.io.Serializable

data class ShortcutBean(
    val key: String,
    val label: String,
    val icon: Icon? = null,
    var intent: Intent? = null,
) : Serializable {

    fun toShortcutInfo(context: Context): ShortcutInfo {
        return ShortcutInfo.Builder(context, key).apply {
            setShortLabel(label)
            setIcon(icon)
            intent?.let { setIntent(it) }
        }.build()
    }
}