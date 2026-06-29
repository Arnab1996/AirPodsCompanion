package me.arnabsaha.airpodscompanion.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import me.arnabsaha.airpodscompanion.MainActivity
import me.arnabsaha.airpodscompanion.ui.theme.AppleGray
import me.arnabsaha.airpodscompanion.ui.theme.AppleGreen
import me.arnabsaha.airpodscompanion.ui.theme.AppleOrange
import me.arnabsaha.airpodscompanion.ui.theme.AppleRed

class GlanceBatteryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
        val left = prefs.getInt("widget_left", -1)
        val right = prefs.getInt("widget_right", -1)
        val case = prefs.getInt("widget_case", -1)

        provideContent {
            GlanceTheme {
                BatteryWidgetContent(context, left, right, case)
            }
        }
    }

    @Composable
    private fun BatteryWidgetContent(context: Context, left: Int, right: Int, case_: Int) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(8.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryIndicator("L", left, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(4.dp))
            BatteryIndicator("R", right, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(4.dp))
            BatteryIndicator("Case", case_, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }

    @Composable
    private fun BatteryIndicator(label: String, level: Int, modifier: GlanceModifier) {
        // Color the percentage text (not the whole cell) so it stays legible in light + dark
        val levelColor = when {
            level < 0 -> AppleGray
            level <= 10 -> AppleRed
            level <= 20 -> AppleOrange
            else -> AppleGreen
        }
        val displayText = if (level < 0) "--" else "$level%"

        Column(
            modifier = modifier
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = displayText,
                style = TextStyle(
                    color = ColorProvider(levelColor),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
