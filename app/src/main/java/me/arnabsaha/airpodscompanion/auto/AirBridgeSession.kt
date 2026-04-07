package me.arnabsaha.airpodscompanion.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class AirBridgeSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = AirBridgeScreen(carContext)
}
