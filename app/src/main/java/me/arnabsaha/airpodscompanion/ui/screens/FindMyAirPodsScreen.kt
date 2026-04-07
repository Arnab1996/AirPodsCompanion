package me.arnabsaha.airpodscompanion.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import kotlinx.coroutines.delay

/**
 * Find My AirPods screen — uses BLE RSSI signal strength to guide
 * the user toward their AirPods with visual bars and haptic feedback.
 */
@Composable
fun FindMyAirPodsScreen(vm: AirPodsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val nearestDevice by vm.nearestAirPods.collectAsState()

    val rssi = nearestDevice?.rssi ?: -100
    val proximity = ((rssi + 100) / 70.0 * 100).coerceIn(0.0, 100.0).toInt()

    // Start/stop BLE scan when entering/leaving this screen
    LaunchedEffect(Unit) { vm.startFindMyScan() }
    DisposableEffect(Unit) { onDispose { vm.stopFindMyScan() } }

    // Haptic feedback — pulses faster as proximity increases
    LaunchedEffect(proximity) {
        if (proximity > 10) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(VibratorManager::class.java)
                mgr.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            while (true) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                val interval = (2000 - proximity * 18L).coerceAtLeast(200L)
                delay(interval)
            }
        }
    }

    // Animated pulse for the proximity indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1500 - proximity * 10).coerceAtLeast(300)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val proximityColor = when {
        proximity > 70 -> Color(0xFF34C759)   // Green — very close
        proximity > 40 -> Color(0xFFFF9500)   // Orange — medium
        proximity > 15 -> Color(0xFFFF3B30)   // Red — far
        else -> Color(0xFF8E8E93)              // Gray — no signal
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                "Find My AirPods",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(40.dp))

        // Proximity percentage
        Text(
            text = "$proximity%",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = proximityColor.copy(alpha = pulseAlpha)
        )

        Text(
            text = when {
                proximity > 70 -> "Very Close!"
                proximity > 40 -> "Getting Closer"
                proximity > 15 -> "Keep Searching"
                else -> "No Signal"
            },
            style = MaterialTheme.typography.titleMedium,
            color = proximityColor
        )

        Spacer(Modifier.height(40.dp))

        // Signal strength bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 1..5) {
                val barThreshold = i * 20
                val isActive = proximity >= barThreshold
                val barHeight = (20 + i * 16).dp

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(barHeight)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isActive) proximityColor.copy(alpha = pulseAlpha)
                            else Color(0xFF3A3A3C)
                        )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // RSSI debug info
        Text(
            text = "RSSI: ${rssi}dBm",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8E8E93)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Walk around slowly.\nThe signal gets stronger as you get closer to your AirPods.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
    }
}
