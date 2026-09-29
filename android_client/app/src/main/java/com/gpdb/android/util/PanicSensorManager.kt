package com.gpdb.android.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

class PanicSensorManager(
    private val context: Context,
    private val onPanicTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    var enableFaceDown: Boolean = true
    var enableShake: Boolean = true

    private val mainHandler = Handler(Looper.getMainLooper())

    // Face down debouncing
    private var faceDownStartTime: Long = 0L
    private var isFaceDownPending: Boolean = false
    private var isProximityNear: Boolean = false

    // Shake detection
    private var lastShakeTime: Long = 0L
    private var shakeCount: Int = 0

    // Panic cooldown to prevent multiple rapid triggers
    private var lastPanicTriggerTime: Long = 0L

    fun startListening() {
        sensorManager?.let { sm ->
            accelerometer?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            proximity?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        isFaceDownPending = false
        shakeCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        if (now - lastPanicTriggerTime < 2000L) {
            return // 2秒防抖冷却
        }

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                isProximityNear = distance < 1.5f || (maxRange > 0 && distance < maxRange * 0.5f)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // 1. 翻转扣桌检测 (Face Down)
                // z轴为屏幕法线方向，朝上时 z ≈ +9.8，屏幕朝下扣在桌上时 z < -7.0
                if (enableFaceDown) {
                    val isScreenFacingDown = z < -7.0f && Math.abs(x) < 5.0f && Math.abs(y) < 5.0f
                    if (isScreenFacingDown || (isProximityNear && z < -3.0f)) {
                        if (!isFaceDownPending) {
                            isFaceDownPending = true
                            faceDownStartTime = now
                        } else if (now - faceDownStartTime >= 300L) {
                            // 保持扣桌超过 300 毫秒，确认触发紧急脱身
                            isFaceDownPending = false
                            triggerPanic(now)
                            return
                        }
                    } else {
                        isFaceDownPending = false
                    }
                }

                // 2. 剧烈摇晃检测 (Shake)
                if (enableShake) {
                    val gX = x / SensorManager.GRAVITY_EARTH
                    val gY = y / SensorManager.GRAVITY_EARTH
                    val gZ = z / SensorManager.GRAVITY_EARTH
                    val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

                    if (gForce > 2.7f) {
                        if (now - lastShakeTime < 600L) {
                            shakeCount++
                            if (shakeCount >= 2) {
                                shakeCount = 0
                                triggerPanic(now)
                                return
                            }
                        } else {
                            shakeCount = 1
                        }
                        lastShakeTime = now
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerPanic(timestamp: Long) {
        lastPanicTriggerTime = timestamp
        mainHandler.post {
            onPanicTriggered()
        }
    }
}
