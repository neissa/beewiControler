package com.beewi.controller.bluetooth

import android.content.Context

/** Remembers which Bluetooth device is the car between app launches. */
class CarPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("car_prefs", Context.MODE_PRIVATE)

    var selectedAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) {
            prefs.edit().putString(KEY_ADDRESS, value).apply()
        }

    private companion object {
        const val KEY_ADDRESS = "selected_car_address"
    }
}
