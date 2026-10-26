package com.luckyzyx.luckytool.listener

import android.content.pm.ActivityInfo

interface OnSelectActivityInfoListener {
    fun resultSelectActivityInfos(list: ArrayList<ActivityInfo>)
}