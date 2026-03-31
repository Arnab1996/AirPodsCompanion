package me.arnabsaha.airpodscompanion.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "WearMain"

class WearMainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient

    // State holders
    var connected by mutableStateOf(false)
    var deviceName by mutableStateOf("AirPods Pro")
    var leftBattery by mutableIntStateOf(-1)
    var rightBattery by mutableIntStateOf(-1)
    var caseBattery by mutableIntStateOf(-1)
    var ancMode by mutableIntStateOf(0x02) // ANC by default
    var leftInEar by mutableStateOf(false)
    var rightInEar by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)

        // Read initial state
        CoroutineScope(Dispatchers.IO).launch { readInitialState() }

        setContent {
            DisposableEffect(Unit) {
                dataClient.addListener(this@WearMainActivity)
                onDispose { dataClient.removeListener(this@WearMainActivity) }
            }
            WearApp()
        }
    }

    private suspend fun readInitialState() {
        try {
            val items = dataClient.dataItems.await()
            for (item in items) {
                if (item.uri.path == DataPaths.STATE_PATH) {
                    updateFromDataMap(DataMapItem.fromDataItem(item))
                }
            }
            items.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read initial state: ${e.message}")
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == DataPaths.STATE_PATH) {
                updateFromDataMap(DataMapItem.fromDataItem(event.dataItem))
            }
        }
    }

    private fun updateFromDataMap(item: DataMapItem) {
        val map = item.dataMap
        connected = map.getBoolean(DataPaths.KEY_CONNECTED, false)
        deviceName = map.getString(DataPaths.KEY_DEVICE_NAME, "AirPods Pro") ?: "AirPods Pro"
        leftBattery = map.getInt(DataPaths.KEY_LEFT_BATTERY, -1)
        rightBattery = map.getInt(DataPaths.KEY_RIGHT_BATTERY, -1)
        caseBattery = map.getInt(DataPaths.KEY_CASE_BATTERY, -1)
        ancMode = map.getInt(DataPaths.KEY_ANC_MODE, 0x02)
        leftInEar = map.getBoolean(DataPaths.KEY_LEFT_IN_EAR, false)
        rightInEar = map.getBoolean(DataPaths.KEY_RIGHT_IN_EAR, false)
        Log.d(TAG, "State updated: L=$leftBattery% R=$rightBattery% C=$caseBattery% ANC=$ancMode")
    }

    private fun sendCommand(cmd: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearMainActivity).connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, DataPaths.COMMAND_PATH, cmd.toByteArray())
                    Log.d(TAG, "Sent command '$cmd' to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command: ${e.message}")
            }
        }
    }

    @Composable
    fun WearApp() {
        MaterialTheme {
            if (connected) {
                DashboardScreen()
            } else {
                DisconnectedScreen()
            }
        }
    }

    @Composable
    fun DashboardScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Device name
            Text(
                deviceName,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            // Battery row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BatteryCircle("L", leftBattery, leftInEar)
                BatteryCircle("R", rightBattery, rightInEar)
                BatteryCircle("C", caseBattery, false)
            }

            Spacer(Modifier.height(10.dp))

            // ANC mode buttons
            val ancName = when (ancMode) {
                0x01 -> "Off"
                0x02 -> "ANC"
                0x03 -> "Transp."
                0x04 -> "Adaptive"
                else -> "ANC"
            }
            Text(ancName, fontSize = 11.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AncButton("Off", ancMode == 0x01) { sendCommand(DataPaths.CMD_ANC_OFF) }
                AncButton("ANC", ancMode == 0x02) { sendCommand(DataPaths.CMD_ANC_ON) }
                AncButton("T", ancMode == 0x03) { sendCommand(DataPaths.CMD_ANC_TRANSPARENCY) }
                AncButton("A", ancMode == 0x04) { sendCommand(DataPaths.CMD_ANC_ADAPTIVE) }
            }
        }
    }

    @Composable
    fun BatteryCircle(label: String, level: Int, inEar: Boolean) {
        val progress = if (level > 0) level / 100f else 0f
        val color = when {
            level < 0 -> Color.Gray
            level <= 10 -> Color(0xFFFF3B30)
            level <= 20 -> Color(0xFFFF9500)
            else -> Color(0xFF34C759)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                    startAngle = 270f,
                    indicatorColor = color,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeWidth = 4.dp
                )
                Text(
                    if (level >= 0) "$level" else "--",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inEar) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF34C759)))
                    Spacer(Modifier.width(2.dp))
                }
                Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }

    @Composable
    fun AncButton(label: String, selected: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(36.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (selected) Color(0xFF007AFF) else Color(0xFF1C1C1E)
            )
        ) {
            Text(label, fontSize = 9.sp, color = Color.White, textAlign = TextAlign.Center)
        }
    }

    @Composable
    fun DisconnectedScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎧", fontSize = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("AirBridge", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Open AirBridge on\nyour phone to connect",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
