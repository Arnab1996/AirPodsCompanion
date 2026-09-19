package me.arnabsaha.airpodscompanion.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.arnabsaha.airpodscompanion.R
import me.arnabsaha.airpodscompanion.ble.scanner.AirPodsAdvertisement
import me.arnabsaha.airpodscompanion.ui.components.SectionCard
import me.arnabsaha.airpodscompanion.ui.components.StatusChip
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed
import me.arnabsaha.airpodscompanion.ui.theme.Radius
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import kotlinx.coroutines.delay

/**
 * Shown while disconnected. Lists the bonded AirPods to pick from, plus whatever the
 * scanner currently sees nearby. No glass backdrop here, the screen is a plain Column.
 */
@SuppressLint("MissingPermission")
@Composable
fun DevicePickerScreen(vm: AirPodsViewModel) {
    val bondedDevices by vm.bondedAirPodsList.collectAsStateWithLifecycle()
    val error by vm.connectionError.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        if (error != null) { delay(4000); vm.clearConnectionError() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
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
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = TextAlpha.secondary))

        Spacer(Modifier.height(32.dp))

        if (bondedDevices.isEmpty()) {
            // nothing paired yet, point the user at Android's Bluetooth settings
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.airpods_case),
                        contentDescription = "AirPods",
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Pair your AirPods first",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = TextAlpha.value))
                    Spacer(Modifier.height(8.dp))
                    Text("Go to Android Settings → Bluetooth\nHold the case button until LED flashes white",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = TextAlpha.secondary),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { vm.autoConnect() },
                        shape = RoundedCornerShape(Radius.control)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        } else {
            Text("Your AirPods", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = TextAlpha.secondary),
                modifier = Modifier.padding(bottom = 10.dp))

            bondedDevices.forEach { airpods ->
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { vm.connectToDevice(airpods.device) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(Radius.control))
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
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.decorative))
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

            NearbySection(vm)

            Spacer(Modifier.weight(1f))

            if (error != null) {
                SectionCard {
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                        color = AppleRed, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = { vm.autoConnect() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(Radius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Connect", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * AirPods and Beats seen over BLE, passive, no connection needed. Kept separate so the
 * advert stream only recomposes this part of the screen.
 */
@Composable
private fun NearbySection(vm: AirPodsViewModel) {
    val nearbyDevices by vm.nearbyDevices.collectAsStateWithLifecycle()
    if (nearbyDevices.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Text("Nearby", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = TextAlpha.secondary),
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
        nearbyDevices.take(4).forEach { dev ->
            NearbyDeviceCard(dev)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NearbyDeviceCard(dev: AirPodsAdvertisement) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.HeadsetMic, null, Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dev.modelName, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                val parts = buildList {
                    when (dev.connectionState) {
                        5 -> add("Playing")
                        6 -> add("On a call")
                    }
                    if (dev.leftBattery >= 0) add("L ${dev.leftBattery}%")
                    if (dev.rightBattery >= 0) add("R ${dev.rightBattery}%")
                    if (dev.caseBattery >= 0) add("Case ${dev.caseBattery}%")
                }
                Text(
                    if (parts.isEmpty()) "No battery yet" else parts.joinToString("   "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary)
                )
            }
            Text("${dev.rssi} dBm", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.decorative))
        }
    }
}
