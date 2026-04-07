package me.arnabsaha.airpodscompanion.wear

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Background service that receives AirPods state from the phone app via Data Layer.
 * Updates complication data when battery changes.
 */
class DataLayerListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataSync"
        var latestLeftBattery: Int = -1
            private set
        var latestRightBattery: Int = -1
            private set
        var latestCaseBattery: Int = -1
            private set
        var latestConnected: Boolean = false
            private set
        var latestAncMode: Int = 0x02
            private set
        var latestDeviceName: String = "AirPods Pro"
            private set
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.dataItem.uri.path == DataPaths.STATE_PATH) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                latestConnected = map.getBoolean(DataPaths.KEY_CONNECTED, false)
                latestDeviceName = map.getString(DataPaths.KEY_DEVICE_NAME, "AirPods Pro") ?: "AirPods Pro"
                latestLeftBattery = map.getInt(DataPaths.KEY_LEFT_BATTERY, -1)
                latestRightBattery = map.getInt(DataPaths.KEY_RIGHT_BATTERY, -1)
                latestCaseBattery = map.getInt(DataPaths.KEY_CASE_BATTERY, -1)
                latestAncMode = map.getInt(DataPaths.KEY_ANC_MODE, 0x02)
                Log.d(TAG, "Background update: L=$latestLeftBattery% R=$latestRightBattery% C=$latestCaseBattery% ANC=$latestAncMode")

                // Request complication update
                BatteryComplicationService.requestUpdate(this)

                // Update ongoing activity
                if (latestConnected) {
                    OngoingActivityManager.update(
                        this, latestDeviceName,
                        latestLeftBattery, latestRightBattery, latestAncMode
                    )
                } else {
                    OngoingActivityManager.stop(this)
                }

                // Request tile update
                try {
                    AirBridgeTileService.requestUpdate(this)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to request tile update: ${e.message}")
                }
            }
        }
    }
}
