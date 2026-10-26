package com.luckyzyx.luckytool.service.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.IAdbDebugController
import com.luckyzyx.luckytool.service.AdbService
import com.luckyzyx.luckytool.utils.OtherPrefs
import com.luckyzyx.luckytool.utils.getString

class RemoteAdbTile : TileService() {
    private var controller: IAdbDebugController? = null

    override fun onStartListening() {
        AdbService.get(this) {
            controller = it
            refreshData()
        }
    }

    override fun onClick() {
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                val port = getString(OtherPrefs, "adb_port", "6666")
                if (port.isNotBlank() && (port.toIntOrNull() ?: 0) > 0) {
                    controller?.adbPort = port.toInt()
                    controller?.restartAdb()
                }
            }

            Tile.STATE_ACTIVE -> {
                controller?.adbPort = -1
                controller?.restartAdb()
                controller?.adbPort = 0
            }

            Tile.STATE_UNAVAILABLE -> {}
        }
        refreshData()
    }

    private fun refreshData() {
        qsTile.state = if (controller == null) Tile.STATE_UNAVAILABLE
        else if ((controller?.adbPort ?: 0) > 0) Tile.STATE_ACTIVE
        else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}
