package com.beewi.controller.bluetooth

import java.util.UUID

/**
 * Connection constants for the BeeWi "Mini Cooper" car (classic Bluetooth, SPP / RFCOMM).
 */
object CarConfig {
    /** Standard Serial Port Profile UUID used for RFCOMM. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Byte sent right after connecting (0xFF). */
    const val INIT_BYTE: Byte = -1

    /** Delay after the init byte before the car accepts commands. */
    const val INIT_DELAY_MS = 200L

    /** Interval between repeated command bytes while a button is held. */
    const val COMMAND_INTERVAL_MS = 10L
}
