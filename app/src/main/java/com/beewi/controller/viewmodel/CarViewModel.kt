package com.beewi.controller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beewi.controller.bluetooth.BeewiConnection
import com.beewi.controller.bluetooth.CarCommand
import com.beewi.controller.bluetooth.CarConfig
import com.beewi.controller.bluetooth.CarDevice
import com.beewi.controller.bluetooth.CarDeviceScanner
import com.beewi.controller.bluetooth.CarPreferences
import com.beewi.controller.bluetooth.sortedForPicker
import com.beewi.controller.bluetooth.upsert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

data class CarUiState(
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    /** Drive commands currently held. Several can be active at once (e.g. forward + right). */
    val activeCommands: Set<CarCommand> = emptySet(),
    /** Paired and discovered devices, likely cars first. */
    val devices: List<CarDevice> = emptyList(),
    /** Address of the device chosen as the car, if any. */
    val selectedAddress: String? = null,
    val isScanning: Boolean = false,
    val scanError: String? = null,
)

class CarViewModel(application: Application) : AndroidViewModel(application) {

    private val connection = BeewiConnection(application)
    private val scanner = CarDeviceScanner(application)
    private val preferences = CarPreferences(application)

    private val _uiState = MutableStateFlow(
        CarUiState(selectedAddress = preferences.selectedAddress),
    )
    val uiState: StateFlow<CarUiState> = _uiState.asStateFlow()

    /**
     * Serialises every write to the car: the repeat loop, changes of held commands and the
     * stop bytes. This guarantees a stop byte is never overtaken by a press byte.
     */
    private val commandMutex = Mutex()

    private var connectJob: Job? = null

    /** Re-sends the press byte of every active command every [CarConfig.COMMAND_INTERVAL_MS]. */
    private var commandLoop: Job? = null

    private var scanJob: Job? = null

    init {
        refreshPairedDevices()
    }

    /** Reloads the paired devices. Call once the Bluetooth permissions are granted. */
    fun refreshPairedDevices() {
        val paired = scanner.pairedDevices()
        _uiState.update { state ->
            var devices = state.devices
            paired.forEach { devices = devices.upsert(it) }
            state.copy(devices = devices.sortedForPicker())
        }
    }

    /** Remembers [address] as the car to control. */
    fun selectDevice(address: String) {
        preferences.selectedAddress = address
        _uiState.update { it.copy(selectedAddress = address) }
    }

    /** Starts a discovery session that adds nearby devices to the list. */
    fun startScan() {
        if (scanJob?.isActive == true) return
        _uiState.update { it.copy(isScanning = true, scanError = null) }
        scanJob = viewModelScope.launch {
            try {
                scanner.discover().collect { found ->
                    _uiState.update { state ->
                        state.copy(devices = state.devices.upsert(found).sortedForPicker())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(scanError = "Scan failed (${e.message ?: e.javaClass.simpleName})")
                }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun connect() {
        val status = _uiState.value.status
        if (status == ConnectionStatus.Connected || status == ConnectionStatus.Connecting) return

        val address = _uiState.value.selectedAddress
        if (address == null) {
            setStatus(ConnectionStatus.Error("No car selected"))
            return
        }

        if (!connection.hasConnectPermission()) {
            setStatus(ConnectionStatus.Error("Bluetooth permission is required"))
            return
        }

        setStatus(ConnectionStatus.Connecting)
        connectJob = viewModelScope.launch {
            val result = connection.connect(address)
            if (!isActive) return@launch // disconnect() was called meanwhile
            result.fold(
                onSuccess = {
                    setStatus(ConnectionStatus.Connected)
                    startCommandLoop()
                },
                onFailure = { e ->
                    setStatus(
                        ConnectionStatus.Error(
                            "Could not connect to the car (${e.message ?: e.javaClass.simpleName})",
                        ),
                    )
                },
            )
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        viewModelScope.launch {
            applyCommands(emptySet())
            commandLoop?.cancel()
            commandLoop = null
            connection.disconnect()
            setStatus(ConnectionStatus.Disconnected)
        }
    }

    /**
     * Sets the drive commands that are currently held. Commands that are no longer in
     * [commands] get their stop byte; new ones are repeated by the command loop.
     */
    fun setCommands(commands: Set<CarCommand>) {
        if (_uiState.value.status != ConnectionStatus.Connected) return
        viewModelScope.launch { applyCommands(commands) }
    }

    /** Called when the app goes to the background: stop the car, keep the connection. */
    fun onAppStopped() {
        setCommands(emptySet())
    }

    private suspend fun applyCommands(commands: Set<CarCommand>) {
        commandMutex.withLock {
            val released = _uiState.value.activeCommands - commands
            _uiState.update { it.copy(activeCommands = commands) }
            try {
                released.forEach { connection.sendCommand(it.releaseByte) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLinkLost(e)
            }
        }
    }

    private fun startCommandLoop() {
        commandLoop?.cancel()
        commandLoop = viewModelScope.launch {
            while (isActive) {
                commandMutex.withLock {
                    try {
                        _uiState.value.activeCommands.forEach { connection.sendCommand(it.pressByte) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onLinkLost(e)
                        return@launch
                    }
                }
                delay(CarConfig.COMMAND_INTERVAL_MS)
            }
        }
    }

    private fun onLinkLost(e: Throwable) {
        _uiState.update { it.copy(activeCommands = emptySet()) }
        setStatus(
            ConnectionStatus.Error("Connection lost (${e.message ?: e.javaClass.simpleName})"),
        )
        viewModelScope.launch {
            commandLoop?.cancel()
            commandLoop = null
            connection.disconnect()
        }
    }

    private fun setStatus(status: ConnectionStatus) {
        _uiState.update { it.copy(status = status) }
    }

    override fun onCleared() {
        commandLoop?.cancel()
        connectJob?.cancel()
        scanJob?.cancel()
        connection.close()
        super.onCleared()
    }
}
