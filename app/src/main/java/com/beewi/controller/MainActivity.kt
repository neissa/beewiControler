package com.beewi.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.beewi.controller.ui.CarControlScreen
import com.beewi.controller.ui.CarSelectionScreen
import com.beewi.controller.ui.theme.BeewiControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeewiControllerTheme {
                // Start on the car picker; switch to the controls once a car is chosen.
                var choosingCar by remember { mutableStateOf(true) }
                if (choosingCar) {
                    CarSelectionScreen(onCarSelected = { choosingCar = false })
                } else {
                    CarControlScreen(onChangeCar = { choosingCar = true })
                }
            }
        }
    }
}
