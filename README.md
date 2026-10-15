# beewiControler

## Usage

Android app (Kotlin, Jetpack Compose, Material 3) that drives a BeeWi "Mini Cooper" remote-control car over classic Bluetooth (SPP / RFCOMM).

1. Pair the car with your phone in Android's Bluetooth settings and power it on.
2. If your car's MAC address differs from the default, edit `CAR_MAC_ADDRESS` in `app/src/main/java/com/beewi/controller/bluetooth/CarConfig.kt`.
3. Install and launch the app, grant the Bluetooth permission when asked, then tap **Connect**.
4. Hold the arrow buttons to drive; release to stop. The car also stops when the app goes to the background.
