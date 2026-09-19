package me.arnabsaha.airpodscompanion.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.ui.composables.ConnectionAnimation
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleOrange
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel

// ═══════════════════════════════════════════════════════════════
// Connecting Screen (shown during AACP handshake)
// ═══════════════════════════════════════════════════════════════

@Composable
fun ConnectingScreen(vm: AirPodsViewModel, state: AacpTransport.ConnectionState, btProfileConnected: Boolean = false) {
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
            isConnected = state == AacpTransport.ConnectionState.CONNECTED,
            modifier = Modifier.size(200.dp)
        )

        Spacer(Modifier.height(24.dp))

        ConnectingStatus(vm, state, btProfileConnected)

        ConnectingError(vm)
    }
}

// name plus status, merged so TalkBack reads them as one announcement
@Composable
private fun ConnectingStatus(vm: AirPodsViewModel, state: AacpTransport.ConnectionState, btProfileConnected: Boolean) {
    val deviceName by vm.bondedDeviceName.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = deviceName ?: "AirPods",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when {
                state == AacpTransport.ConnectionState.CONNECTED && !btProfileConnected -> "Waiting for Bluetooth audio…"
                state == AacpTransport.ConnectionState.CONNECTING -> "Connecting…"
                state == AacpTransport.ConnectionState.HANDSHAKING -> "Setting up…"
                state == AacpTransport.ConnectionState.RECONNECTING -> "Reconnecting…"
                state == AacpTransport.ConnectionState.CONNECTED -> "Connected!"
                state == AacpTransport.ConnectionState.FAILED -> "Connection failed"
                else -> "Searching…"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                state == AacpTransport.ConnectionState.CONNECTED && !btProfileConnected -> AppleOrange
                state == AacpTransport.ConnectionState.CONNECTED -> AppleGreen
                state == AacpTransport.ConnectionState.FAILED -> AppleRed
                state == AacpTransport.ConnectionState.RECONNECTING -> AppleOrange
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
private fun ConnectingError(vm: AirPodsViewModel) {
    val error by vm.connectionError.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        if (error != null) { delay(4000); vm.clearConnectionError() }
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

// ═══════════════════════════════════════════════════════════════
// Bluetooth off
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
// Permissions
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(perms: MultiplePermissionsState, canDrawOverlays: Boolean) {
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
        if (canDrawOverlays) {
            PermCard("Overlay", "Connection popup over other apps", Icons.Default.Layers, true)
        } else {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()))
            }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text("Grant Overlay", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "If the overlay toggle is greyed out (sideloaded build), open App info → ⋮ → Allow restricted settings first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermCard(title: String, desc: String, icon: ImageVector, granted: Boolean) {
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
