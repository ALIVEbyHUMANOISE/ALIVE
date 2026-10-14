package com.projectego.app.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.projectego.app.R

/**
 * The button that summons the pill.
 *
 * A quick settings tile rather than a floating handle: the handle would have to
 * be on screen all the time, which is exactly the thing this feature is trying
 * not to be. Here nothing exists until the person pulls the shade and presses
 * it - the pill is asked for, never ambushed.
 *
 * It is also the switch that turns it off, so no second place is needed to get
 * rid of it.
 */
class InfoPillTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (InfoPillService.isRunning) {
            InfoPillService.stop(this)
        } else if (Settings.canDrawOverlays(this)) {
            InfoPillService.start(this)
        }
        // The state lands a moment later, when the service says it is up.
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = when {
            InfoPillService.isRunning -> Tile.STATE_ACTIVE
            !Settings.canDrawOverlays(this) -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_alive_hourglass)
        tile.label = getString(R.string.pill_tile_label)
        tile.updateTile()
    }
}
