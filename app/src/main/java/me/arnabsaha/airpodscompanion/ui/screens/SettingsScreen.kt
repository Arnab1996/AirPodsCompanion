package me.arnabsaha.airpodscompanion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import me.arnabsaha.airpodscompanion.ui.components.AppleSlider
import me.arnabsaha.airpodscompanion.ui.components.InfoRow
import me.arnabsaha.airpodscompanion.ui.components.NavRow
import me.arnabsaha.airpodscompanion.ui.components.RowDivider
import me.arnabsaha.airpodscompanion.ui.components.SectionCard
import me.arnabsaha.airpodscompanion.ui.components.SectionHeader
import me.arnabsaha.airpodscompanion.ui.components.SettingToggle
import me.arnabsaha.airpodscompanion.ui.components.StemActionDialog
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.Spacing
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import kotlin.math.roundToInt

/**
 * Everything that used to live below the noise control on the dashboard, grouped the way iOS
 * groups settings. Each group collects only its own flows so a battery tick does not invalidate
 * the list, and no haze state is provided here so the cards stay opaque while scrolling.
 */
@Composable
fun SettingsScreen(vm: AirPodsViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Spacer(Modifier.height(Spacing.s))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.s,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            item { AudioGroup(vm) }
            item { ControlsGroup(vm) }
            item { HeadGesturesGroup(vm) }
            item { BackgroundGroup(vm) }
        }
    }
}

@Composable
private fun AudioGroup(vm: AirPodsViewModel) {
    val reachable by vm.isDeviceReachable.collectAsStateWithLifecycle()
    val avEnabled by vm.avEnabled.collectAsStateWithLifecycle()
    val caEnabled by vm.caEnabled.collectAsStateWithLifecycle()
    val edEnabled by vm.edEnabled.collectAsStateWithLifecycle()
    val sleepDetection by vm.sleepDetection.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Audio")
        SectionCard {
            SettingToggle(
                "Personalized Volume", "Adjusts volume in response to your environment",
                enabled = avEnabled, onToggle = { vm.setAdaptiveVolume(it) }, available = reachable
            )
            RowDivider()
            SettingToggle(
                "Conversation Awareness",
                "Lowers media volume and reduces background noise when you start speaking",
                enabled = caEnabled, onToggle = { vm.setConversationalAwareness(it) }, available = reachable
            )
            RowDivider()
            ChimeVolumeRow(vm, available = reachable)
            RowDivider()
            SettingToggle(
                "Automatic Ear Detection", "Auto play/pause when earbuds are removed",
                enabled = edEnabled, onToggle = { vm.setEarDetection(it) }, available = reachable
            )
            RowDivider()
            SettingToggle(
                "Pause When Falling Asleep", "Automatically pause media when sleep is detected",
                enabled = sleepDetection, onToggle = { vm.setSleepDetection(it) }, available = reachable
            )
        }
    }
}

/** The slider keeps its own value while dragging and only commits on release, as before. */
@Composable
private fun ChimeVolumeRow(vm: AirPodsViewModel, available: Boolean) {
    val chimeVolume by vm.chimeVolume.collectAsStateWithLifecycle()
    var localChimeVolume by remember { mutableStateOf(chimeVolume) }
    LaunchedEffect(chimeVolume) { localChimeVolume = chimeVolume }
    val haptic = LocalHapticFeedback.current
    val contentAlpha = if (available) 1f else 0.4f

    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Chime Volume",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                "${localChimeVolume.roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.value * contentAlpha)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeOff, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.decorative * contentAlpha)
            )
            AppleSlider(
                value = localChimeVolume,
                onValueChange = { localChimeVolume = it },
                onValueChangeFinished = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.setChimeVolume(localChimeVolume)
                    vm.previewChime()
                },
                valueRange = 0f..100f,
                accent = MaterialTheme.colorScheme.primary,
                enabled = available,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.m)
            )
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.decorative * contentAlpha)
            )
        }
    }
}

