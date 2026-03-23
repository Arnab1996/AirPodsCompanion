package me.arnabsaha.airpodscompanion.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode

/**
 * Quick Settings tile for cycling ANC modes.
 * Tap to cycle: Off → ANC → Transparency → Adaptive → Off
 */
class AncTileService : TileService() {

    companion object {
        private const val TAG = "AncTile"
    }

    private var airPodsService: AirPodsService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            airPodsService = (service as AirPodsService.LocalBinder).getService()
            bound = true
            updateTileState()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            airPodsService = null
            bound = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindService(Intent(this, AirPodsService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        super.onStopListening()
        if (bound) {
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val service = airPodsService ?: return
        val currentMode = service.ancMode.value

        val nextMode = when (currentMode) {
            NoiseControlMode.OFF -> NoiseControlMode.NOISE_CANCELLATION
            NoiseControlMode.NOISE_CANCELLATION -> NoiseControlMode.TRANSPARENCY
            NoiseControlMode.TRANSPARENCY -> NoiseControlMode.ADAPTIVE
            NoiseControlMode.ADAPTIVE -> NoiseControlMode.OFF
            else -> NoiseControlMode.OFF
        }

        service.setNoiseControlMode(nextMode)
        Log.d(TAG, "ANC mode cycled to: ${modeName(nextMode)}")

        // Update tile after a short delay for the state to propagate
        qsTile?.let { tile ->
            tile.label = modeName(nextMode)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val service = airPodsService
        val tile = qsTile ?: return

        if (service == null || !service.transport.isConnected) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "ANC"
            tile.subtitle = "Not connected"
        } else {
            tile.state = Tile.STATE_ACTIVE
            val mode = service.ancMode.value
            tile.label = modeName(mode)
            tile.subtitle = "Tap to change"
        }
        tile.updateTile()
    }

    private fun modeName(mode: Byte): String = when (mode) {
        NoiseControlMode.OFF -> "Off"
        NoiseControlMode.NOISE_CANCELLATION -> "Noise Cancel"
        NoiseControlMode.TRANSPARENCY -> "Transparency"
        NoiseControlMode.ADAPTIVE -> "Adaptive"
        else -> "ANC"
    }
}
