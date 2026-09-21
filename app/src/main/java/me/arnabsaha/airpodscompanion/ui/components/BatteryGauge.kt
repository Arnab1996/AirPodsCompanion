package me.arnabsaha.airpodscompanion.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleOrange
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha

/**
 * Circular battery gauge for one component (left, right, case).
 *
 * The animated level is kept as a State and read inside onDrawBehind rather than in the composable
 * body, so an animating ring invalidates only the draw phase. The recessed-well gradient is then
 * genuinely built once per size instead of once per frame.
 */
@Composable
fun BatteryGauge(
    label: String,
    level: Int,
    isCharging: Boolean,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = when {
        level < 0 -> Color.Gray.copy(alpha = 0.3f)
        level <= 10 -> AppleRed
        level <= 20 -> AppleOrange
        else -> AppleGreen
    }
    val animatedLevel = animateFloatAsState(
        targetValue = if (level >= 0) level / 100f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "battery"
    )

    val dark = isSystemInDarkTheme()
    val wellHighlight = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val wellShadow = if (dark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.08f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    // The AirPods report a component as DISCONNECTED when a bud is off or out of range, and the
    // service turns that into -1. Distinct from still waiting for the first packet.
    val unavailable = level < 0 && !isLoading

    val state = when {
        level >= 0 && isCharging -> "$level percent, charging"
        level >= 0 -> "$level percent"
        isLoading -> "updating"
        else -> "unavailable"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label battery: $state"
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(58.dp).alpha(if (unavailable) 0.4f else 1f)
        ) {
            Spacer(
                Modifier
                    .size(52.dp)
                    .drawWithCache {
                        val stroke = 7f
                        val ringRadius = size.minDimension / 2f - stroke
                        val well = Brush.radialGradient(
                            listOf(wellHighlight, wellShadow),
                            center = size.center,
                            radius = size.minDimension / 2f
                        )
                        onDrawBehind {
                            drawCircle(brush = well, radius = ringRadius)
                            drawArc(trackColor, -90f, 360f, false, style = Stroke(stroke))
                            val sweep = 360f * animatedLevel.value
                            if (sweep > 0f) {
                                drawArc(
                                    color.copy(alpha = 0.20f), -90f, sweep, false,
                                    style = Stroke(stroke * 1.7f, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color, -90f, sweep, false,
                                    style = Stroke(stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
            )
            if (level >= 0) {
                Text(
                    text = "$level%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = if (isLoading) "Updating" else if (label == "Case") "Closed" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary)
        )
        Text(
            text = if (isCharging && level >= 0) "Charging" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = AppleGreen
        )
    }
}

/** Small L / R indicator showing whether a bud is in the ear. */
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
                .size(9.dp)
                .clip(CircleShape)
                .background(if (inEar) AppleGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.value)
        )
    }
}
