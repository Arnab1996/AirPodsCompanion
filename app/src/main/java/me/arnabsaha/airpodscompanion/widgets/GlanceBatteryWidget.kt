package me.arnabsaha.airpodscompanion.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class GlanceBatteryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
        val left = prefs.getInt("widget_left", -1)
        val right = prefs.getInt("widget_right", -1)
        val case = prefs.getInt("widget_case", -1)

        provideContent {
            GlanceTheme {
                BatteryWidgetContent(left, right, case)
            }
        }
    }

    @Composable
    private fun BatteryWidgetContent(left: Int, right: Int, case_: Int) {
        val darkBg = ColorProvider(Color(0xFF1C1C1E), Color(0xFF1C1C1E))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(darkBg)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatteryIndicator(
                    label = "L",
                    level = left,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                BatteryIndicator(
                    label = "R",
                    level = right,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                BatteryIndicator(
                    label = "Case",
                    level = case_,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }
        }
    }

    @Composable
    private fun BatteryIndicator(label: String, level: Int, modifier: GlanceModifier) {
        val bgColor = when {
            level < 0 -> ColorProvider(Color.Gray, Color.Gray)
            level <= 10 -> ColorProvider(Color(0xFFFF3B30), Color(0xFFFF3B30))
            level <= 20 -> ColorProvider(Color(0xFFFF9500), Color(0xFFFF9500))
            else -> ColorProvider(Color(0xFF34C759), Color(0xFF34C759))
        }

        val white = ColorProvider(Color.White, Color.White)
        val displayText = if (level < 0) "--" else "$level%"

        Column(
            modifier = modifier
                .background(bgColor)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = white,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = displayText,
                style = TextStyle(
                    color = white,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
