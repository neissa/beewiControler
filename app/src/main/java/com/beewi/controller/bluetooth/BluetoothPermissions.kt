package com.beewi.controller.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime Bluetooth permissions needed by [BeewiConnection] and [CarDeviceScanner]. */
object BluetoothPermissions {

    /**
     * Permissions to request at runtime. Android 12+ uses the Bluetooth permissions.
     * Before Android 12, discovery needs a location permission.
     */
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean =
        required().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
}
