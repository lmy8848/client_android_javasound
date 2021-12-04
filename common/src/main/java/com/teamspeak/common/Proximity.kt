package com.teamspeak.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class Proximity(context: Context, private val delegate: Delegate) {

    interface Delegate {
        fun onProximitySensorNear()
        fun onProximitySensorFar()
    }

    companion object {
        private val TAG = Proximity::class.java.simpleName
        private const val waitForProximityNegative = 1

        private fun getWakelockParameterizeReleaseMethod(): Method? {
            try {
                return PowerManager.WakeLock::class.java.getDeclaredMethod("release", Integer.TYPE)
            } catch (e: NoSuchMethodException) {
                Log.d(TAG, "Parametrized WakeLock release is not available on this device.")
            }

            return null
        }
    }

    private val powerManager: PowerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val wakelockParametrizedRelease = getWakelockParameterizeReleaseMethod()

    private val sensorManager: SensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val sensorListener = if (sensor == null) null else object: SensorEventListener{
        override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {
        }

        override fun onSensorChanged(event: SensorEvent) {
            try{
                if (event.values != null && event.values.isNotEmpty()) {
                    if (event.values[0] < sensor.maximumRange) {
                        delegate.onProximitySensorNear()
                    } else {
                        delegate.onProximitySensorFar()
                    }
                }
            } catch(exc: Exception ) {
                Log.e(TAG, "onSensorChanged exception", exc)
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                val wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
                wakeLock.setReferenceCounted(false)
                proximityWakeLock = wakeLock
            }
        }
        else {
            try {
                val wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
                wakeLock.setReferenceCounted(false)
                proximityWakeLock = wakeLock
            } catch (t: Throwable) {
                Log.e(TAG, "Couldn't create proximity lock.")
            }
        }
    }

    fun acquireProximityScreenWakeLock() {
        if (proximityWakeLock?.isHeld == false) {
            proximityWakeLock!!.acquire()
        }
    }

    fun releaseProximityScreenWakeLock() {
        if (proximityWakeLock?.isHeld == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                proximityWakeLock!!.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            } else {
                var released = false

                if (wakelockParametrizedRelease != null) {
                    try {
                        wakelockParametrizedRelease.invoke(proximityWakeLock, waitForProximityNegative)
                        released = true
                    } catch (e: IllegalAccessException) {
                        Log.w(TAG, e)
                    } catch (e: InvocationTargetException) {
                        Log.w(TAG, e)
                    }
                }

                if (!released) proximityWakeLock!!.release()
            }
        }
    }

    fun startCustomSensor() {
        if (sensor == null || sensorListener == null)
            return

        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopCustomSensor() {
        if (sensor == null || sensorListener == null)
            return

        sensorManager.unregisterListener(sensorListener)
    }

}