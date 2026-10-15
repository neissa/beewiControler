package com.beewi.controller.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beewi.controller.R
import com.beewi.controller.bluetooth.BluetoothPermissions
import com.beewi.controller.bluetooth.CarCommand
import com.beewi.controller.viewmodel.CarViewModel
import com.beewi.controller.viewmodel.ConnectionStatus
import kotlin.math.roundToInt

private val STICK_SIZE: Dp = 140.dp

/** Fraction of the joystick radius taken by the knob. */
private const val KNOB_RATIO = 0.4f

/** Fraction of the knob travel that is ignored around the centre. */
private const val DEAD_ZONE_RATIO = 0.3f

private enum class StickAxis { VERTICAL, HORIZONTAL }

@Composable
fun CarControlScreen(
    viewModel: CarViewModel = viewModel(),
    onChangeCar: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Throttle: -1 = forward (stick up), 1 = backward (stick down), 0 = neutral.
    var throttleSign by remember { mutableIntStateOf(0) }
    // Steering: -1 = left, 1 = right, 0 = straight.
    var steeringSign by remember { mutableIntStateOf(0) }

    // Stop the car as soon as the app leaves the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        throttleSign = 0
        steeringSign = 0
        viewModel.onAppStopped()
    }

    LaunchedEffect(throttleSign, steeringSign) {
        viewModel.setCommands(driveCommands(throttleSign, steeringSign))
    }

    // The ViewModel checks the permission again, so a denied request simply shows an error.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.connect()
    }

    val onConnectClick: () -> Unit = {
        if (BluetoothPermissions.allGranted(context)) {
            viewModel.connect()
        } else {
            permissionLauncher.launch(BluetoothPermissions.required())
        }
    }

    val connected = uiState.status == ConnectionStatus.Connected

    Scaffold { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            val isLandscape = maxWidth > maxHeight
            val statusPanel: @Composable () -> Unit = {
                ConnectionPanel(
                    status = uiState.status,
                    onConnect = onConnectClick,
                    onDisconnect = viewModel::disconnect,
                    onChangeCar = onChangeCar,
                )
            }
            val throttleStick: @Composable () -> Unit = {
                StickColumn(
                    label = stringResource(R.string.label_throttle),
                    axis = StickAxis.VERTICAL,
                    enabled = connected,
                    onSignChange = { throttleSign = it },
                )
            }
            val steeringStick: @Composable () -> Unit = {
                StickColumn(
                    label = stringResource(R.string.label_steering),
                    axis = StickAxis.HORIZONTAL,
                    enabled = connected,
                    onSignChange = { steeringSign = it },
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    throttleStick()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        statusPanel()
                    }
                    steeringStick()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    statusPanel()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        throttleStick()
                        steeringStick()
                    }
                }
            }
        }
    }
}

/** Maps the two stick signs to the set of commands to hold. */
private fun driveCommands(throttle: Int, steering: Int): Set<CarCommand> = buildSet {
    when (throttle) {
        -1 -> add(CarCommand.FORWARD)
        1 -> add(CarCommand.BACKWARD)
    }
    when (steering) {
        -1 -> add(CarCommand.LEFT)
        1 -> add(CarCommand.RIGHT)
    }
}

@Composable
private fun ConnectionPanel(
    status: ConnectionStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChangeCar: () -> Unit,
) {
    val statusText = when (status) {
        ConnectionStatus.Disconnected -> stringResource(R.string.status_disconnected)
        ConnectionStatus.Connecting -> stringResource(R.string.status_connecting)
        ConnectionStatus.Connected -> stringResource(R.string.status_connected)
        is ConnectionStatus.Error -> stringResource(R.string.status_error, status.message)
    }
    val isError = status is ConnectionStatus.Error
    val isConnected = status == ConnectionStatus.Connected

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = if (isConnected) onDisconnect else onConnect,
            enabled = status != ConnectionStatus.Connecting,
        ) {
            Text(
                text = if (isConnected) {
                    stringResource(R.string.button_disconnect)
                } else {
                    stringResource(R.string.button_connect)
                },
            )
        }
        if (!isConnected && status != ConnectionStatus.Connecting) {
            TextButton(onClick = onChangeCar) {
                Text(text = stringResource(R.string.button_change_car))
            }
        }
    }
}

@Composable
private fun StickColumn(
    label: String,
    axis: StickAxis,
    enabled: Boolean,
    onSignChange: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Joystick(axis = axis, enabled = enabled, size = STICK_SIZE, onSignChange = onSignChange)
    }
}

/**
 * A joystick whose knob only moves along [axis]. It reports -1 / 0 / 1 through
 * [onSignChange] whenever the knob leaves or re-enters the dead zone. Releasing the knob
 * snaps it back to the centre and reports 0.
 */
@Composable
private fun Joystick(
    axis: StickAxis,
    enabled: Boolean,
    size: Dp,
    onSignChange: (Int) -> Unit,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSignChange by rememberUpdatedState(onSignChange)

    var areaPx by remember { mutableStateOf(IntSize.Zero) }
    var travel by remember { mutableFloatStateOf(0f) }
    var sign by remember { mutableIntStateOf(0) }

    // Largest distance the knob centre can move from the middle, in pixels.
    fun maxTravel(): Float = minOf(areaPx.width, areaPx.height) / 2f * (1f - KNOB_RATIO)

    fun applyTravel(newTravel: Float) {
        val max = maxTravel()
        travel = newTravel.coerceIn(-max, max)
        val threshold = max * DEAD_ZONE_RATIO
        val newSign = when {
            !currentEnabled -> 0
            travel > threshold -> 1
            travel < -threshold -> -1
            else -> 0
        }
        if (newSign != sign) {
            sign = newSign
            currentOnSignChange(newSign)
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) applyTravel(0f)
    }

    val axisOf: (Offset) -> Float = { if (axis == StickAxis.VERTICAL) it.y else it.x }

    Box(
        modifier = Modifier
            .size(size)
            .onSizeChanged { areaPx = it }
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .pointerInput(axis) {
                detectDragGestures(
                    onDragStart = { position ->
                        if (currentEnabled) {
                            val centre = Offset(areaPx.width / 2f, areaPx.height / 2f)
                            applyTravel(axisOf(position - centre))
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (currentEnabled) applyTravel(travel + axisOf(drag))
                    },
                    onDragEnd = { applyTravel(0f) },
                    onDragCancel = { applyTravel(0f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val knobColor = when {
            !enabled -> MaterialTheme.colorScheme.outlineVariant
            sign != 0 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondaryContainer
        }
        Box(
            modifier = Modifier
                .size(size * KNOB_RATIO)
                .offset {
                    val t = travel.roundToInt()
                    if (axis == StickAxis.VERTICAL) IntOffset(0, t) else IntOffset(t, 0)
                }
                .background(knobColor, CircleShape),
        )
    }
}
