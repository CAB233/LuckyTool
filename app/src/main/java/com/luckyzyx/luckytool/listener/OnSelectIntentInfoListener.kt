package com.luckyzyx.luckytool.listener

import com.luckyzyx.luckytool.data.AppIntentInfo

interface OnSelectIntentInfoListener {
    fun resultSelectIntentInfos(list: ArrayList<AppIntentInfo>)
}