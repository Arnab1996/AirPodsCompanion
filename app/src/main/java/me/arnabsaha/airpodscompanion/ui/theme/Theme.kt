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

// Apple system colors — consistent across light/dark
val AppleBlue = Color(0xFF007AFF)
val AppleBlueDark = Color(0xFF0A84FF)
val AppleGreen = Color(0xFF34C759)
val AppleRed = Color(0xFFFF3B30)
val AppleOrange = Color(0xFFFF9500)

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