@Composable
private fun ControlsGroup(vm: AirPodsViewModel) {
    val reachable by vm.isDeviceReachable.collectAsStateWithLifecycle()
    val oneBudAnc by vm.oneBudAnc.collectAsStateWithLifecycle()
    val volumeSwipe by vm.volumeSwipe.collectAsStateWithLifecycle()
    val allowOff by vm.allowOff.collectAsStateWithLifecycle()
    val inCaseTone by vm.inCaseTone.collectAsStateWithLifecycle()
    val stemAction by vm.stemAction.collectAsStateWithLifecycle()
    var showStemDialog by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Controls")
        SectionCard {
            SettingToggle(
                "One Bud ANC", "Keep noise cancellation active with a single earbud",
                enabled = oneBudAnc, onToggle = { vm.setOneBudAnc(it) }, available = reachable
            )
            RowDivider()
            SettingToggle(
                "Volume Swipe", "Swipe the stem to adjust volume",
                enabled = volumeSwipe, onToggle = { vm.setVolumeSwipe(it) }, available = reachable
            )
            RowDivider()
            SettingToggle(
                "Off Listening Mode",
                "When on, listening modes will include an Off option. Loud sounds are not reduced in Off mode.",
                enabled = allowOff, onToggle = { vm.setAllowOff(it) }, available = reachable
            )
            RowDivider()
            SettingToggle(
                "Enable Charging Case Sounds", "Play a sound when placing buds in the case",
                enabled = inCaseTone, onToggle = { vm.setInCaseTone(it) }, available = reachable
            )
            RowDivider()
            NavRow(
                "Press and Hold",
                onClick = { showStemDialog = true },
                value = stemAction,
                available = reachable
            )
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

@Composable
private fun HeadGesturesGroup(vm: AirPodsViewModel) {
    val reachable by vm.isDeviceReachable.collectAsStateWithLifecycle()
    val headTrackingOn by vm.headTracking.collectAsStateWithLifecycle()
    val headTrackingLoading by vm.headTrackingLoading.collectAsStateWithLifecycle()
    val headGesture by vm.headGesture.collectAsStateWithLifecycle()

    var gestureFlash by remember { mutableStateOf<String?>(null) }
    // The gesture flow keeps its last value, so seed the guard with whatever is already there.
    // Without this the flash replays every time this group is composed again.
    var lastShown by remember { mutableStateOf(headGesture?.first ?: 0L) }
    LaunchedEffect(headGesture) {
        val gesture = headGesture ?: return@LaunchedEffect
        if (gesture.first == lastShown) return@LaunchedEffect
        lastShown = gesture.first
        gestureFlash = when (gesture.second) {
            "nod" -> "Nodded, Yes ✓"
            "shake" -> "Shook, No ✗"
            else -> null
        } ?: return@LaunchedEffect
        delay(2500)
        gestureFlash = null
    }

    Column {
        SectionHeader("Head Gestures")
        SectionCard {
            SettingToggle(
                "Head Gestures",
                if (headTrackingLoading) "Starting in a moment…"
                else "Move your head to answer or decline calls. Keep both AirPods in your ears.",
                enabled = headTrackingOn, onToggle = { vm.toggleHeadTracking() }, available = reachable
            )
            if (headTrackingLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Warming up, battery & ear detection first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary)
                    )
                }
            }
            if (headTrackingOn && !headTrackingLoading) {
                val statusColor = if (gestureFlash != null) MaterialTheme.colorScheme.primary else AppleGreen
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        gestureFlash ?: "Listening, nod to accept, shake to decline",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
            RowDivider()
            InfoRow("Accept, Reply", "Up and Down")
            RowDivider()
            InfoRow("Decline, Dismiss", "Side to Side")
        }
    }
}

@Composable
private fun BackgroundGroup(vm: AirPodsViewModel) {
    val autoResume by vm.autoResume.collectAsStateWithLifecycle()
    val backgroundScan by vm.backgroundScan.collectAsStateWithLifecycle()
    val runInBackground by vm.runInBackground.collectAsStateWithLifecycle()

    Column {
        SectionHeader("Background")
        SectionCard {
            SettingToggle(
                "Resume Music on Connect", "Start playback automatically when your AirPods connect",
                enabled = autoResume, onToggle = { vm.setAutoResume(it) }
            )
            RowDivider()
            SettingToggle(
                "Background Battery Updates",
                "Keep a low-power scan while connected for case battery and case-open alerts",
                enabled = backgroundScan, onToggle = { vm.setBackgroundScan(it) }
            )
            RowDivider()
            SettingToggle(
                "Run in Background",
                "Keep battery, ANC, ear detection and popups working when AirBridge is closed. Turn off to fully stop when you swipe the app away (leaves \"Active apps\").",
                enabled = runInBackground, onToggle = { vm.setRunInBackground(it) }
            )
        }
    }
}
