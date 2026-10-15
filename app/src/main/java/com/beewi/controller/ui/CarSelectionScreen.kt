package com.beewi.controller.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beewi.controller.R
import com.beewi.controller.bluetooth.BluetoothPermissions
import com.beewi.controller.bluetooth.CarDevice
import com.beewi.controller.viewmodel.CarViewModel

/** Lets the user pick which Bluetooth device is the car, with likely cars listed first. */
@Composable
fun CarSelectionScreen(
    viewModel: CarViewModel = viewModel(),
    onCarSelected: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingScan by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPairedDevices()
        if (pendingScan && BluetoothPermissions.allGranted(context)) viewModel.startScan()
        pendingScan = false
    }

    // Load paired devices on entry, asking for permissions first if needed.
    LaunchedEffect(Unit) {
        if (BluetoothPermissions.allGranted(context)) {
            viewModel.refreshPairedDevices()
        } else {
            permissionLauncher.launch(BluetoothPermissions.required())
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    val onScanClick: () -> Unit = {
        when {
            uiState.isScanning -> viewModel.stopScan()
            BluetoothPermissions.allGranted(context) -> viewModel.startScan()
            else -> {
                pendingScan = true
                permissionLauncher.launch(BluetoothPermissions.required())
            }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.title_select_car),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.hint_select_car),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onScanClick) {
                    Text(
                        text = stringResource(
                            if (uiState.isScanning) R.string.button_stop_scan else R.string.button_scan,
                        ),
                    )
                }
                if (uiState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            uiState.scanError?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.status_no_devices),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.devices, key = { it.address }) { device ->
                    DeviceRow(
                        device = device,
                        selected = device.address == uiState.selectedAddress,
                        onClick = { viewModel.selectDevice(device.address) },
                    )
                }
            }

            Button(
                onClick = onCarSelected,
                enabled = uiState.selectedAddress != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.button_continue))
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CarDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                if (device.isLikelyCar) {
                    Text(
                        text = stringResource(R.string.label_likely_car),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (device.bonded) {
                    Text(
                        text = stringResource(R.string.label_paired),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
