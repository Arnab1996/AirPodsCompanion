package me.arnabsaha.airpodscompanion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import me.arnabsaha.airpodscompanion.ui.theme.GlassBackdrop
import me.arnabsaha.airpodscompanion.ui.theme.LocalHazeState
import me.arnabsaha.airpodscompanion.ui.theme.Radius
import me.arnabsaha.airpodscompanion.ui.theme.glassBorder
import me.arnabsaha.airpodscompanion.ui.theme.glassEffect
import me.arnabsaha.airpodscompanion.ui.theme.glassStyle
import me.arnabsaha.airpodscompanion.ui.theme.rememberGlassState
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModelFactory
import kotlin.math.roundToInt

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back press: close activity and remove from recents, service keeps running
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAndRemoveTask()
            }
        })

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
        val btProfileConnected by vm.isBluetoothProfileConnected.collectAsState()
        val gateEarState by vm.earState.collectAsState()
        val bothInCase = gateEarState.leftInCase && gateEarState.rightInCase

        // Show the dashboard only while the AirPods are actually in use. Stay through brief AACP
        // reconnects, but revert to the picker quickly once they're stowed (both in case), the
        // audio profile drops, or the link fails — no long "Reconnecting…" limbo.
        var showDashboard by remember { mutableStateOf(false) }
        LaunchedEffect(connState, btProfileConnected, bothInCase) {
            when {
                // Both buds back in the case → user is done; return to origin promptly.
                bothInCase -> {
                    kotlinx.coroutines.delay(600)
                    showDashboard = false
                }
                connState == AacpTransport.ConnectionState.CONNECTED && btProfileConnected -> {
                    if (!showDashboard) {
                        kotlinx.coroutines.delay(400)
                        showDashboard = true
                    }
                }
                connState == AacpTransport.ConnectionState.FAILED -> showDashboard = false
                // Audio profile gone (latch already expired) → real disconnect.
                !btProfileConnected -> {
                    kotlinx.coroutines.delay(800)
                    showDashboard = false
                }
                connState == AacpTransport.ConnectionState.DISCONNECTED -> {
                    kotlinx.coroutines.delay(1200)
                    showDashboard = false
                }
                // CONNECTING / HANDSHAKING / RECONNECTING with audio still latched: hold.
            }
        }

        val btEnabled = rememberBluetoothEnabled()
        val screen = when {
            !btEnabled -> "bt_off"
            showDashboard -> "dashboard"
            // Active first-time connect → connecting animation. Anything else (disconnected,
            // failed, or reconnecting to an unreachable device) → the picker, so the user gets
            // an actionable screen instead of staring at "Reconnecting…".
            connState == AacpTransport.ConnectionState.CONNECTING ||
            connState == AacpTransport.ConnectionState.HANDSHAKING ||
            connState == AacpTransport.ConnectionState.CONNECTED -> "connecting"
            else -> "picker"
        }
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "screen"
        ) { target ->
            when (target) {
                "bt_off" -> BluetoothOffScreen()
                "dashboard" -> DashboardScreen(vm)
                "picker" -> DevicePickerScreen(vm)
                else -> ConnectingScreen(vm, connState, btProfileConnected)
            }
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
    var showFindMy by remember { mutableStateOf(false) }

    if (showFindMy) {
        androidx.activity.compose.BackHandler { showFindMy = false }
        me.arnabsaha.airpodscompanion.ui.screens.FindMyAirPodsScreen(vm, onBack = { showFindMy = false })
        return
    }

    val battery by vm.battery.collectAsState()
    val earState by vm.earState.collectAsState()
    val ancMode by vm.ancMode.collectAsState()
    val deviceName by vm.bondedDeviceName.collectAsState()
    val connState by vm.connectionState.collectAsState()
    val scrollState = rememberScrollState()
    val leAudio by vm.leAudioCapability.collectAsState()
    val batteryAlertThreshold by vm.batteryAlertThreshold.collectAsState()

    // Persisted settings from ViewModel
    val caEnabled by vm.caEnabled.collectAsState()
    val avEnabled by vm.avEnabled.collectAsState()
    val edEnabled by vm.edEnabled.collectAsState()
    val oneBudAnc by vm.oneBudAnc.collectAsState()
    val volumeSwipe by vm.volumeSwipe.collectAsState()
    val sleepDetection by vm.sleepDetection.collectAsState()
    val inCaseTone by vm.inCaseTone.collectAsState()
    val headTrackingOn by vm.headTracking.collectAsState()
    val headTrackingLoading by vm.headTrackingLoading.collectAsState()
    val chimeVolume by vm.chimeVolume.collectAsState()
    val stemAction by vm.stemAction.collectAsState()
    val allowOff by vm.allowOff.collectAsState()
    val deviceInfo by vm.deviceInfo.collectAsState()
    val autoResume by vm.autoResume.collectAsState()

    val hazeState = rememberGlassState()
    Box(modifier = Modifier.fillMaxSize()) {
      GlassBackdrop(hazeState)
      CompositionLocalProvider(LocalHazeState provides hazeState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    val isLive = connState == AacpTransport.ConnectionState.CONNECTED
                    val statusColor = if (isLive) AppleGreen else AppleOrange
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isLive) "Connected" else "Reconnecting…",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
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
                BatteryGauge("Left", battery?.leftLevel ?: -1, battery?.leftCharging == true, isLoading = battery == null)
                BatteryGauge("Right", battery?.rightLevel ?: -1, battery?.rightCharging == true, isLoading = battery == null)
                BatteryGauge("Case", battery?.caseLevel ?: -1, battery?.caseCharging == true, isLoading = battery == null)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Noise Control — segmented with icons
        SectionHeader("Noise Control")

        AncSegmentedControl(currentMode = ancMode, onModeChange = { vm.setNoiseControlMode(it) })

        Spacer(Modifier.height(20.dp))

        // ═══ Find My + Codec + Battery Alerts (key utilities, kept near the top) ═══
        StatusUtilityCard(
            leAudioText = leAudio?.displayText ?: "Checking…",
            batteryAlertThreshold = batteryAlertThreshold,
            onFindMy = { showFindMy = true },
            onThresholdChange = { vm.setBatteryAlertThreshold(it) }
        )

        Spacer(Modifier.height(20.dp))

        // ═══ Audio Section (macOS-inspired flat rows) ═══
        SectionHeader("Audio")
        SectionCard {
            SettingToggle("Personalized Volume", "Adjusts volume in response to your environment",
                enabled = avEnabled, onToggle = { vm.setAdaptiveVolume(it) })
            Divider()
            SettingToggle("Conversation Awareness",
                "Lowers media volume and reduces background noise when you start speaking",
                enabled = caEnabled, onToggle = { vm.setConversationalAwareness(it) })
            Divider()
            // Chime Volume with labeled slider
            var localChimeVolume by remember { mutableStateOf(chimeVolume) }
            LaunchedEffect(chimeVolume) { localChimeVolume = chimeVolume }
            val chimeHaptic = LocalHapticFeedback.current
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chime Volume", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("${localChimeVolume.roundToInt()}%", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    AppleSlider(
                        value = localChimeVolume,
                        onValueChange = { localChimeVolume = it },
                        onValueChangeFinished = {
                            chimeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setChimeVolume(localChimeVolume)
                            vm.previewChime()
                        },
                        valueRange = 0f..100f,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
            Divider()
            SettingToggle("Automatic Ear Detection", "Auto play/pause when earbuds are removed",
                enabled = edEnabled, onToggle = { vm.setEarDetection(it) })
            Divider()
            SettingToggle("Pause When Falling Asleep", "Automatically pause media when sleep is detected",
                enabled = sleepDetection, onToggle = { vm.setSleepDetection(it) })
            Divider()
            SettingToggle("One Bud ANC", "Keep noise cancellation active with a single earbud",
                enabled = oneBudAnc, onToggle = { vm.setOneBudAnc(it) })
            Divider()
            SettingToggle("Volume Swipe", "Swipe the stem to adjust volume",
                enabled = volumeSwipe, onToggle = { vm.setVolumeSwipe(it) })
            Divider()
            SettingToggle("Resume Music on Connect", "Start playback automatically when your AirPods connect",
                enabled = autoResume, onToggle = { vm.setAutoResume(it) })
        }

        Spacer(Modifier.height(20.dp))

        // ═══ Head Gestures Section ═══
        SectionHeader("Head Gestures")
        SectionCard {
            SettingToggle("Head Gestures",
                if (headTrackingLoading) "Starting in a moment…"
                else "Move your head to answer or decline calls. Keep both AirPods in your ears.",
                enabled = headTrackingOn, onToggle = { vm.toggleHeadTracking() })
            if (headTrackingLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Warming up — battery & ear detection first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            // Live status once warm-up completes — and a flash when a gesture fires
            val headGesture by vm.headGesture.collectAsState()
            var gestureFlash by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(headGesture) {
                when (headGesture?.second) {
                    "nod" -> { gestureFlash = "Nodded — Yes ✓"; kotlinx.coroutines.delay(2500); gestureFlash = null }
                    "shake" -> { gestureFlash = "Shook — No ✗"; kotlinx.coroutines.delay(2500); gestureFlash = null }
                }
            }
            if (headTrackingOn && !headTrackingLoading) {
                val flashing = gestureFlash != null
                val statusColor = if (flashing) MaterialTheme.colorScheme.primary else AppleGreen
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        gestureFlash ?: "Listening — nod to accept, shake to decline",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
            Divider()
            SettingInfo("Accept, Reply", "Up and Down")
            Divider()
            SettingInfo("Decline, Dismiss", "Side to Side")
        }

        Spacer(Modifier.height(20.dp))

        // ═══ Case & Stem Section ═══
        SectionCard {
            SettingToggle("Enable Charging Case Sounds", "Play a sound when placing buds in the case",
                enabled = inCaseTone, onToggle = { vm.setInCaseTone(it) })
            Divider()
            SettingToggle("Off Listening Mode",
                "When on, listening modes will include an Off option. Loud sounds are not reduced in Off mode.",
                enabled = allowOff, onToggle = { vm.setAllowOff(it) })
            Divider()
            // Press and Hold configuration
            var showStemDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { showStemDialog = true }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Press and Hold", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stemAction, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            if (showStemDialog) {
                StemActionDialog(
                    current = stemAction,
                    onDismiss = { showStemDialog = false },
                    onSelect = { vm.setStemAction(it); showStemDialog = false }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ═══ Device Section ═══
        SectionCard {
            var showRenameDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showRenameDialog = true }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Name", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(deviceName ?: "AirPods Pro", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (showRenameDialog) {
                RenameDialog(currentName = deviceName ?: "AirPods Pro",
                    onDismiss = { showRenameDialog = false },
                    onRename = { vm.renameAirPods(it); showRenameDialog = false })
            }
            Divider()
            SettingInfo("Model", deviceInfo?.modelNumber?.ifBlank { null } ?: "—", dense = true)
            Divider()
            SettingInfo("Firmware", deviceInfo?.firmwareVersion?.ifBlank { null } ?: "—", dense = true)
            Divider()
            SettingInfo("Serial", deviceInfo?.serialNumber?.ifBlank { null } ?: "—", dense = true)
            Divider()
            SettingInfo("Protocol", "AACP / L2CAP", dense = true)
            Divider()
            SettingInfo("App Version", "0.1.0", dense = true)
            Divider()
            // Overlay permission
            val context = LocalContext.current
            val hasOverlay = android.provider.Settings.canDrawOverlays(context)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()))
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Popup Overlay", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (hasOverlay) "Granted" else "Tap to enable",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasOverlay) AppleGreen else MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══ Disconnect + Forget ═══
        var showDisconnectDialog by remember { mutableStateOf(false) }
        androidx.compose.material3.OutlinedButton(
            onClick = { showDisconnectDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(Radius.control),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = AppleRed
            )
        ) {
            Text("Disconnect", style = MaterialTheme.typography.labelLarge)
        }
        if (showDisconnectDialog) {
            DisconnectConfirmDialog(
                deviceName = deviceName ?: "AirPods",
                onDismiss = { showDisconnectDialog = false },
                onConfirm = { vm.disconnect(); showDisconnectDialog = false }
            )
        }

        Spacer(Modifier.height(8.dp))

        var showForgetDialog by remember { mutableStateOf(false) }
        androidx.compose.material3.TextButton(
            onClick = { showForgetDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forget This Device", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        if (showForgetDialog) {
            ForgetConfirmDialog(
                deviceName = deviceName ?: "AirPods",
                onDismiss = { showForgetDialog = false },
                onConfirm = { vm.forgetDevice(); showForgetDialog = false }
            )
        }

        Spacer(Modifier.height(32.dp))
      }
      }
    }
}

