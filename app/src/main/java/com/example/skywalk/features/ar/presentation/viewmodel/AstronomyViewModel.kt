// features/ar/presentation/viewmodel/AstronomyViewModel.kt

package com.example.skywalk.features.ar.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Vector3
import com.example.skywalk.features.ar.utils.AstronomyUtils
import timber.log.Timber
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

class AstronomyViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _deviceOrientation = MutableLiveData<DeviceOrientation>()
    val deviceOrientation: LiveData<DeviceOrientation> = _deviceOrientation

    private val _deviceOrientationVector = MutableLiveData<Vector3>()
    val deviceOrientationVector: LiveData<Vector3> = _deviceOrientationVector

    private val _deviceUpVector = MutableLiveData<Vector3>()
    val deviceUpVector: LiveData<Vector3> = _deviceUpVector

    private val _celestialObjects = MutableLiveData<List<CelestialObject>>()
    val celestialObjects: LiveData<List<CelestialObject>> = _celestialObjects

    private var useRotationVector = true
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)

    private var userLocation: Location? = null
    private val fieldOfViewDegrees = 60f // Typical camera field of view

    init {
        // Register sensor listeners
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            useRotationVector = true
        } else if (accelerometerSensor != null && magnetometerSensor != null) {
            sensorManager.registerListener(
                this,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            sensorManager.registerListener(
                this,
                magnetometerSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            useRotationVector = false
        } else {
            Timber.e("No suitable sensors found for orientation detection")
        }

        // Set a default location if none provided
        if (userLocation == null) {
            val defaultLocation = Location("default")
            defaultLocation.latitude = 34.0522 // Los Angeles
            defaultLocation.longitude = -118.2437
            userLocation = defaultLocation
        }

        // Initialize celestial objects
        loadCelestialObjects()
    }

    private fun loadCelestialObjects() {
        val objects = listOf(
            CelestialObject(
                name = "Sun",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = -26.7f,
                imageResourceId = com.example.skywalk.R.drawable.sun,
                type = "Star"
            ),
            CelestialObject(
                name = "Mercury",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = 0.5f,
                imageResourceId = com.example.skywalk.R.drawable.mercury,
                type = "Planet"
            ),
            CelestialObject(
                name = "Venus",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = -4.6f,
                imageResourceId = com.example.skywalk.R.drawable.venus,
                type = "Planet"
            ),
            CelestialObject(
                name = "Mars",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = 0.7f,
                imageResourceId = com.example.skywalk.R.drawable.mars,
                type = "Planet"
            ),
            CelestialObject(
                name = "Jupiter",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = -2.7f,
                imageResourceId = com.example.skywalk.R.drawable.jupiter,
                type = "Planet"
            ),
            CelestialObject(
                name = "Saturn",
                skyCoordinate = SkyCoordinate(0f, 0f), // Will be updated in real-time
                magnitude = 0.6f,
                imageResourceId = com.example.skywalk.R.drawable.saturn,
                type = "Planet"
            )
        )

        _celestialObjects.value = objects

        // Now update their positions
        updateCelestialPositions()
    }

    private fun updateCelestialPositions() {
        val currentTime = Date()

        val updatedObjects = _celestialObjects.value?.map { obj ->
            // Calculate the actual position using astronomical formulas
            val skyCoordinate = AstronomyUtils.calculatePlanetPosition(obj.name, currentTime)
            obj.copy(skyCoordinate = skyCoordinate)
        } ?: emptyList()

        _celestialObjects.postValue(updatedObjects)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Process rotation vector
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                updateOrientation()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Process accelerometer data
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                updateOrientationAngles()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Process magnetometer data
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                updateOrientationAngles()
            }
        }
    }

    private fun updateOrientationAngles() {
        if (!useRotationVector) {
            // Update rotation matrix using accelerometer and magnetometer
            SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )

            // Get orientation angles from rotation matrix
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            updateOrientation()
        }
    }

    private fun updateOrientation() {
        // Convert radians to degrees
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        _deviceOrientation.postValue(
            DeviceOrientation(
                azimuth = azimuth,
                pitch = pitch,
                roll = roll
            )
        )

        // Convert orientation angles to a direction vector
        // This will represent where the phone is pointing
        val lookX = -cos(orientationAngles[1]) * sin(orientationAngles[0])
        val lookY = -cos(orientationAngles[1]) * cos(orientationAngles[0])
        val lookZ = -sin(orientationAngles[1])

        _deviceOrientationVector.postValue(
            Vector3(lookX, lookY, lookZ)
        )

        // Calculate the "up" vector for the phone
        val upX = sin(orientationAngles[2]) * sin(orientationAngles[0]) - cos(orientationAngles[2]) * sin(orientationAngles[1]) * cos(orientationAngles[0])
        val upY = -sin(orientationAngles[2]) * cos(orientationAngles[0]) - cos(orientationAngles[2]) * sin(orientationAngles[1]) * sin(orientationAngles[0])
        val upZ = -cos(orientationAngles[2]) * cos(orientationAngles[1])

        _deviceUpVector.postValue(
            Vector3(upX, upY, upZ)
        )

        // Update celestial positions every few seconds
        // In a real app, you might want to do this less frequently
        if (System.currentTimeMillis() % 5000 < 100) {
            updateCelestialPositions()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister sensor listeners
        sensorManager.unregisterListener(this)
    }

    fun setLocation(latitude: Double, longitude: Double) {
        val location = Location("manual")
        location.latitude = latitude
        location.longitude = longitude
        userLocation = location

        // Update celestial positions with new location
        updateCelestialPositions()
    }

    fun getFieldOfViewRadians(): Float {
        return (fieldOfViewDegrees * Math.PI / 180).toFloat()
    }
}