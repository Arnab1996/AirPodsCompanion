package me.arnabsaha.airpodscompanion.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Wear OS Tile that displays AirPods battery levels and ANC mode.
 * Reads data from [DataLayerListenerService] companion object statics.
 */
class AirBridgeTileService : TileService() {

    companion object {
        private const val RESOURCES_VERSION = "1"

        /**
         * Request a tile content update from anywhere.
         */
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(AirBridgeTileService::class.java)
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val connected = DataLayerListenerService.latestConnected
        val layout = if (connected) connectedLayout() else disconnectedLayout()

        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(
                LayoutElementBuilders.Layout.Builder()
                    .setRoot(layout)
                    .build()
            )
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(entry)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(timeline)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    /**
     * Layout when AirPods are connected: title, battery text, ANC mode.
     * Tapping opens WearMainActivity.
     */
    private fun connectedLayout(): LayoutElementBuilders.LayoutElement {
        val left = DataLayerListenerService.latestLeftBattery
        val right = DataLayerListenerService.latestRightBattery
        val case = DataLayerListenerService.latestCaseBattery
        val ancMode = DataLayerListenerService.latestAncMode
        val deviceName = DataLayerListenerService.latestDeviceName

        val leftText = if (left >= 0) "$left%" else "--%"
        val rightText = if (right >= 0) "$right%" else "--%"
        val caseText = if (case >= 0) "$case%" else "--%"
        val batteryString = "L: $leftText  R: $rightText  Case: $caseText"

        val ancLabel = when (ancMode) {
            0x01 -> "Off"
            0x02 -> "ANC"
            0x03 -> "Transparency"
            0x04 -> "Adaptive"
            else -> "ANC"
        }

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(
                        ComponentName(this, WearMainActivity::class.java).className
                    )
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(launchAction)
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .build()

        // Title
        val titleText = LayoutElementBuilders.Text.Builder()
            .setText(deviceName)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(14f))
                    .setColor(argb(0xFFAAAAAAu.toInt()))
                    .build()
            )
            .build()

        // Battery info
        val batteryText = LayoutElementBuilders.Text.Builder()
            .setText(batteryString)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(13f))
                    .setColor(argb(0xFFFFFFFFu.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()

        // ANC mode
        val ancText = LayoutElementBuilders.Text.Builder()
            .setText(ancLabel)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setColor(argb(0xFF007AFFu.toInt()))
                    .build()
            )
            .build()

        // Spacers
        val spacerSmall = LayoutElementBuilders.Spacer.Builder()
            .setHeight(dp(6f))
            .build()

        val spacerMedium = LayoutElementBuilders.Spacer.Builder()
            .setHeight(dp(8f))
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setModifiers(modifiers)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(titleText)
            .addContent(spacerMedium)
            .addContent(batteryText)
            .addContent(spacerSmall)
            .addContent(ancText)
            .build()
    }

    /**
     * Layout when AirPods are disconnected.
     */
    private fun disconnectedLayout(): LayoutElementBuilders.LayoutElement {
        val text = LayoutElementBuilders.Text.Builder()
            .setText("AirPods not connected")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(14f))
                    .setColor(argb(0xFF888888u.toInt()))
                    .build()
            )
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text)
            .build()
    }
}
