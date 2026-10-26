package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.utils.IntentUtils
import com.luckyzyx.luckytool.utils.closeCollapse

class ProcessManagerTile : TileService() {
    override fun onClick() {
        closeCollapse()
        IntentUtils(this).jumpRunningApp()
    }
}