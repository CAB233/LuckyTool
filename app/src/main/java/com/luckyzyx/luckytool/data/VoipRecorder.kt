package com.luckyzyx.luckytool.data

import java.io.Serializable

data class VoipRecorder(
    val packName: String,
    val appName: String,
    val activity: String = ""
) : Serializable
