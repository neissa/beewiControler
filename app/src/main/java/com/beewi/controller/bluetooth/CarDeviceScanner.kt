package com.beewi.controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Lists paired devices and runs classic Bluetooth discovery. Needs the Bluetooth permissions. */
class CarDeviceScanner(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Paired devices. Empty if Bluetooth is off, unsupported, or permission is missing. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<CarDevice> {
        val bluetooth = adapter ?: return emptyList()
        return try {
            bluetooth.bondedDevices.orEmpty().map { it.toCarDevice(bonded = true) }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Emits devices found by a discovery session. Completes when discovery finishes.
     * Cancelling the collector stops the discovery.
     */
    @SuppressLint("MissingPermission")
    fun discover(): Flow<CarDevice> = callbackFlow {
        val bluetooth = adapter
        if (bluetooth == null || !bluetooth.isEnabled) {
            close(IllegalStateException("Bluetooth is turned off"))
            return@callbackFlow
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.bluetoothDeviceExtra() ?: return
                        trySend(device.toCarDevice(bonded = false))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        try {
            if (!bluetooth.startDiscovery()) {
                close(IllegalStateException("Could not start Bluetooth discovery"))
            }
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            try {
                bluetooth.cancelDiscovery()
            } catch (_: SecurityException) {
                // Permission revoked meanwhile: nothing more to stop.
            }
            appContext.unregisterReceiver(receiver)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toCarDevice(bonded: Boolean): CarDevice = CarDevice(
        name = try {
            name?.takeIf { it.isNotBlank() } ?: UNKNOWN_NAME
        } catch (_: SecurityException) {
            UNKNOWN_NAME
        },
        address = address,
        bonded = bonded,
        majorClass = bluetoothClass?.majorDeviceClass ?: 0,
    )

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
