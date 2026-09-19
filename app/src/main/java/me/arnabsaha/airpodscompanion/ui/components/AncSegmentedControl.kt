package me.arnabsaha.airpodscompanion.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.arnabsaha.airpodscompanion.protocol.constants.NoiseControlMode
import me.arnabsaha.airpodscompanion.ui.theme.TextAlpha

private data class AncOption(val label: String, val mode: Byte, val icon: ImageVector)

private val ANC_OPTIONS = listOf(
    AncOption("Off", NoiseControlMode.OFF, Icons.AutoMirrored.Filled.VolumeOff),
    AncOption("ANC", NoiseControlMode.NOISE_CANCELLATION, Icons.Default.HearingDisabled),
    AncOption("Transparent", NoiseControlMode.TRANSPARENCY, Icons.AutoMirrored.Filled.VolumeUp),
    AncOption("Adaptive", NoiseControlMode.ADAPTIVE, Icons.Default.GraphicEq)
)

/**
 * Four-way noise control picker.
 *
 * Each segment is a radio button as far as TalkBack is concerned, so the selected mode is
 * announced. When [available] is false the control dims and stops accepting taps rather than
 * quietly dropping the command on the floor.
 */
@Composable
fun AncSegmentedControl(
    currentMode: Byte,
    onModeChange: (Byte) -> Unit,
    available: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val pillShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (available) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ANC_OPTIONS.forEach { opt ->
            val isSelected = currentMode == opt.mode
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White
                else MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.secondary),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "ancFg"
            )
            // A short settle on the pill so switching modes feels physical rather than instant.
            val pillScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "ancPill"
            )

            val segment = if (isSelected) {
                Modifier
                    .weight(1f)
                    .scale(pillScale)
                    .shadow(6.dp, pillShape)
                    .clip(pillShape)
                    .background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.82f))))
            } else {
                Modifier.weight(1f).clip(pillShape)
            }

            Box(
                modifier = segment
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        enabled = available,
                        role = Role.RadioButton,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChange(opt.mode)
                        }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(opt.icon, null, Modifier.size(20.dp), tint = contentColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = opt.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
