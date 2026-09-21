package me.arnabsaha.airpodscompanion.ui.screens

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether AirBridge may draw the connection popup over other apps.
 *
 * Re-reads the grant every time the activity resumes. Reading it once during composition, which is
 * what the old Device card did, left the row saying "Tap to enable" after the user had just
 * enabled it and come back from the system screen.
 */
@Composable
fun rememberCanDrawOverlays(): Boolean = rememberCanDrawOverlaysState().value

@Composable
private fun rememberCanDrawOverlaysState(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val granted = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted.value = Settings.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}
