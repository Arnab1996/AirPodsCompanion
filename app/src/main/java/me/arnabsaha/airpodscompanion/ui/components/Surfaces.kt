package me.arnabsaha.airpodscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.arnabsaha.airpodscompanion.ui.theme.LocalHazeState
import me.arnabsaha.airpodscompanion.ui.theme.Radius
import me.arnabsaha.airpodscompanion.ui.theme.glassBorder
import me.arnabsaha.airpodscompanion.ui.theme.glassEffect
import me.arnabsaha.airpodscompanion.ui.theme.glassStyle

/**
 * Grouped container for a set of rows, in the style of an iOS settings group.
 *
 * Frosts the screen backdrop when a [LocalHazeState] is provided, otherwise falls back to an
 * opaque surface. Home provides one because it is short and mostly static. Long lists like Settings
 * leave it out so the list does not pay for a blur pass per card on every scroll frame.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val shape = RoundedCornerShape(Radius.card)
    if (hazeState != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .glassEffect(hazeState, shape, glassStyle())
                .border(1.dp, glassBorder(), shape)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), content = content)
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), content = content)
        }
    }
}

/** Uppercase group label above a [SectionCard], the way iOS labels a settings group. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        modifier = modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp)
    )
}

/** Hairline separator between rows inside a [SectionCard]. */
@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** Small filled pill, used for "Active" and similar one-word states. */
@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(Radius.chip))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
