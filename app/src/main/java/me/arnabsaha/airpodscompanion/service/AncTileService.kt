package me.arnabsaha.airpodscompanion.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode

/**
 * Quick Settings tile for cycling ANC modes.
 * Tap to cycle: Off → ANC → Transparency → Adaptive → Off
 *
 * The tile reflects the device's CONFIRMED ANC mode (echoed back over AACP), never an
 * optimistic guess — so it can't lie if a command fails or the mode changes from elsewhere.
 */
class AncTileService : TileService() {

    companion object {
        private const val TAG = "AncTile"
    }

    private var airPodsService: AirPodsService? = null
    private var bound = false
    private var collectScope: CoroutineScope? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val svc = (service as AirPodsService.LocalBinder).getService()
            airPodsService = svc
            bound = true
            // Keep the tile in sync with the real ANC mode + connection, from any source
            collectScope?.cancel()
            collectScope = CoroutineScope(Dispatchers.Main).also { scope ->
                scope.launch { svc.ancMode.collect { updateTileState() } }
                scope.launch { svc.connectionState.collect { updateTileState() } }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            airPodsService = null
            bound = false
            collectScope?.cancel()
            collectScope = null
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindService(Intent(this, AirPodsService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        super.onStopListening()
        collectScope?.cancel()
        collectScope = null
        if (bound) {
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
    }

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

        // Send the command; the tile repaints when the device echoes the new mode back
        service.setNoiseControlMode(nextMode)
        Log.d(TAG, "ANC mode cycle requested: ${modeName(nextMode)}")
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
