package me.arnabsaha.airpodscompanion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import me.arnabsaha.airpodscompanion.ble.transport.AacpTransport
import me.arnabsaha.airpodscompanion.ui.screens.AboutScreen
import me.arnabsaha.airpodscompanion.ui.screens.BluetoothOffScreen
import me.arnabsaha.airpodscompanion.ui.screens.ConnectingScreen
import me.arnabsaha.airpodscompanion.ui.screens.DevicePickerScreen
import me.arnabsaha.airpodscompanion.ui.screens.FindMyAirPodsScreen
import me.arnabsaha.airpodscompanion.ui.screens.HomeScreen
import me.arnabsaha.airpodscompanion.ui.screens.PermissionsScreen
import me.arnabsaha.airpodscompanion.ui.screens.SettingsScreen
import me.arnabsaha.airpodscompanion.ui.screens.rememberCanDrawOverlays
import me.arnabsaha.airpodscompanion.ui.theme.AirPodsCompanionTheme
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModel
import me.arnabsaha.airpodscompanion.viewmodel.AirPodsViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back press: close activity and remove from recents, service keeps running.
        // Compose BackHandlers register later on the same dispatcher, so an open sub-screen
        // still consumes back first.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAndRemoveTask()
            }
        })

        setContent {
            AirPodsCompanionTheme {
                val viewModel: AirPodsViewModel = viewModel(
                    factory = AirPodsViewModelFactory(application)
                )
                MainScreen(viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Top level router
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(vm: AirPodsViewModel) {
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

    if (!perms.allPermissionsGranted) {
        PermissionsScreen(perms, rememberCanDrawOverlays())
        return
    }

    LaunchedEffect(Unit) { vm.bindService() }
    DisposableEffect(Unit) { onDispose { vm.unbindService() } }

    val connState by vm.connectionState.collectAsStateWithLifecycle()
    val btProfileConnected by vm.isBluetoothProfileConnected.collectAsStateWithLifecycle()
    val gateEarState by vm.earState.collectAsStateWithLifecycle()
    val bothInCase = gateEarState.leftInCase && gateEarState.rightInCase

    // Show the dashboard only while the AirPods are actually in use. Stay through brief AACP
    // reconnects, but revert to the picker quickly once they're stowed (both in case), the
    // audio profile drops, or the link fails, so there is no long "Reconnecting" limbo.
    var showDashboard by remember { mutableStateOf(false) }
    LaunchedEffect(connState, btProfileConnected, bothInCase) {
        when {
            // Both buds back in the case → user is done; return to origin promptly.
            bothInCase -> {
                delay(600)
                showDashboard = false
            }
            connState == AacpTransport.ConnectionState.CONNECTED && btProfileConnected -> {
                if (!showDashboard) {
                    delay(400)
                    showDashboard = true
                }
            }
            connState == AacpTransport.ConnectionState.FAILED -> showDashboard = false
            // Audio profile gone (latch already expired) → real disconnect.
            !btProfileConnected -> {
                delay(800)
                showDashboard = false
            }
            connState == AacpTransport.ConnectionState.DISCONNECTED -> {
                delay(1200)
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
        // an actionable screen instead of staring at "Reconnecting".
        connState == AacpTransport.ConnectionState.CONNECTING ||
        connState == AacpTransport.ConnectionState.HANDSHAKING ||
        connState == AacpTransport.ConnectionState.CONNECTED -> "connecting"
        else -> "picker"
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
        label = "screen"
    ) { target ->
        when (target) {
            "bt_off" -> BluetoothOffScreen()
            "dashboard" -> ConnectedFlow(vm)
            "picker" -> DevicePickerScreen(vm)
            else -> ConnectingScreen(vm, connState, btProfileConnected)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Connected flow: Home with Settings, About and Find My pushed on top
// ═══════════════════════════════════════════════════════════════

private enum class Destination { Home, Settings, About, FindMy }

@Composable
private fun ConnectedFlow(vm: AirPodsViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    BackHandler(enabled = destination != Destination.Home) { destination = Destination.Home }

    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val slide = spring<IntOffset>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                // Without this the spring settles to 0.01px, so the transition stays "running"
                // for ~250ms after it looks finished and both screens keep composing.
                visibilityThreshold = IntOffset.VisibilityThreshold
            )
            val transform = if (targetState == Destination.Home) {
                // Popping back: the detail slides off to the right over Home. AnimatedContent draws
                // the incoming state last, so Home needs a negative z-index or it covers the slide.
                (slideInHorizontally(slide) { -it / 5 } + fadeIn(tween(160)) togetherWith
                    slideOutHorizontally(slide) { it } + fadeOut(tween(160)))
                    .apply { targetContentZIndex = -1f }
            } else {
                slideInHorizontally(slide) { it } + fadeIn(tween(160)) togetherWith
                    slideOutHorizontally(slide) { -it / 5 } + fadeOut(tween(160))
            }
            transform
        },
        label = "destination"
    ) { target ->
        when (target) {
            Destination.Home -> HomeScreen(
                vm = vm,
                onOpenSettings = { destination = Destination.Settings },
                onOpenAbout = { destination = Destination.About },
                onOpenFindMy = { destination = Destination.FindMy }
            )
            Destination.Settings -> SettingsScreen(vm) { destination = Destination.Home }
            Destination.About -> AboutScreen(vm) { destination = Destination.Home }
            Destination.FindMy -> FindMyAirPodsScreen(vm) { destination = Destination.Home }
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
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}
