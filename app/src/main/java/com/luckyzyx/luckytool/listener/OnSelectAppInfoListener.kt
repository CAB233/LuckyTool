package com.luckyzyx.luckytool.listener

import com.luckyzyx.luckytool.data.AppInfo

interface OnSelectAppInfoListener {
    fun resultSelectAppInfos(list: ArrayList<AppInfo>)
}