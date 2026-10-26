package com.luckyzyx.luckytool.data

import kotlinx.serialization.Serializable

@Serializable
data class AppVerInfo(
    var name: String,
    var packName: String,
    var versionName: String,
    var versionCode: Long,
    var versionCommit: String
)