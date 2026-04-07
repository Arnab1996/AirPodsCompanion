package me.arnabsaha.airpodscompanion.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationDismissReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISMISSED = "me.arnabsaha.airpodscompanion.INFO_NOTIFICATION_DISMISSED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISSED) {
            Log.d("NotifDismiss", "Info notification dismissed by user")
            context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("info_notification_dismissed", true)
                .apply()
        }
    }
}
