package com.luckyzyx.luckytool.hook.utils

import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.luckyzyx.luckytool.data.DisplayMode

class DynamicDisplayInfoUtils(private val dynamicInfo: Any) {

    fun getSupportedDisplayModes(): Array<Any> {
        return dynamicInfo.asResolver().firstField {
            name = "supportedDisplayModes"
        }.get<Array<Any>>() ?: arrayOf()
    }

    fun getDisplayMode(modeIns: Any): Pair<Int, DisplayMode>? {
        val id = modeIns.asResolver().firstField { name = "id" }.get<Int>() ?: return null
        val width = modeIns.asResolver().firstField { name = "width" }.get<Int>()
        val height = modeIns.asResolver().firstField { name = "height" }.get<Int>()
        val xDpi = modeIns.asResolver().firstField { name = "xDpi" }.get<Float>()
        val yDpi = modeIns.asResolver().firstField { name = "yDpi" }.get<Float>()
        val refreshRate = modeIns.asResolver().firstField {
            name {
                //C15 peakRefreshRate
                it.contains("refreshRate", true)
            }
        }.get<Float>()
        val mode = DisplayMode(id, width, height, xDpi, yDpi, refreshRate)
        return Pair(id, mode)
    }
}