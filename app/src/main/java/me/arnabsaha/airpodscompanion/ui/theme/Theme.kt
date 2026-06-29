package me.arnabsaha.airpodscompanion.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// ── Layout tokens — single source of truth; plain objects, no CompositionLocal ──
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 32.dp
}

object Radius {
    val card = 18.dp
    val control = 12.dp
    val chip = 8.dp
}

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlueDark,
    onPrimary = DarkOnSurface,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
)

@Composable
fun AirPodsCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // No dynamic color — AirBridge always uses Apple blue branding
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            val barColor = colorScheme.background.toArgb()
            activity.enableEdgeToEdge(
                statusBarStyle = if (darkTheme) SystemBarStyle.dark(barColor)
                    else SystemBarStyle.light(barColor, barColor),
                navigationBarStyle = if (darkTheme) SystemBarStyle.dark(barColor)
                    else SystemBarStyle.light(barColor, barColor)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AirBridgeTypography,
        content = content
    )
}
