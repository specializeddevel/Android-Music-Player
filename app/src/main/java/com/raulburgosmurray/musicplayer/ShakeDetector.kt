package com.raulburgosmurray.musicplayer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var lastShakeTime: Long = 0
    private val shakeThreshold = 2.5f // Sensibilidad aumentada (antes era 12.0f)
    private val minTimeBetweenShakes = 1000 // ms

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Fuerza G total (1.0 es el estado de reposo)
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        // Si la fuerza G es significativamente mayor que 1.0, detectamos el agitado
        if (gForce > shakeThreshold) {
            val now = System.currentTimeMillis()
            if (lastShakeTime + minTimeBetweenShakes > now) return
            
            lastShakeTime = now
            Log.d("ShakeDetector", "¡Agitado detectado! G-Force: $gForce")
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
