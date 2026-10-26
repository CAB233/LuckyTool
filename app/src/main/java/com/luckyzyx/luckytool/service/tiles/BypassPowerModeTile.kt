package com.luckyzyx.luckytool.service.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.luckyzyx.luckytool.ITileServiceController
import com.luckyzyx.luckytool.service.TilesService

class BypassPowerModeTile : TileService() {
    private var controller: ITileServiceController? = null

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED
                || intent.action == Intent.ACTION_POWER_DISCONNECTED
            ) {
                refreshBypass()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onStartListening() {
        super.onStartListening()
        TilesService.get(this) {
            controller = it
            refreshData()
        }
    }

    override fun onClick() {
        when (qsTile.state) {
            Tile.STATE_INACTIVE -> {
                if (isCharging()) controller?.bypassMode = true
            }

            Tile.STATE_ACTIVE -> {
                controller?.bypassMode = false
            }

            Tile.STATE_UNAVAILABLE -> {}
        }
        refreshData()
    }

    private fun isCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun refreshBypass() {
        if (!isCharging()) controller?.bypassMode = false
        refreshData()
    }

    private fun refreshData() {
        qsTile.state = if (controller == null) Tile.STATE_UNAVAILABLE
        else if (!controller!!.checkBypassMode()) Tile.STATE_UNAVAILABLE
        else if (controller!!.bypassMode) Tile.STATE_ACTIVE
        else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}
