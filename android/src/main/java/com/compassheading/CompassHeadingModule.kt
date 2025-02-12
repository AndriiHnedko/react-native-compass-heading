package com.compassheading

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.Surface
import android.view.Display
import android.view.WindowManager
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlin.math.abs

class CompassHeadingModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), SensorEventListener {

    companion object {
        const val NAME = "CompassHeading"
    }

    private val mApplicationContext: Context = reactContext.applicationContext
    private var mAzimuth: Int = 0 // degree
    private var mFilter: Int = 1
    private var sensorManager: SensorManager? = null

    override fun getName(): String {
        return NAME
    }

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for React Native built-in Event Emitter Calls
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for React Native built-in Event Emitter Calls
    }

    @ReactMethod
    fun start(filter: Int, promise: Promise) {
        try {
            sensorManager = mApplicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val rvSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            sensorManager?.registerListener(this, rvSensor, SensorManager.SENSOR_DELAY_GAME)
            mFilter = filter
            Log.d(NAME, "Compass heading started with filter: $mFilter")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e(NAME, "Failed to start compass heading: ${e.message}")
            promise.reject("failed_start", e.message)
        }
    }

    @ReactMethod
    fun stop() {
        sensorManager?.unregisterListener(this)
        Log.d(NAME, "Compass heading stopped")
    }

    @ReactMethod
    fun hasCompass(promise: Promise) {
        try {
            val manager = mApplicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val hasCompass = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
                    manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
            promise.resolve(hasCompass)
        } catch (e: Exception) {
            Log.e(NAME, "Error checking for compass: ${e.message}")
            promise.resolve(false)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> onUpdateRotationVector(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun onUpdateRotationVector(event: SensorEvent) {
      val rotationVector = floatArrayOf(event.values[0], event.values[1], event.values[2])
      val rotationMatrix = getRotationMatrix(rotationVector)
      val normalizedAxis = normalizeAxisByDisplayRotation()
      val remappedRotationMatrix = remapRotationMatrix(rotationMatrix, normalizedAxis[0], normalizedAxis[1])
      val azimuthInRad = SensorManager.getOrientation(remappedRotationMatrix, FloatArray(3))[0]
      val azimuthInDeg = Math.toDegrees(azimuthInRad.toDouble())
      val newAzimuth = (azimuthInDeg + 360f) % 360f
      if (abs(mAzimuth - newAzimuth) > mFilter) {
        mAzimuth = newAzimuth.toInt()
        emitUpdate()
      }
    }

    private fun getRotationMatrix(rotationVector: FloatArray): FloatArray {
      val rotationMatrix = FloatArray(9)
      SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
      return rotationMatrix
    }

    private fun remapRotationMatrix(rotationMatrix: FloatArray, newX: Int, newY: Int): FloatArray {
      val remappedRotationMatrix = FloatArray(9)
      SensorManager.remapCoordinateSystem(rotationMatrix, newX, newY, remappedRotationMatrix)
      return remappedRotationMatrix
    }

    private fun getDisplay(): Display? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            reactApplicationContext.currentActivity?.display
        } else {
            (mApplicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
    }

    private fun normalizeAxisByDisplayRotation(): IntArray {
      return when (getDisplay()?.rotation) {
        Surface.ROTATION_90 -> intArrayOf(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X)
        Surface.ROTATION_180 -> intArrayOf(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
        Surface.ROTATION_270 -> intArrayOf(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
        else -> intArrayOf(SensorManager.AXIS_X, SensorManager.AXIS_Y)
      }
    }

    private fun emitUpdate() {
      val params = Arguments.createMap().apply {
        putDouble("heading", mAzimuth.toDouble())
        putDouble("accuracy", 1.0)
      }

      reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("HeadingUpdated", params)
    }
}
