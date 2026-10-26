package com.luckyzyx.luckytool.data

import kotlinx.serialization.Serializable

@Serializable
data class MemcConfigPackage(
    var packName: String,
    var rate: String,
    var type: String
)