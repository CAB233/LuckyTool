package com.luckyzyx.luckytool.data

import java.io.Serializable

data class CameraFilter(
    val key: String,
    val title: String,
    var isEnable: Boolean = false
) : Serializable
