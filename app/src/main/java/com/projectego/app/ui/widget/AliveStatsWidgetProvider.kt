package com.projectego.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.projectego.app.MainActivity
import com.projectego.app.R
import com.projectego.app.data.LockStateManager
import com.projectego.app.data.UsageHistoryRepository
import com.projectego.app.ui.AppLabels
import com.projectego.app.ui.Durations
import com.projectego.app.ui.UsageChartStates

/**
 * The statistics dashboard, on the home screen.
 *
 * It reads the day ALIVE already recorded rather than the engine: the widget
 * has no business rolling the day over, setting the lock, or archiving
 * anything, and a screen somebody glances at must not be able to change what it
 * reports. The trade is that it is never fresher than the last app render -
 * which is why every render also pushes an update here, and why the period
 * below exists as a floor rather than as the plan.
 *
 * Tapping it opens the statistics panel itself, not just the app.
 */
class AliveStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {

        /** Opens the app straight into the statistics panel. */
        const val EXTRA_OPEN_STATS = "com.projectego.app.extra.OPEN_STATS"

        private const val ROWS = 3

        /** Redraws every placed widget. Called after each app render. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AliveStatsWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = render(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun render(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_alive_stats)

            val todayKey = LockStateManager.dayKeyOf(System.currentTimeMillis())
            val day = UsageHistoryRepository(context).day(todayKey)
            val totalMinutes = day?.totalMinutes ?: 0L
            val limitMinutes = day?.limitMinutes ?: 0L

            // Where the eye lands first: everything below is only true if this
            // is. Tapping it goes to the panel the number came from.
            views.setOnClickPendingIntent(R.id.widget_root, openStats(context))

            val apps = (day?.perAppMinutes ?: emptyMap())
                .filterValues { it > 0L }
                .entries
                .sortedByDescending { it.value }
                .take(ROWS)

            val rowIds = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
            val nameIds = intArrayOf(R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3)
            val minuteIds = intArrayOf(R.id.widget_minutes_1, R.id.widget_minutes_2, R.id.widget_minutes_3)

            // No record at all is not the same as a recorded zero, and the widget
            // says which one it is instead of showing an empty card.
            if (day == null) {
                views.setTextViewText(R.id.widget_total, Durations.of(context, 0L))
                views.setTextViewText(R.id.widget_allowance, "")
                views.setViewVisibility(R.id.widget_bar, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it, View.GONE) }
                return views
            }

            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_bar, View.VISIBLE)
            views.setTextViewText(R.id.widget_total, Durations.of(context, totalMinutes))
            views.setTextViewText(
                R.id.widget_allowance,
                if (limitMinutes > 0L) {
                    context.getString(
                        R.string.widget_stats_of_allowance,
                        UsageChartStates.percentOf(totalMinutes, limitMinutes)
                    )
                } else {
                    context.getString(R.string.widget_stats_no_allowance)
                }
            )
            // Full at 100 and no further: the bar is where the day stands against
            // the allowance, while the line above states the real percentage -
            // which can be past a hundred, and that is worth seeing.
            views.setProgressBar(
                R.id.widget_bar,
                100,
                UsageChartStates.percentOf(totalMinutes, limitMinutes).coerceIn(0, 100),
                false
            )

            rowIds.forEachIndexed { index, rowId ->
                val entry = apps.getOrNull(index)
                if (entry == null) {
                    views.setViewVisibility(rowId, View.GONE)
                } else {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(nameIds[index], AppLabels.labelFor(context, entry.key))
                    views.setTextViewText(minuteIds[index], Durations.of(context, entry.value))
                }
            }

            return views
        }

        private fun openStats(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_STATS, true)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
