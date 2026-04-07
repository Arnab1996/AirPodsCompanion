package me.arnabsaha.airpodscompanion.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import me.arnabsaha.airpodscompanion.MainActivity
import me.arnabsaha.airpodscompanion.service.AacpBatteryState

/**
 * Monitors battery levels and posts high-priority alerts when levels drop below thresholds.
 * Only alerts once per threshold crossing per component — won't spam on every battery packet.
 */
class BatteryAlertManager(private val context: Context) {

    companion object {
        private const val TAG = "BatteryAlert"
        private const val CHANNEL_ID = "airpods_battery_alert"
        private const val NOTIF_LEFT = 100
        private const val NOTIF_RIGHT = 101
        private const val NOTIF_CASE = 102
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)

    // Track the last level we alerted at for each component
    private var lastAlertedLeft = Int.MAX_VALUE
    private var lastAlertedRight = Int.MAX_VALUE
    private var lastAlertedCase = Int.MAX_VALUE

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Battery Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Low battery warnings for AirPods"
            enableVibration(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun checkAndAlert(battery: AacpBatteryState) {
        val threshold = getThreshold()
        if (threshold <= 0) return // Alerts disabled

        checkComponent("Left", battery.leftLevel, threshold, NOTIF_LEFT, lastAlertedLeft)?.let {
            lastAlertedLeft = it
        }
        checkComponent("Right", battery.rightLevel, threshold, NOTIF_RIGHT, lastAlertedRight)?.let {
            lastAlertedRight = it
        }
        checkComponent("Case", battery.caseLevel, threshold, NOTIF_CASE, lastAlertedCase)?.let {
            lastAlertedCase = it
        }

        // Reset alerts if level rises above threshold
        if (battery.leftLevel > threshold) lastAlertedLeft = Int.MAX_VALUE
        if (battery.rightLevel > threshold) lastAlertedRight = Int.MAX_VALUE
        if (battery.caseLevel > threshold) lastAlertedCase = Int.MAX_VALUE
    }

    fun resetAlerts() {
        lastAlertedLeft = Int.MAX_VALUE
        lastAlertedRight = Int.MAX_VALUE
        lastAlertedCase = Int.MAX_VALUE
        // Cancel any active alert notifications
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIF_LEFT)
        manager.cancel(NOTIF_RIGHT)
        manager.cancel(NOTIF_CASE)
    }

    fun getThreshold(): Int = prefs.getInt("battery_alert_threshold", 20)

    fun setThreshold(value: Int) {
        prefs.edit().putInt("battery_alert_threshold", value).apply()
    }

    private fun checkComponent(
        name: String, level: Int, threshold: Int, notifId: Int, lastAlerted: Int
    ): Int? {
        if (level < 0) return null // Unknown/disconnected
        if (level <= threshold && lastAlerted > threshold) {
            postAlert(name, level, notifId)
            Log.d(TAG, "$name battery alert: $level% (threshold=$threshold%)")
            return level
        }
        return null
    }

    private fun postAlert(component: String, level: Int, notifId: Int) {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle("Low Battery: $component")
            .setContentText("$component AirPod is at $level%")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notifId, notification)
    }
}
