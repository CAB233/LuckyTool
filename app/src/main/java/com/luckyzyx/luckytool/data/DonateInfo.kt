package com.luckyzyx.luckytool.data

import java.io.Serializable

data class DonateInfo(
    val name: String,
    val money: Double,
    val details: Int,
    val unit: String = "RMB"
) : Serializable

data class DonateDetailInfo(
    val name: String,
    val time: String,
    val channel: String,
    val money: Double,
    val order: String,
    val unit: String = "RMB",
) : Serializable