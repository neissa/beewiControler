package com.beewi.controller.bluetooth

import android.bluetooth.BluetoothClass

/**
 * A Bluetooth device that may be the car.
 *
 * @property majorClass the Bluetooth major device class (see [BluetoothClass.Device.Major]),
 *   or [BluetoothClass.Device.Major.MISC]-like values when unknown.
 */
data class CarDevice(
    val name: String,
    val address: String,
    val bonded: Boolean,
    val majorClass: Int,
) {
    /** True when the device looks like a toy car. This is a heuristic, not a guarantee. */
    val isLikelyCar: Boolean
        get() = CarIdentifier.isLikelyCar(name, address, majorClass)
}

/** Heuristics used to flag devices that are probably the car. */
object CarIdentifier {

    /** Name fragments typical of BeeWi toys and car models. */
    private val NAME_PATTERN = Regex(
        "beewi|bbz|cooper|mini|\\bcar\\b|\\btoy\\b|\\brc\\b|voiture",
        RegexOption.IGNORE_CASE,
    )

    /** Manufacturer prefix (OUI) of the known car. Devices sharing it are likely the same brand. */
    private const val KNOWN_CAR_OUI = "00:13:EF"

    private const val MAJOR_TOY = BluetoothClass.Device.Major.TOY

    fun isLikelyCar(name: String, address: String, majorClass: Int): Boolean =
        NAME_PATTERN.containsMatchIn(name) ||
            majorClass == MAJOR_TOY ||
            address.uppercase().startsWith(KNOWN_CAR_OUI)
}

/** Keeps the list readable: likely cars first, then paired devices, then by name. */
fun List<CarDevice>.sortedForPicker(): List<CarDevice> =
    sortedWith(
        compareByDescending<CarDevice> { it.isLikelyCar }
            .thenByDescending { it.bonded }
            .thenBy { it.name.lowercase() },
    )

/** Adds or updates [device], keeping the paired flag once it has been set. */
fun List<CarDevice>.upsert(device: CarDevice): List<CarDevice> {
    val existing = firstOrNull { it.address == device.address } ?: return this + device
    return map {
        if (it.address == device.address) {
            device.copy(
                name = if (device.name.isNotBlank() && device.name != UNKNOWN_NAME) device.name else existing.name,
                bonded = existing.bonded || device.bonded,
            )
        } else {
            it
        }
    }
}

const val UNKNOWN_NAME = "Unnamed device"
