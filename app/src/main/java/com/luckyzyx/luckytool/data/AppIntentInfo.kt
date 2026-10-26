package com.luckyzyx.luckytool.data

import com.luckyzyx.luckytool.enums.IntentType
import kotlinx.serialization.Serializable

@Serializable
data class AppIntentInfo(
    var name: String,
    var packName: String,
    var action: String,
    var activity: String,
    var type: IntentType
)