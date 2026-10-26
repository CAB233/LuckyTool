package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.utils.IntentUtils
import com.luckyzyx.luckytool.utils.closeCollapse

class ChargingTestTile : TileService() {
    override fun onClick() {
        closeCollapse()
        IntentUtils(this).jumpBatteryInfo()
    }
}