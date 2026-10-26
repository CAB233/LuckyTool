package com.luckyzyx.luckytool.data

import kotlinx.serialization.Serializable

@Serializable
data class DarkModeInfo(
    var packName: String,
    var curType: Int = 0,
)