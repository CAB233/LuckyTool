package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.ITileServiceController
import com.luckyzyx.luckytool.service.TilesService

class GoogleServiceTile : TileService() {
    private var controller: ITileServiceController? = null

    override fun onStartListening() {
        TilesService.get(this) {
            controller = it
            refreshData()
        }
    }

    override fun onClick() {
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> controller?.googleStatus = true
            Tile.STATE_ACTIVE -> controller?.googleStatus = false
            Tile.STATE_UNAVAILABLE -> {}
        }
        refreshData()
    }

    private fun refreshData() {
        qsTile.state = if (controller == null) Tile.STATE_UNAVAILABLE
        else if (controller!!.googleStatus) Tile.STATE_ACTIVE
        else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}