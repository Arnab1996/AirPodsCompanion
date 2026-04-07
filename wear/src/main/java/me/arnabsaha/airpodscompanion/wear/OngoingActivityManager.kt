package me.arnabsaha.airpodscompanion.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Singleton that manages a Wear OS Ongoing Activity notification while AirPods are connected.
 * Shows battery status on the watch face and in the recents/ongoing area.
 */
object OngoingActivityManager {

    private const val CHANNEL_ID = "airbridge_ongoing"
    private const val NOTIFICATION_ID = 1001

    private var isActive = false

    /**
     * Start or create the ongoing activity notification.
     */
    fun start(
        context: Context,
        deviceName: String,
        leftBattery: Int,
        rightBattery: Int,
        ancMode: Int
    ) {
        ensureChannel(context)

        val touchIntent = Intent(context, WearMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            touchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = buildStatusText(leftBattery, rightBattery)
        val ancText = ancModeLabel(ancMode)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(deviceName)
            .setContentText(statusText)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .build()

        val ongoingStatus = Status.Builder()
            .addTemplate("#battery# #anc#")
            .addPart("battery", Status.TextPart(statusText))
            .addPart("anc", Status.TextPart(ancText))
            .build()

        val ongoingActivity = OngoingActivity.Builder(context, NOTIFICATION_ID, notification)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(pendingIntent)
            .setStatus(ongoingStatus)
            .build()

        ongoingActivity.apply(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        isActive = true
    }

    /**
     * Update the ongoing activity with new battery/ANC values.
     * Delegates to [start] which recreates the notification with updated data.
     */
    fun update(
        context: Context,
        deviceName: String,
        leftBattery: Int,
        rightBattery: Int,
        ancMode: Int
    ) {
        start(context, deviceName, leftBattery, rightBattery, ancMode)
    }

    /**
     * Stop and dismiss the ongoing activity notification.
     */
    fun stop(context: Context) {
        if (!isActive) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        isActive = false
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AirBridge Status",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shows AirPods connection status on the watch"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildStatusText(leftBattery: Int, rightBattery: Int): String {
        val left = if (leftBattery >= 0) "${leftBattery}%" else "--%"
        val right = if (rightBattery >= 0) "${rightBattery}%" else "--%"
        return "L:$left R:$right"
    }

    private fun ancModeLabel(ancMode: Int): String {
        return when (ancMode) {
            0x01 -> "Off"
            0x02 -> "ANC"
            0x03 -> "Transparency"
            0x04 -> "Adaptive"
            else -> "ANC"
        }
    }
}
