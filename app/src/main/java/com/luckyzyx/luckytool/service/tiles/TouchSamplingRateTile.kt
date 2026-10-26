package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.ITileServiceController
import com.luckyzyx.luckytool.service.TilesService
import com.luckyzyx.luckytool.utils.GlobalKeyValue.keyTouchSamplingRate
import com.luckyzyx.luckytool.utils.GlobalKeyValue.keyTouchSamplingRateLevel
import com.luckyzyx.luckytool.utils.SettingsPrefs
import com.luckyzyx.luckytool.utils.getString
import com.luckyzyx.luckytool.utils.putBoolean

class TouchSamplingRateTile : TileService() {
    private var controller: ITileServiceController? = null

    override fun onStartListening() {
        TilesService.get(this) {
            controller = it
            refreshData()
        }
    }

    override fun onClick() {
        val level = getString(SettingsPrefs, keyTouchSamplingRateLevel, "240")
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                controller?.touchMode = level.toInt()
                putBoolean(SettingsPrefs, keyTouchSamplingRate, true)
            }

            Tile.STATE_ACTIVE -> {
                controller?.touchMode = 0
                putBoolean(SettingsPrefs, keyTouchSamplingRate, false)
            }

            Tile.STATE_UNAVAILABLE -> {}
        }
        refreshData()
    }

    private fun refreshData() {
        qsTile.state = if (controller == null) Tile.STATE_UNAVAILABLE
        else if (!controller!!.checkTouchMode()) Tile.STATE_UNAVAILABLE
        else if (controller!!.touchMode > 0) Tile.STATE_ACTIVE
        else Tile.STATE_INACTIVE
        qsTile.updateTile()
        if (qsTile.state == Tile.STATE_UNAVAILABLE) putBoolean(
            SettingsPrefs, keyTouchSamplingRate, false
        )
    }
}
