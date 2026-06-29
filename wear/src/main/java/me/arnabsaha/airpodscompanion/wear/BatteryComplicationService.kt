package me.arnabsaha.airpodscompanion.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Watch face complication showing AirPods battery as "L:85 R:88" or a ranged gauge.
 */
class BatteryComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, BatteryComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("L:95 R:92").build(),
                contentDescription = PlainComplicationText.Builder("AirPods Battery").build()
            ).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 90f, min = 0f, max = 100f,
                contentDescription = PlainComplicationText.Builder("AirPods Battery").build()
            )
                .setText(PlainComplicationText.Builder("90%").build())
                .build()
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val left = DataLayerListenerService.latestLeftBattery
        val right = DataLayerListenerService.latestRightBattery
        val connected = DataLayerListenerService.latestConnected

        // Tapping the complication opens the watch app
        val tapAction = PendingIntent.getActivity(
            this, 0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                val text = if (connected && left >= 0) "L:$left R:$right" else "—"
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text).build(),
                    contentDescription = PlainComplicationText.Builder("AirPods: $text").build()
                )
                    .setTapAction(tapAction)
                    .build()
            }
            ComplicationType.RANGED_VALUE -> {
                // Show the LOWER of the two buds so the gauge warns as early as the first one to die
                val low = when {
                    connected && left >= 0 && right >= 0 -> minOf(left, right).toFloat()
                    connected && left >= 0 -> left.toFloat()
                    else -> 0f
                }
                val text = if (connected && left >= 0) "${low.toInt()}%" else "—"
                RangedValueComplicationData.Builder(
                    value = low, min = 0f, max = 100f,
                    contentDescription = PlainComplicationText.Builder("AirPods: $text").build()
                )
                    .setText(PlainComplicationText.Builder(text).build())
                    .setTapAction(tapAction)
                    .build()
            }
            else -> throw IllegalArgumentException("Unsupported: ${request.complicationType}")
        }
    }
}
