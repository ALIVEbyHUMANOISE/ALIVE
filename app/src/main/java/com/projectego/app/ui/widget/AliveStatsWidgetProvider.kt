package com.projectego.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
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
 * It reads what ALIVE already recorded - the day's archive and the run the app
 * already counted - rather than asking the engine: the widget has no business
 * rolling the day over, setting the lock, or archiving anything, and a screen
 * somebody glances at must not be able to change what it
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

        /** A four-by-two cell on a typical launcher, which is what the card is
         *  painted for before the host stretches it to whatever it really is. */
        private const val CARD_WIDTH_DP = 320f
        private const val CARD_HEIGHT_DP = 160f

        /**
         * The big number, and the word above it.
         *
         * The two travel together because they say one thing: the figure is the
         * day's screen total now, and a day recorded before that total existed
         * falls back to its entertainment minutes - with the label falling back
         * to match. A number under the wrong word is the one mistake a glanceable
         * card cannot afford.
         */
        internal fun figureFor(totalMinutes: Long, screenMinutes: Long?): Pair<Long, Int> =
            if (screenMinutes == null) totalMinutes to R.string.widget_stats_eyebrow
            else screenMinutes to R.string.widget_stats_eyebrow_screen

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

            // A day recorded before the screen total existed says nothing rather
            // than zero, because zero would be a claim about time nobody counted.
            // On such a day the figure falls back to the entertainment minutes and
            // the label above it falls back with it - the picture and its caption
            // never disagree about which number is being shown.
            val screen = day?.screenMinutes

            // Where the eye lands first: everything below is only true if this
            // is. Tapping it goes to the panel the number came from.
            views.setOnClickPendingIntent(R.id.widget_root, openStats(context))

            // The card, at the size a four-by-two cell is on this device; the
            // host stretches it if the person resizes the widget away from that.
            val density = context.resources.displayMetrics.density
            views.setImageViewBitmap(
                R.id.widget_card,
                AliveWidgetArt.card(
                    context,
                    (CARD_WIDTH_DP * density).toInt(),
                    (CARD_HEIGHT_DP * density).toInt()
                )
            )

            val apps = (day?.perAppMinutes ?: emptyMap())
                .filterValues { it > 0L }
                .entries
                .sortedByDescending { it.value }
                .take(ROWS)

            val (figureMinutes, figureLabel) = figureFor(totalMinutes, screen)
            views.setTextViewText(R.id.widget_eyebrow, context.getString(figureLabel))

            // The big figure is the whole day now, not only the part that costs
            // something: the widget is glanced at, and the number someone wants
            // from a glance is how long they have been on the phone.
            val figure = Durations.of(context, figureMinutes)
            views.setImageViewBitmap(
                R.id.widget_figure,
                AliveWidgetArt.figure(
                    context,
                    figure,
                    ContextCompat.getColor(context, R.color.alive_ink)
                )
            )
            // The figure is a picture, so the sentence it carries is said here
            // instead: the card has to read the same out loud as it does at a
            // glance.
            views.setContentDescription(R.id.widget_figure, figure)

            views.setImageViewResource(
                R.id.widget_character,
                characterFor(
                    recorded = day != null,
                    overAllowance = limitMinutes > 0L && totalMinutes >= limitMinutes
                )
            )

            // The one figure on this card that is not about today, read from the
            // same preference file the app writes it to - and no flame beside a
            // run of zero, which would be a lie about the number it stands next
            // to.
            val streak = LockStateManager(context).getCurrentStreak()
            views.setTextViewText(
                R.id.widget_streak,
                context.getString(R.string.widget_stats_streak, streak)
            )
            views.setViewVisibility(R.id.widget_flame, if (streak > 0) View.VISIBLE else View.GONE)

            val rowIds = intArrayOf(R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
            val nameIds = intArrayOf(R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3)
            val minuteIds = intArrayOf(R.id.widget_minutes_1, R.id.widget_minutes_2, R.id.widget_minutes_3)

            // No record at all is not the same as a recorded zero, and the widget
            // says which one it is instead of showing an empty card.
            if (day == null) {
                views.setTextViewText(R.id.widget_allowance, "")
                views.setViewVisibility(R.id.widget_meter, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rowIds.forEach { views.setViewVisibility(it, View.GONE) }
                return views
            }

            views.setViewVisibility(R.id.widget_empty, View.GONE)
            views.setViewVisibility(R.id.widget_meter, View.VISIBLE)

            // The bar stops at a full allowance and no further, while this line
            // states the real share - which can be past a hundred, and that is
            // worth seeing. It is the allowance's sentence, not the figure's: the
            // big number above is the whole screen, and the two are labelled apart
            // on purpose, because "4j58" over "126% of the allowance" would read
            // as one wrong claim instead of two true ones.
            val allowance = if (limitMinutes > 0L) {
                context.getString(
                    R.string.widget_stats_of_allowance,
                    UsageChartStates.percentOf(totalMinutes, limitMinutes)
                )
            } else {
                context.getString(R.string.widget_stats_no_allowance)
            }
            views.setTextViewText(R.id.widget_allowance, allowance)
            views.setContentDescription(R.id.widget_meter, allowance)

            // The same slices, in the same colours, in the same order as the rows
            // under them, so the bar and the list are one statement rather than
            // two pictures that happen to sit together.
            val named = apps.map { it.value }
            views.setImageViewBitmap(
                R.id.widget_meter,
                AliveWidgetArt.meter(
                    context,
                    WidgetMeter.slices(named, totalMinutes - named.sum(), limitMinutes)
                )
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

        /**
         * The character, answering the one fact this card knows.
         *
         * A day with no record yet waves, because that is what Home shows on a
         * fresh day, and a day spent past its allowance wears the face Home
         * wears for it. Nothing in between needs a face of its own - the number
         * is the news.
         */
        private fun characterFor(recorded: Boolean, overAllowance: Boolean): Int = when {
            !recorded -> R.drawable.ic_alive_pose_wave
            overAllowance -> R.drawable.ic_alive_face_angry
            else -> R.drawable.ic_alive_character
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
