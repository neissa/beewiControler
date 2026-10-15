package com.beewi.controller.bluetooth

/**
 * Drive commands understood by the car.
 *
 * [pressByte] is repeatedly sent while a direction is held,
 * [releaseByte] is sent once when it is released.
 */
enum class CarCommand(val pressByte: Byte, val releaseByte: Byte) {
    FORWARD(pressByte = 1, releaseByte = 0),
    BACKWARD(pressByte = 3, releaseByte = 2),
    LEFT(pressByte = 5, releaseByte = 4),
    RIGHT(pressByte = 7, releaseByte = 6),
}