@Composable
private fun StatusUtilityCard(
    leAudioText: String,
    batteryAlertThreshold: Int,
    onFindMy: () -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    SectionCard {
        // Find My AirPods
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFindMy() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Find My AirPods", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
        Divider()
        // LE Audio status
        InfoRow("Audio Codec", leAudioText)
        Divider()
        // Battery Alert Threshold
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Battery Alert", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text("$batteryAlertThreshold%", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        AppleSlider(
            value = batteryAlertThreshold.toFloat(),
            onValueChange = { onThresholdChange(((it / 5).roundToInt() * 5).coerceIn(5, 50)) },
            valueRange = 5f..50f,
            accent = AppleGreen,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StemActionDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val options = listOf("Noise Control", "Voice Assistant")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Press and Hold", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == option, onClick = { onSelect(option) })
                        Spacer(Modifier.width(6.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun DisconnectConfirmDialog(deviceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "AirBridge will stop managing $deviceName until you reconnect. Audio playback is unaffected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Disconnect", color = AppleRed)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ForgetConfirmDialog(deviceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget $deviceName?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                "This unpairs $deviceName from your phone. You'll need to re-pair them in Bluetooth settings to use them again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Forget", color = AppleRed)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun BatteryGauge(label: String, level: Int, isCharging: Boolean, isLoading: Boolean = false) {
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
            androidx.compose.foundation.Canvas(modifier = Modifier.size(72.dp)) {
                val stroke = 9f
                val ringRadius = size.minDimension / 2f - stroke
                // Recessed "well" — soft radial gradient gives the gauge an inset, neumorphic feel
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.22f)),
                        center = center, radius = size.minDimension / 2f
                    ),
                    radius = ringRadius
                )
                // Track
                drawArc(Color.White.copy(alpha = 0.08f), -90f, 360f, false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                if (displayLevel >= 0) {
                    // Soft glow under the progress arc — fakes a raised, lit ring
                    drawArc(color.copy(alpha = 0.30f), -90f, 360f * animatedLevel, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 2.4f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawArc(color, -90f, 360f * animatedLevel, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke,
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
                    text = if (isLoading) "Updating" else if (label == "Case") "Closed" else "—",
                    style = MaterialTheme.typography.labelSmall,
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

    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        modes.forEach { opt ->
            val isSelected = currentMode == opt.mode
            val contentColor by animateColorAsState(
                if (isSelected) Color.White
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                tween(220), label = "ancFg"
            )
            val pillShape = RoundedCornerShape(12.dp)
            // Selected segment is a raised glass pill; the rest sit flat in the inset track
            val segmentModifier = if (isSelected) {
                Modifier
                    .weight(1f)
                    .shadow(6.dp, pillShape)
                    .clip(pillShape)
                    .background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.82f))))
            } else {
                Modifier.weight(1f).clip(pillShape)
            }
            Box(
                modifier = segmentModifier
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onModeChange(opt.mode)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(opt.icon, opt.label, Modifier.size(20.dp), tint = contentColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = opt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
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

/**
 * Clean, Apple-style slider: a thin track in [accent] with a round white thumb and a
 * soft shadow. No tick marks or stop indicators — those are what made the stock
 * Material 3 slider look cluttered.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = accent,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        ),
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .shadow(3.dp, CircleShape, clip = false)
                    .background(Color.White, CircleShape)
            )
        }
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
fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))
}

@Composable
fun SettingToggle(title: String, description: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle(it)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { toggle(!enabled) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = toggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = AppleGreen,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
fun SettingInfo(label: String, value: String, dense: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (dense) 7.dp else 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label,
            style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
        Text(value,
            style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
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
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(Radius.card)
    if (hazeState != null) {
        // Frosted liquid-glass surface — blurs the screen backdrop behind it
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(hazeState, shape, glassStyle())
                .border(1.dp, glassBorder(), shape)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { content() }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    shape
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Connecting Screen (shown during AACP handshake)
// ═══════════════════════════════════════════════════════════════

@Composable
fun ConnectingScreen(vm: AirPodsViewModel, state: AacpTransport.ConnectionState, btProfileConnected: Boolean = false) {
    val deviceName by vm.bondedDeviceName.collectAsState()
    val error by vm.connectionError.collectAsState()
    val isConnected = state == AacpTransport.ConnectionState.CONNECTED

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); vm.clearConnectionError() }
    }

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
                state == AacpTransport.ConnectionState.HANDSHAKING ||
                state == AacpTransport.ConnectionState.RECONNECTING,
            isConnected = isConnected,
            modifier = Modifier.size(200.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = deviceName ?: "AirPods",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                state == AacpTransport.ConnectionState.CONNECTED && !btProfileConnected -> "Waiting for Bluetooth audio..."
                state == AacpTransport.ConnectionState.CONNECTING -> "Connecting..."
                state == AacpTransport.ConnectionState.HANDSHAKING -> "Setting up..."
                state == AacpTransport.ConnectionState.RECONNECTING -> "Reconnecting..."
                state == AacpTransport.ConnectionState.CONNECTED -> "Connected!"
                state == AacpTransport.ConnectionState.FAILED -> "Connection failed"
                else -> "Searching..."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                state == AacpTransport.ConnectionState.CONNECTED && !btProfileConnected -> AppleOrange
                state == AacpTransport.ConnectionState.CONNECTED -> AppleGreen
                state == AacpTransport.ConnectionState.FAILED -> AppleRed
                state == AacpTransport.ConnectionState.RECONNECTING -> AppleOrange
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            }
        )

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

    LaunchedEffect(error) {
        if (error != null) { kotlinx.coroutines.delay(4000); vm.clearConnectionError() }
    }

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

/** Tracks the Bluetooth adapter's on/off state, updating live on ACTION_STATE_CHANGED. */
@Composable
fun rememberBluetoothEnabled(): Boolean {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    enabled = adapter?.isEnabled == true
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

@Composable
fun BluetoothOffScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, null, Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text("Bluetooth is off", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Turn on Bluetooth to connect your AirPods.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                context.startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Turn On Bluetooth", style = MaterialTheme.typography.labelLarge, color = Color.White)
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
