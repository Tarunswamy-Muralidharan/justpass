package com.justpass.app.ui.components.water

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Reports the device's gravity vector as (x, y) normalised to [-1, 1].
 *
 *  x > 0  → phone tilted right (right edge lower)
 *  x < 0  → phone tilted left
 *  y > 0  → phone tilted forward (top edge lower — landscape)
 *
 * Uses TYPE_GRAVITY when available (smoothed by the OS), falls back to
 * TYPE_ACCELEROMETER on devices that don't expose gravity (rare, mostly
 * pre-2014 hardware). Auto-registers / unregisters with the composition
 * lifecycle so background battery cost is zero when off-screen.
 *
 * No runtime permission required — gravity / accelerometer are always
 * accessible without the user granting anything.
 */
@Composable
fun rememberGravity(): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(0f to 0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = if (sensor != null && sensorManager != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Gravity vector magnitude is ~9.81; divide to get -1..1
                    // for portrait, x is left-right tilt + y is forward-back.
                    val x = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    state.value = x to y
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }.also {
                // SENSOR_DELAY_UI = ~16ms cadence. Higher than UI need but cheap.
                sensorManager.registerListener(it, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        } else null

        onDispose {
            if (listener != null && sensorManager != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }
    return state
}

/**
 * Reports the device's linear acceleration as (x, y, z) in m/s² with gravity
 * already removed by the OS — i.e. pure motion. Used to drive splash impulses
 * when the phone is shaken / jolted.
 *
 *  x > 0 → device shoved right
 *  y > 0 → device shoved up (in portrait)
 *  z > 0 → device shoved toward the user (out of screen)
 *
 * Falls back to (0, 0, 0) state if TYPE_LINEAR_ACCELERATION is missing
 * (rare on modern hardware). Auto-registers / unregisters with the
 * composition lifecycle.
 */
@Composable
fun rememberLinearAcceleration(): State<Triple<Float, Float, Float>> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Triple(0f, 0f, 0f)) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val listener = if (sensor != null && sensorManager != null) {
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    state.value = Triple(event.values[0], event.values[1], event.values[2])
                }
                override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
            }.also {
                sensorManager.registerListener(it, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        } else null

        onDispose {
            if (listener != null && sensorManager != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }
    return state
}
