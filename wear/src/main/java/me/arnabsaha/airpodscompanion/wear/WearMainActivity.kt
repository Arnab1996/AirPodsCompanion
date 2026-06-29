package me.arnabsaha.airpodscompanion.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Shared Apple-system palette so the watch matches the phone exactly. */
object WearColors {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Surface = Color(0xFF1C1C1E)
    // Glass backdrop — same deep navy→black + glow as the phone dashboard
    val GradTop = Color(0xFF101D38)
    val GradBottom = Color(0xFF000000)
    val Glow = Color(0xFF0A84FF)
}

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

    /** Deep navy→black gradient with a soft accent glow — the watch's take on the glass look. */
    @Composable
    fun GlassBackground(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(WearColors.GradTop, WearColors.GradBottom)))
                val glowCenter = Offset(size.width * 0.5f, size.height * 0.16f)
                val glowRadius = size.minDimension * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(WearColors.Glow.copy(alpha = 0.26f), Color.Transparent),
                        center = glowCenter, radius = glowRadius
                    ),
                    radius = glowRadius, center = glowCenter
                )
            }
            content()
        }
    }

    @Composable
    fun DashboardScreen() {
        GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
            Text(ancName, fontSize = 11.sp, color = WearColors.Blue, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AncButton("Off", ancMode == 0x01) { sendCommand(DataPaths.CMD_ANC_OFF) }
                AncButton("ANC", ancMode == 0x02) { sendCommand(DataPaths.CMD_ANC_ON) }
                AncButton("T", ancMode == 0x03) { sendCommand(DataPaths.CMD_ANC_TRANSPARENCY) }
                AncButton("A", ancMode == 0x04) { sendCommand(DataPaths.CMD_ANC_ADAPTIVE) }
            }
        }
        }
    }

    @Composable
    fun BatteryCircle(label: String, level: Int, inEar: Boolean) {
        val progress = if (level > 0) level / 100f else 0f
        val color = when {
            level < 0 -> Color.Gray
            level <= 10 -> WearColors.Red
            level <= 20 -> WearColors.Orange
            else -> WearColors.Green
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 4.dp.toPx()
                    // Recessed well — inset radial gradient gives the ring neumorphic depth
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.28f)),
                            center = center, radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f - sw
                    )
                    if (level >= 0) {
                        // Soft glow tracing the level, under the crisp indicator
                        val inset = sw / 2f
                        drawArc(
                            color = color.copy(alpha = 0.28f),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            style = Stroke(sw * 1.9f, cap = StrokeCap.Round)
                        )
                    }
                }
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
                    Box(Modifier.size(4.dp).clip(CircleShape).background(WearColors.Green))
                    Spacer(Modifier.width(2.dp))
                }
                Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }

    @Composable
    fun AncButton(label: String, selected: Boolean, onClick: () -> Unit) {
        val haptic = LocalHapticFeedback.current
        // Selected = raised blue glass pill; others = translucent inset glass
        val fill = if (selected)
            Brush.verticalGradient(listOf(WearColors.Blue, WearColors.Blue.copy(alpha = 0.82f)))
        else
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, Color.White.copy(alpha = if (selected) 0.25f else 0.10f), CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun DisconnectedScreen() {
        GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_headphones),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(WearColors.Blue)
            )
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
}
