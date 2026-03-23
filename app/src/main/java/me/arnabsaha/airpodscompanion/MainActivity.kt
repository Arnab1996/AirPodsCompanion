package me.arnabsaha.airpodscompanion

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwipeUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode
import me.arnabsaha.airpodscompanion.ui.composables.ConnectionAnimation
import me.arnabsaha.airpodscompanion.ui.theme.AirPodsCompanionTheme
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleOrange
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModelFactory

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirPodsCompanionTheme {
                val viewModel: AirPodsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = AirPodsViewModelFactory(application)
                )
                MainScreen(viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Main Screen Router
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(vm: AirPodsViewModel) {
    val context = LocalContext.current
    val perms = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(
            "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.READ_PHONE_STATE", "android.permission.ANSWER_PHONE_CALLS"
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf("android.permission.POST_NOTIFICATIONS") else emptyList()
        else listOf(
            "android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_PHONE_STATE"
        )
    )

    if (perms.allPermissionsGranted) {
        LaunchedEffect(Unit) { vm.bindService() }
        DisposableEffect(Unit) { onDispose { vm.unbindService() } }

        val connState by vm.connectionState.collectAsState()
        when (connState) {
            AacpTransport.ConnectionState.CONNECTED -> DashboardScreen(vm)
            AacpTransport.ConnectionState.DISCONNECTED,
            AacpTransport.ConnectionState.FAILED -> DevicePickerScreen(vm)
            else -> ConnectingScreen(vm, connState)
        }
    } else {
        PermissionsScreen(perms, Settings.canDrawOverlays(context))
    }
}

// ═══════════════════════════════════════════════════════════════
// Dashboard Screen (shown when AACP is connected)
// ═══════════════════════════════════════════════════════════════

@Composable
fun DashboardScreen(vm: AirPodsViewModel) {
    val battery by vm.battery.collectAsState()
    val earState by vm.earState.collectAsState()
    val ancMode by vm.ancMode.collectAsState()
    val deviceName by vm.bondedDeviceName.collectAsState()
    val scrollState = rememberScrollState()

    // Persisted settings from ViewModel
    val caEnabled by vm.caEnabled.collectAsState()
    val avEnabled by vm.avEnabled.collectAsState()
    val edEnabled by vm.edEnabled.collectAsState()
    val oneBudAnc by vm.oneBudAnc.collectAsState()
    val volumeSwipe by vm.volumeSwipe.collectAsState()
    val sleepDetection by vm.sleepDetection.collectAsState()
    val inCaseTone by vm.inCaseTone.collectAsState()
    val headTrackingOn by vm.headTracking.collectAsState()
    val chimeVolume by vm.chimeVolume.collectAsState()
    val stemAction by vm.stemAction.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Header with connection badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName ?: "AirPods Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AppleGreen))
                    Spacer(Modifier.width(6.dp))
                    Text("Connected", style = MaterialTheme.typography.bodySmall, color = AppleGreen)
                }
            }
            // Ear indicators in header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    EarDot("L", earState.leftInEar)
                    Spacer(Modifier.width(12.dp))
                    EarDot("R", earState.rightInEar)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Battery Card — large circular gauges
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BatteryGauge("Left", battery?.leftLevel ?: -1, battery?.leftCharging == true)
                BatteryGauge("Right", battery?.rightLevel ?: -1, battery?.rightCharging == true)
                BatteryGauge("Case", battery?.caseLevel ?: -1, battery?.caseCharging == true)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Noise Control — segmented with icons
        Text("Noise Control", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        AncSegmentedControl(currentMode = ancMode, onModeChange = { vm.setNoiseControlMode(it) })

        Spacer(Modifier.height(20.dp))

        // Settings section with proper toggles and icons
        Text("Features", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        SectionCard {
            IconToggleRow(
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                title = "Conversational Awareness",
                subtitle = "Lower volume when you speak",
                enabled = caEnabled,
                onToggle = { vm.setConversationalAwareness(it) }
            )

            Divider()

            IconToggleRow(
                icon = Icons.Default.GraphicEq,
                title = "Adaptive Volume",
                subtitle = "Adjust to your environment",
                enabled = avEnabled,
                onToggle = { vm.setAdaptiveVolume(it) }
            )

            Divider()

            IconToggleRow(
                icon = Icons.Default.Headphones,
                title = "Ear Detection",
                subtitle = "Auto play/pause",
                enabled = edEnabled,
                onToggle = { vm.setEarDetection(it) }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Device section
        Text("Device", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        SectionCard {
            var showRenameDialog by remember { mutableStateOf(false) }

            // Rename row with pencil icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRenameDialog = true }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Name", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(deviceName ?: "AirPods Pro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }

            if (showRenameDialog) {
                RenameDialog(
                    currentName = deviceName ?: "AirPods Pro",
                    onDismiss = { showRenameDialog = false },
                    onRename = { newName ->
                        vm.renameAirPods(newName)
                        showRenameDialog = false
                    }
                )
            }

            Divider()

            // Spatial Audio with icon
            IconToggleRow(
                icon = Icons.Default.SurroundSound,
                title = "Spatial Audio",
                subtitle = "Head tracking for immersive sound",
                enabled = headTrackingOn,
                onToggle = { vm.toggleHeadTracking() }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Stem & Accessibility
        Text("Stem & Accessibility", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        SectionCard {
            // Chime Volume Slider with feedback
            var localChimeVolume by remember { mutableStateOf(chimeVolume) }
            LaunchedEffect(chimeVolume) { localChimeVolume = chimeVolume }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, "Chime Volume", Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Chime Volume", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("${localChimeVolume.toInt()}%", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    androidx.compose.material3.Slider(
                        value = localChimeVolume,
                        onValueChange = { localChimeVolume = it },
                        onValueChangeFinished = { vm.setChimeVolume(localChimeVolume) },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Divider()

            IconToggleRow(
                icon = Icons.Default.HearingDisabled,
                title = "One Bud ANC",
                subtitle = "Keep noise cancellation with one earbud",
                enabled = oneBudAnc,
                onToggle = { vm.setOneBudAnc(it) }
            )

            Divider()

            IconToggleRow(
                icon = Icons.Default.SwipeUp,
                title = "Volume Swipe",
                subtitle = "Swipe stem to adjust volume",
                enabled = volumeSwipe,
                onToggle = { vm.setVolumeSwipe(it) }
            )

            Divider()

            IconToggleRow(
                icon = Icons.Default.Bedtime,
                title = "Sleep Detection",
                subtitle = "Auto-pause when you fall asleep",
                enabled = sleepDetection,
                onToggle = { vm.setSleepDetection(it) }
            )

            Divider()

            IconToggleRow(
                icon = Icons.Default.NotificationsActive,
                title = "In-Case Tone",
                subtitle = "Sound when placing buds in case",
                enabled = inCaseTone,
                onToggle = { vm.setInCaseTone(it) }
            )

            Divider()

            // Stem Long Press Configuration — Noise Control vs Off
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Press & Hold", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Currently: $stemAction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                androidx.compose.material3.TextButton(onClick = {
                    val newAction = if (stemAction == "Noise Control") "Off" else "Noise Control"
                    vm.setStemAction(newAction)
                }) {
                    Text(if (stemAction == "Noise Control") "Set to Off" else "Set to Noise Control",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Device Info Card
        Text("About", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        SectionCard {
            InfoRow("Model", deviceName ?: "AirPods Pro")
            Divider()
            InfoRow("Connection", "AACP over L2CAP (PSM 0x1001)")
            Divider()
            InfoRow("App Version", "0.1.0")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun BatteryGauge(label: String, level: Int, isCharging: Boolean) {
    val displayLevel = if (level < 0) -1 else level
    val color = when {
        displayLevel < 0 -> Color.Gray.copy(alpha = 0.3f)
        displayLevel <= 10 -> AppleRed
        displayLevel <= 20 -> AppleOrange
        else -> AppleGreen
    }
    val animatedLevel by animateFloatAsState(
        targetValue = if (displayLevel >= 0) displayLevel / 100f else 0f,
        animationSpec = tween(800), label = "battery"
    )

    val accessLabel = "$label battery: ${if (displayLevel >= 0) "$displayLevel percent" else "unavailable"}${if (isCharging && displayLevel >= 0) ", charging" else ""}"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = accessLabel
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(68.dp)) {
                drawArc(Color.Gray.copy(alpha = 0.1f), -90f, 360f, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(10f))
                if (displayLevel >= 0) {
                    drawArc(color, -90f, 360f * animatedLevel, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(10f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
            if (displayLevel >= 0) {
                Text(
                    text = "$displayLevel%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "N/A",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        if (isCharging && displayLevel >= 0) {
            Text("Charging", style = MaterialTheme.typography.labelSmall, color = AppleGreen)
        }
    }
}

@Composable
fun EarDot(label: String, inEar: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label earbud: ${if (inEar) "in ear" else "not in ear"}"
        }
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (inEar) AppleGreen else Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
fun AncSegmentedControl(currentMode: Byte, onModeChange: (Byte) -> Unit) {
    data class AncOption(val label: String, val mode: Byte, val icon: ImageVector)
    val modes = listOf(
        AncOption("Off", NoiseControlMode.OFF, Icons.AutoMirrored.Filled.VolumeOff),
        AncOption("ANC", NoiseControlMode.NOISE_CANCELLATION, Icons.Default.HearingDisabled),
        AncOption("Transparent", NoiseControlMode.TRANSPARENCY, Icons.AutoMirrored.Filled.VolumeUp),
        AncOption("Adaptive", NoiseControlMode.ADAPTIVE, Icons.Default.GraphicEq)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { opt ->
            val isSelected = currentMode == opt.mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onModeChange(opt.mode) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        opt.icon, opt.label, Modifier.size(20.dp),
                        tint = if (isSelected) Color.White
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = opt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun IconToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: ${if (enabled) "enabled" else "disabled"}. $subtitle"
            }
            .clickable { onToggle(!enabled) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = AppleGreen,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename AirPods", style = MaterialTheme.typography.titleLarge) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onRename(name) }) {
                Text("Rename", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// ═══════════════════════════════════════════════════════════════
// Connecting Screen (shown during AACP handshake)
// ═══════════════════════════════════════════════════════════════

@Composable
fun ConnectingScreen(vm: AirPodsViewModel, state: AacpTransport.ConnectionState) {
    val deviceName by vm.bondedDeviceName.collectAsState()
    val error by vm.connectionError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ConnectionAnimation(
            isConnecting = state == AacpTransport.ConnectionState.CONNECTING ||
                           state == AacpTransport.ConnectionState.HANDSHAKING,
            isConnected = false
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = deviceName ?: "AirPods",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        if (state == AacpTransport.ConnectionState.RECONNECTING) {
            Spacer(Modifier.height(8.dp))
            Text("Connection lost, reconnecting...",
                style = MaterialTheme.typography.bodySmall,
                color = AppleOrange)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = AppleRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Device Picker Screen (shown when disconnected — lists bonded AirPods)
// ═══════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun DevicePickerScreen(vm: AirPodsViewModel) {
    val bondedDevices by vm.bondedAirPodsList.collectAsState()
    val deviceName by vm.bondedDeviceName.collectAsState()
    val error by vm.connectionError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Headphones, null, Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("AirBridge", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(4.dp))
        Text("Not connected", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))

        Spacer(Modifier.height(32.dp))

        if (bondedDevices.isEmpty()) {
            // No bonded AirPods — show setup instructions
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.airpods_case),
                        contentDescription = "AirPods",
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(24.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Pair your AirPods first",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Text("Go to Android Settings → Bluetooth\nHold the case button until LED flashes white",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.autoConnect() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        } else {
            // Show bonded AirPods devices
            Text("Your AirPods", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 10.dp))

            bondedDevices.forEach { airpods ->
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.connectToDevice(airpods.device) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // AirPods icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HeadsetMic, null, Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(airpods.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(airpods.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                        if (airpods.isCurrentlyConnected) {
                            StatusChip("Active", AppleGreen)
                        } else {
                            Text("Connect", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.weight(1f))

            // Error message
            if (error != null) {
                SectionCard {
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                        color = AppleRed, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            // Retry button at bottom
            Button(
                onClick = { vm.autoConnect() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Connect", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium, color = color)
    }
}

// ═══════════════════════════════════════════════════════════════
// Loading + Permissions Screens
// ═══════════════════════════════════════════════════════════════

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null,
                Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Starting AirBridge...", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(perms: com.google.accompanist.permissions.MultiplePermissionsState, canDrawOverlays: Boolean) {
    val context = LocalContext.current
    val pulse = rememberInfiniteTransition(label = "p")
    val scale by pulse.animateFloat(1f, 1.05f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "s")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Default.Headphones, null,
            Modifier.size(72.dp).scale(scale),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("AirBridge", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Grant permissions to connect to your AirPods",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        PermCard("Bluetooth", "Communicate with AirPods", Icons.Default.Bluetooth,
            perms.permissions.filter { it.permission.contains("BLUETOOTH") }.all { it.status.isGranted })
        PermCard("Notifications", "Battery alerts", Icons.Default.Notifications,
            perms.permissions.find { it.permission.contains("NOTIFICATIONS") }?.status?.isGranted == true)
        PermCard("Phone", "Head gesture call control", Icons.Default.Phone,
            perms.permissions.filter { it.permission.contains("PHONE") || it.permission.contains("CALLS") }.all { it.status.isGranted })

        Spacer(Modifier.height(24.dp))
        Button(onClick = { perms.launchMultiplePermissionRequest() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)) {
            Text("Grant Permissions", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()))
        }, modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !canDrawOverlays,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canDrawOverlays) Color.Gray else MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)) {
            Text(if (canDrawOverlays) "Overlay Granted" else "Grant Overlay",
                style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun PermCard(title: String, desc: String, icon: ImageVector, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                .background(if (granted) MaterialTheme.colorScheme.primary.copy(0.12f)
                    else Color.Gray.copy(0.1f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, title, Modifier.size(20.dp),
                    tint = if (granted) MaterialTheme.colorScheme.primary else Color.Gray)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                tint = if (granted) AppleGreen else Color.Gray.copy(0.2f))
        }
    }
}
