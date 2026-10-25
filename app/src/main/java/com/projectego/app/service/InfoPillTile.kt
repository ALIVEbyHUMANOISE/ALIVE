package com.projectego.app.service

import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.projectego.app.R
import com.projectego.app.data.PillSettings
import com.projectego.app.domain.AppClassifier
import com.projectego.app.model.AppCategory

/**
 * The button that summons the pill.
 *
 * A quick settings tile rather than a floating handle: the handle would have to
 * be on screen all the time, which is exactly the thing this feature is trying
 * not to be. Here nothing exists until the person pulls the shade and presses
 * it - the pill is asked for, never ambushed.
 *
 * It is also the switch that remembers the choice, and it is the summon while
 * the accessibility service is off: the guard is the fast path that raises the
 * pill when an entertainment app comes forward, but it may not be running, and
 * a switch that is on with nothing to show for it is not a switch.
 */
class InfoPillTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (PillSettings.isEnabled(this)) {
            PillSettings.setEnabled(this, false)
            InfoPillService.stop(this)
        } else if (Settings.canDrawOverlays(this)) {
            PillSettings.setEnabled(this, true)
            summonIfEntertainmentInFront()
        }
        refresh()
    }

    /**
     * Raises the pill now when an entertainment app is already in front.
     *
     * The shade is drawn over that app, and the app's last resume is still the
     * answer to "which app is in front" - so pressing the tile from inside
     * TikTok starts the reading for TikTok, with no accessibility service
     * involved. If the person is somewhere else, the setting stays on and the
     * guard brings the pill up the next time an entertainment app comes forward.
     */
    private fun summonIfEntertainmentInFront() {
        val front = PillForegroundProbe.latest(this, SUMMON_PROBE_MILLIS) ?: return
        if (AppClassifier.categorize(this, front.packageName) != AppCategory.ENTERTAINMENT) return
        InfoPillService.start(this, front.packageName)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = when {
            PillSettings.isEnabled(this) -> Tile.STATE_ACTIVE
            !Settings.canDrawOverlays(this) -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_alive_hourglass)
        tile.label = getString(R.string.pill_tile_label)
        tile.updateTile()
    }

    private companion object {
        /** How old the last resume may be to still count as "in front". */
        const val SUMMON_PROBE_MILLIS = 30 * 60_000L
    }
}