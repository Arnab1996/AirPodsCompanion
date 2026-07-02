package me.arnabsaha.airpodscompanion.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import me.arnabsaha.airpodscompanion.R

/**
 * Home screen widget showing AirPods battery levels.
 * Updates when battery data changes via broadcast.
 */
class BatteryWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE = "me.arnabsaha.airpodscompanion.BATTERY_WIDGET_UPDATE"
        const val EXTRA_LEFT = "left"
        const val EXTRA_RIGHT = "right"
        const val EXTRA_CASE = "case"

        fun sendUpdate(context: Context, left: Int, right: Int, case_: Int) {
            // Write to prefs for Glance + Android Auto to read
            context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
                .edit()
                .putInt("widget_left", left)
                .putInt("widget_right", right)
                .putInt("widget_case", case_)
                .apply()

            val intent = Intent(ACTION_UPDATE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_LEFT, left)
                putExtra(EXTRA_RIGHT, right)
                putExtra(EXTRA_CASE, case_)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BatteryWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    /**
     * Renders the widget from the last-known levels persisted in prefs. Both the periodic
     * [onUpdate] (widget add / 30-min refresh / reboot) and the on-change [onReceive] read
     * the same source, so the widget shows current battery the moment it appears — it no
     * longer sits on dashes waiting for the next battery-change broadcast.
     */
    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val prefs = context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
        val left = prefs.getInt("widget_left", -1)
        val right = prefs.getInt("widget_right", -1)
        val case_ = prefs.getInt("widget_case", -1)

        val views = RemoteViews(context.packageName, R.layout.widget_battery)
        views.setTextViewText(R.id.widget_left, if (left >= 0) "L: $left%" else "L: --")
        views.setTextViewText(R.id.widget_right, if (right >= 0) "R: $right%" else "R: --")
        views.setTextViewText(R.id.widget_case, if (case_ >= 0) "Case: $case_%" else "Case: --")
        manager.updateAppWidget(widgetId, views)
    }
}
