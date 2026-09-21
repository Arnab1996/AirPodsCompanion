package me.arnabsaha.airpodscompanion.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Liquid-glass plumbing, kept in one file so all Haze API usage lives together.
 *
 * A screen draws a [GlassBackdrop] (the layer that gets blurred), provides its
 * [HazeState] through [LocalHazeState], and any surface below — e.g. SectionCard —
 * frosts that backdrop via [glassEffect].
 */

/** The active backdrop a glass surface should sample; null = no glass (opaque fallback). */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun rememberGlassState(): HazeState = rememberHazeState()

/**
 * Full-bleed backdrop: a deep vertical gradient with a few soft colored glows so the
 * frosted cards above it have something to refract. Marked as the haze source.
 */
@Composable
fun GlassBackdrop(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val base = if (darkTheme) listOf(GlassDarkTop, GlassDarkMid, GlassDarkBottom)
    else listOf(GlassLightTop, GlassLightMid, GlassLightBottom)
    // Kept low. These only need to give the frosted cards something to refract, and anything
    // stronger fights the status colours drawn on top of it.
    val blobAlpha = if (darkTheme) 0.22f else 0.18f

    Box(modifier.fillMaxSize().hazeSource(hazeState)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(base))
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                val center = Offset(size.width * cx, size.height * cy)
                val radius = size.minDimension * r
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = blobAlpha), Color.Transparent),
                        center = center, radius = radius
                    ),
                    radius = radius, center = center
                )
            }
            // Nothing sits in the top right corner: that is where the ear indicators live and a
            // coloured glow behind them muddies the green.
            blob(GlassBlobBlue, 0.12f, 0.18f, 0.70f)
            blob(GlassBlobPurple, 0.95f, 0.46f, 0.55f)
            blob(GlassBlobTeal, 0.5f, 0.96f, 0.80f)
        }
    }
}

/** Frosted material tuned to the current surface so it reads in both light and dark. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun glassStyle(): HazeStyle = HazeMaterials.regular(MaterialTheme.colorScheme.surface)

/** Subtle top-lit edge that sells the glass rim. */
@Composable
fun glassBorder(): Brush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))
)

/** Clip + frost a surface against [hazeState]'s backdrop. */
fun Modifier.glassEffect(hazeState: HazeState, shape: Shape, style: HazeStyle): Modifier =
    this.clip(shape).hazeEffect(state = hazeState, style = style)
