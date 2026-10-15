package com.beewi.controller.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the classic Bluetooth (SPP / RFCOMM) socket to the car.
 *
 * All blocking I/O runs on [Dispatchers.IO]. Every Bluetooth call checks permissions first
 * and any [SecurityException] or I/O error is surfaced as a failed [Result] / exception
 * instead of crashing the app.
 */
class BeewiConnection(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Serialises writes so the command loop and the release write never interleave. */
    private val writeMutex = Mutex()
    private val lock = Any()

    // All guarded by [lock].
    private var pendingSocket: BluetoothSocket? = null
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    val isConnected: Boolean
        get() = synchronized(lock) { socket?.isConnected == true && output != null }

    fun hasConnectPermission(): Boolean = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    /** Connects to the device at [address] and sends the init byte. */
    suspend fun connect(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            connectInternal(address)
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectInternal(address: String) {
        if (isConnected) return

        val bluetooth = adapter ?: throw IOException("Bluetooth is not supported on this device")
        if (!hasConnectPermission()) {
            throw SecurityException("BLUETOOTH_CONNECT permission is not granted")
        }
        if (!bluetooth.isEnabled) throw IOException("Bluetooth is turned off")

        // Discovery slows down RFCOMM connections. Needs BLUETOOTH_SCAN on API 31+.
        try {
            bluetooth.cancelDiscovery()
        } catch (e: SecurityException) {
            // Not fatal: we can still try to connect without cancelling discovery.
        }

        val device = bluetooth.getRemoteDevice(address)
        val newSocket = device.createRfcommSocketToServiceRecord(CarConfig.SPP_UUID)
        synchronized(lock) { pendingSocket = newSocket }

        try {
            newSocket.connect()
            val out = newSocket.outputStream
            out.write(byteArrayOf(CarConfig.INIT_BYTE))
            out.flush()
            delay(CarConfig.INIT_DELAY_MS)

            synchronized(lock) {
                // disconnect() clears pendingSocket: if it no longer points to us, we were cancelled.
                if (pendingSocket !== newSocket) throw IOException("Connection was cancelled")
                pendingSocket = null
                socket = newSocket
                output = out
            }
        } catch (e: Throwable) {
            synchronized(lock) { if (pendingSocket === newSocket) pendingSocket = null }
            closeQuietly(newSocket)
            throw e
        }
    }

    /** Writes one command byte. Throws [IOException] if not connected or the write fails. */
    suspend fun sendCommand(command: Byte) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val out = synchronized(lock) { output } ?: throw IOException("Not connected to the car")
                out.write(byteArrayOf(command))
                out.flush()
            }
        }
    }

    /** Closes the socket. Safe to call when already disconnected. */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) { closeAll() }
    }

    /** Non-suspending close, used when the ViewModel is cleared. */
    fun close() = closeAll()

    private fun closeAll() {
        val toClose = synchronized(lock) {
            val sockets = listOfNotNull(pendingSocket, socket)
            pendingSocket = null
            socket = null
            output = null
            sockets
        }
        toClose.forEach(::closeQuietly)
    }

    private fun closeQuietly(bluetoothSocket: BluetoothSocket) {
        try {
            bluetoothSocket.close()
        } catch (_: IOException) {
            // Already closed or broken: nothing to do.
        }
    }

    private fun hasPermission(permission: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
