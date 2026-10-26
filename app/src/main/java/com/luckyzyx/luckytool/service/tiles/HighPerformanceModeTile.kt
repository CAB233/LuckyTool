package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.utils.A15
import com.luckyzyx.luckytool.utils.IntentUtils
import com.luckyzyx.luckytool.utils.SDK
import com.luckyzyx.luckytool.utils.closeCollapse

class HighPerformanceModeTile : TileService() {
    override fun onClick() {
        closeCollapse()
        if (SDK >= A15) IntentUtils(this).jumpBattery()
        else IntentUtils(this).jumpHighPerformance()
    }
}