package me.arnabsaha.airpodscompanion.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.arnabsaha.airpodscompanion.service.AirPodsService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d("BootReceiver", "Boot/update detected, starting AirPodsService")
            val serviceIntent = Intent(context, AirPodsService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
