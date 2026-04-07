package me.arnabsaha.airpodscompanion.auto

import android.content.Context
import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import me.arnabsaha.airpodscompanion.service.AirPodsService

/**
 * Android Auto screen showing AirPods battery status and ANC toggle.
 * Reads battery from SharedPreferences (written by AirPodsService on each battery packet).
 */
class AirBridgeScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val prefs = carContext.getSharedPreferences("airbridge_settings", Context.MODE_PRIVATE)
        val left = prefs.getInt("widget_left", -1)
        val right = prefs.getInt("widget_right", -1)
        val case_ = prefs.getInt("widget_case", -1)

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Left")
                    .addText(if (left >= 0) "$left%" else "--")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Right")
                    .addText(if (right >= 0) "$right%" else "--")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Case")
                    .addText(if (case_ >= 0) "$case_%" else "--")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Cycle ANC")
                    .setOnClickListener {
                        val intent = Intent(carContext, AirPodsService::class.java)
                            .setAction(AirPodsService.ACTION_CYCLE_ANC)
                        carContext.startService(intent)
                        invalidate()
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("AirBridge")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
