package me.arnabsaha.airpodscompanion.ui.composables

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen

/**
 * Multi-stage connection animation:
 * 1. Case slides up + fades in
 * 2. Lid opens (rotation)
 * 3. Earbuds visible
 * 4. Pulsing blue rings (connecting)
 * 5. Green checkmark (connected)
 */
@Composable
fun ConnectionAnimation(
    isConnecting: Boolean,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    // Animation progress values
    val caseAlpha = remember { Animatable(0f) }
    val caseSlide = remember { Animatable(60f) }
    val lidAngle = remember { Animatable(0f) }
    val budsAlpha = remember { Animatable(0f) }
    val checkAlpha = remember { Animatable(0f) }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ringAlpha"
    )

    // Stage animations
    LaunchedEffect(isConnecting, isConnected) {
        if (isConnecting || isConnected) {
            // Stage 1: Case appears
            caseAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            caseSlide.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            delay(200)

            // Stage 2: Lid opens
            lidAngle.animateTo(-45f, tween(600, easing = FastOutSlowInEasing))
            delay(100)

            // Stage 3: Buds visible
            budsAlpha.animateTo(1f, tween(300))

            if (isConnected) {
                delay(300)
                // Stage 5: Checkmark
                checkAlpha.animateTo(1f, tween(400))
            }
        } else {
            // Reset
            caseAlpha.snapTo(0f)
            caseSlide.snapTo(60f)
            lidAngle.snapTo(0f)
            budsAlpha.snapTo(0f)
            checkAlpha.snapTo(0f)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val alpha = caseAlpha.value
                val slideY = caseSlide.value

                if (alpha <= 0f) return@Canvas

                // Pulsing rings (connecting state)
                if (isConnecting && checkAlpha.value < 0.5f) {
                    drawCircle(
                        color = primaryColor.copy(alpha = pulseAlpha * alpha),
                        radius = 80f * pulseRadius,
                        center = Offset(cx, cy + slideY)
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = pulseAlpha * 0.5f * alpha),
                        radius = 80f * pulseRadius * 1.3f,
                        center = Offset(cx, cy + slideY)
                    )
                }

                // Case body (rounded rectangle)
                val caseW = 120f
                val caseH = 90f
                val caseLeft = cx - caseW / 2
                val caseTop = cy - caseH / 2 + slideY + 20f

                // Case bottom half
                val casePath = Path().apply {
                    addRoundRect(RoundRect(
                        rect = Rect(caseLeft, caseTop, caseLeft + caseW, caseTop + caseH),
                        cornerRadius = CornerRadius(20f)
                    ))
                }
                drawPath(casePath, Color.White.copy(alpha = alpha * 0.95f))
                drawPath(casePath, surfaceColor.copy(alpha = alpha * 0.15f), style = Stroke(2f))

                // LED indicator on case
                drawCircle(
                    color = if (isConnected) AppleGreen.copy(alpha = alpha)
                            else primaryColor.copy(alpha = alpha * 0.6f),
                    radius = 4f,
                    center = Offset(cx, caseTop + caseH * 0.35f)
                )

                // Lid (rotates open)
                rotate(degrees = lidAngle.value, pivot = Offset(cx, caseTop)) {
                    val lidPath = Path().apply {
                        addRoundRect(RoundRect(
                            rect = Rect(caseLeft + 5f, caseTop - 45f, caseLeft + caseW - 5f, caseTop + 5f),
                            cornerRadius = CornerRadius(16f)
                        ))
                    }
                    drawPath(lidPath, Color(0xFFE8E8ED).copy(alpha = alpha * 0.9f))
                    drawPath(lidPath, surfaceColor.copy(alpha = alpha * 0.1f), style = Stroke(1.5f))
                }

                // Earbuds inside case
                if (budsAlpha.value > 0f) {
                    val budAlpha = budsAlpha.value * alpha
                    // Left bud
                    drawEarbud(cx - 22f, caseTop + 15f, budAlpha, surfaceColor, false)
                    // Right bud
                    drawEarbud(cx + 22f, caseTop + 15f, budAlpha, surfaceColor, true)
                }

                // Connected checkmark
                if (checkAlpha.value > 0f) {
                    val checkColor = AppleGreen.copy(alpha = checkAlpha.value)
                    drawCircle(checkColor.copy(alpha = checkAlpha.value * 0.15f),
                        radius = 36f, center = Offset(cx, cy + slideY - 30f))
                    drawCircle(checkColor, radius = 24f, center = Offset(cx, cy + slideY - 30f))

                    // Checkmark path
                    val checkPath = Path().apply {
                        moveTo(cx - 10f, cy + slideY - 30f)
                        lineTo(cx - 3f, cy + slideY - 23f)
                        lineTo(cx + 12f, cy + slideY - 40f)
                    }
                    drawPath(checkPath, Color.White.copy(alpha = checkAlpha.value),
                        style = Stroke(3.5f, cap = StrokeCap.Round))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                isConnected -> "Connected"
                isConnecting -> "Connecting..."
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isConnected) AppleGreen
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

private fun DrawScope.drawEarbud(x: Float, y: Float, alpha: Float, color: Color, mirrored: Boolean) {
    val dir = if (mirrored) -1f else 1f
    // Bud body
    drawCircle(Color.White.copy(alpha = alpha * 0.9f), radius = 12f, center = Offset(x, y))
    drawCircle(color.copy(alpha = alpha * 0.12f), radius = 12f, center = Offset(x, y))
    // Stem
    drawLine(
        Color.White.copy(alpha = alpha * 0.85f),
        start = Offset(x + dir * 2f, y + 10f),
        end = Offset(x + dir * 2f, y + 28f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    // Ear tip
    drawCircle(Color(0xFFD1D1D6).copy(alpha = alpha * 0.7f),
        radius = 6f, center = Offset(x - dir * 5f, y - 3f))
}
