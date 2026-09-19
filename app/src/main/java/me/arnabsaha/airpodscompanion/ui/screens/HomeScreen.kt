package me.arnabsaha.airpodscompanion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.arnabsaha.airpodscompanion.ui.components.AppleSlider
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.ui.components.AncSegmentedControl
import me.arnabsaha.airpodscompanion.ui.components.BatteryGauge
import me.arnabsaha.airpodscompanion.ui.components.EarDot
import me.arnabsaha.airpodscompanion.ui.components.InfoRow
import me.arnabsaha.airpodscompanion.ui.components.NavRow
import me.arnabsaha.airpodscompanion.ui.components.RowDivider
import me.arnabsaha.airpodscompanion.ui.components.SectionCard
import me.arnabsaha.airpodscompanion.ui.components.SectionHeader
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleOrange
import me.arnabsaha.airpodscompanion.ui.theme.GlassBackdrop
import me.arnabsaha.airpodscompanion.ui.theme.LocalHazeState
import me.arnabsaha.airpodscompanion.ui.theme.Spacing
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha
import me.arnabsaha.airpodscompanion.ui.theme.rememberGlassState
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import kotlin.math.roundToInt

/**
 * The primary screen: who is connected, how much battery is left, noise control, and the few
 * utilities worth reaching in one tap. Everything else lives behind Settings.
 *
 * Each card reads the flows it needs itself, so a battery tick does not invalidate the ANC
 * control or the header. The scroll is only a safety net for short screens and large fonts.
 */
@Composable
fun HomeScreen(
    vm: AirPodsViewModel,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFindMy: () -> Unit
) {
    val hazeState = rememberGlassState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        GlassBackdrop(hazeState)
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(Spacing.l))
                HomeHeader(vm)

                Spacer(Modifier.height(Spacing.xxl))
                BatteryCard(vm)

                Spacer(Modifier.height(Spacing.m))
                NoiseControl(vm)

                Spacer(Modifier.height(Spacing.m))
                UtilitiesCard(vm, onOpenFindMy)

                Spacer(Modifier.height(Spacing.m))
                SectionCard {
                    NavRow("Settings", onClick = onOpenSettings, icon = Icons.Default.Settings)
                    RowDivider()
                    NavRow("About AirBridge", onClick = onOpenAbout, icon = Icons.Default.Info)
                }

                Spacer(Modifier.height(bottomInset + Spacing.xxl))
            }
        }
    }
}

@Composable
private fun HomeHeader(vm: AirPodsViewModel) {
    val deviceName by vm.bondedDeviceName.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceName ?: "AirPods Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            StatusLine(vm)
        }
        EarDots(vm)
    }
}

@Composable
private fun StatusLine(vm: AirPodsViewModel) {
    val connState by vm.connectionState.collectAsStateWithLifecycle()
    val connectionActivity by vm.connectionActivity.collectAsStateWithLifecycle()
    val isLive = connState == AacpTransport.ConnectionState.CONNECTED
    val statusColor = if (isLive) AppleGreen else AppleOrange
    val activity = when (connectionActivity) {
        5 -> " · Playing"
        6 -> " · On a call"
        else -> ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.width(6.dp))
        Text(
            if (isLive) "Connected$activity" else "Reconnecting…",
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
    }
}

@Composable
private fun EarDots(vm: AirPodsViewModel) {
    val earState by vm.earState.collectAsStateWithLifecycle()
    Row {
        EarDot("L", earState.leftInEar)
        Spacer(Modifier.width(12.dp))
        EarDot("R", earState.rightInEar)
    }
}

@Composable
private fun BatteryCard(vm: AirPodsViewModel) {
    val battery by vm.battery.collectAsStateWithLifecycle()
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BatteryGauge("Left", battery?.leftLevel ?: -1, battery?.leftCharging == true, isLoading = battery == null)
            BatteryGauge("Right", battery?.rightLevel ?: -1, battery?.rightCharging == true, isLoading = battery == null)
            BatteryGauge("Case", battery?.caseLevel ?: -1, battery?.caseCharging == true, isLoading = battery == null)
        }
    }
}

@Composable
private fun NoiseControl(vm: AirPodsViewModel) {
    val ancMode by vm.ancMode.collectAsStateWithLifecycle()
    val reachable by vm.isDeviceReachable.collectAsStateWithLifecycle()
    SectionHeader("Noise Control")
    AncSegmentedControl(
        currentMode = ancMode,
        onModeChange = { vm.setNoiseControlMode(it) },
        available = reachable
    )
}

@Composable
private fun UtilitiesCard(vm: AirPodsViewModel, onOpenFindMy: () -> Unit) {
    val leAudio by vm.leAudioCapability.collectAsStateWithLifecycle()
    SectionCard {
        NavRow("Find My AirPods", onClick = onOpenFindMy, icon = Icons.Default.Bluetooth)
        RowDivider()
        InfoRow("Audio Codec", leAudio?.displayText ?: "Checking…")
        RowDivider()
        BatteryAlertRow(vm)
    }
}

/** Own composable so dragging the slider does not recompose the rest of the utilities card. */
@Composable
private fun BatteryAlertRow(vm: AirPodsViewModel) {
    val threshold by vm.batteryAlertThreshold.collectAsStateWithLifecycle()
    // Drag locally and write the preference once on release. The old code wrote SharedPreferences
    // on every drag frame.
    var local by remember { mutableStateOf(threshold) }
    LaunchedEffect(threshold) { local = threshold }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Battery Alert",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "$local%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.value)
        )
    }
    AppleSlider(
        value = local.toFloat(),
        onValueChange = { local = ((it / 5).roundToInt() * 5).coerceIn(5, 50) },
        onValueChangeFinished = { vm.setBatteryAlertThreshold(local) },
        valueRange = 5f..50f,
        accent = AppleGreen,
        modifier = Modifier.fillMaxWidth()
    )
}
