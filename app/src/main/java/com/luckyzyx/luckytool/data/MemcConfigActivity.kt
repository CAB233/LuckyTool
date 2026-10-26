package com.luckyzyx.luckytool.data

import kotlinx.serialization.Serializable

@Serializable
data class MemcConfigActivity(
    var packName: String,
    var activity: String,
    var type: String
)