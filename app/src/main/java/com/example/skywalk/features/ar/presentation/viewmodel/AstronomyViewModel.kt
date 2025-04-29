package com.example.skywalk.features.ar.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AstronomyViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Sensors
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gameRotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // LiveData objects observed by the UI
    private val _deviceOrientation = MutableLiveData<DeviceOrientation>()
    val deviceOrientation: LiveData<DeviceOrientation> = _deviceOrientation

    private val _deviceOrientationVector = MutableLiveData<Vector3>()
    val deviceOrientationVector: LiveData<Vector3> = _deviceOrientationVector

    private val _deviceUpVector = MutableLiveData<Vector3>()
    val deviceUpVector: LiveData<Vector3> = _deviceUpVector

    private val _celestialObjects = MutableLiveData<List<CelestialObject>>()
    val celestialObjects: LiveData<List<CelestialObject>> = _celestialObjects

    // Sensor data arrays
    private var rotationMatrix = FloatArray(9)
    private var rotationMatrixAdjusted = FloatArray(9)
    private var orientationAngles = FloatArray(3)
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)

    // Gyroscope integration values
    private var timestamp = 0L
    private var rotationVectorFromGyro = FloatArray(4) { if (it == 3) 1f else 0f }
    private val deltaRotationVector = FloatArray(4) { if (it == 3) 1f else 0f }
    private val deltaRotationMatrix = FloatArray(9)

    // Sensor fusion strategy flags
    private var useRotationVector = true
    private var useGameRotation = false
    private var useGyroAndMagAccel = false
    private var useLegacyFusion = false

    // Complementary filter coefficients (0.0-1.0)
    private val ROTATION_FILTER_COEFFICIENT = 0.92f
    private val COMPLEMENTARY_FILTER_COEFFICIENT = 0.98f

    // Filtered orientation values
    private var filteredRotationVector = FloatArray(4) { if (it == 3) 1f else 0f }
    private var filteredOrientationAngles = FloatArray(3)
    private var filteredRotationMatrix = FloatArray(9)

    // User location
    private var latitude = 34.0522  // Default: Los Angeles
    private var longitude = -118.2437

    // Scheduler for background updates
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Field of view (should match camera)
    private val fieldOfViewDegrees = 60f

    init {
        initializeLocationAndObjects()
        determineSensorStrategy()
        registerSensors()

        // Schedule updates for celestial positions
        scheduler.scheduleAtFixedRate({
            updateCelestialPositions()
        }, 2, 30, TimeUnit.SECONDS)
    }

    private fun initializeLocationAndObjects() {
        // Initialize with default celestial objects
        val objects = listOf(
            CelestialObject(
                name = "Sun",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -26.7f,
                imageResourceId = com.example.skywalk.R.drawable.sun,
                type = "Star"
            ),
            CelestialObject(
                name = "Moon",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -12.7f,
                imageResourceId = com.example.skywalk.R.drawable.moon,
                type = "Moon"
            ),
            CelestialObject(
                name = "Mercury",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.5f,
                imageResourceId = com.example.skywalk.R.drawable.mercury,
                type = "Planet"
            ),
            CelestialObject(
                name = "Venus",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -4.6f,
                imageResourceId = com.example.skywalk.R.drawable.venus,
                type = "Planet"
            ),
            CelestialObject(
                name = "Mars",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.7f,
                imageResourceId = com.example.skywalk.R.drawable.mars,
                type = "Planet"
            ),
            CelestialObject(
                name = "Jupiter",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -2.7f,
                imageResourceId = com.example.skywalk.R.drawable.jupiter,
                type = "Planet"
            ),
            CelestialObject(
                name = "Saturn",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.6f,
                imageResourceId = com.example.skywalk.R.drawable.saturn,
                type = "Planet"
            )
        )

        _celestialObjects.postValue(objects)

        // Initial update of positions
        updateCelestialPositions()
    }

    private fun determineSensorStrategy() {
        // Determine the best sensor fusion strategy based on available sensors
        useRotationVector = rotationVectorSensor != null
        useGameRotation = !useRotationVector && gameRotationSensor != null
        useGyroAndMagAccel = !useRotationVector && !useGameRotation &&
                gyroscopeSensor != null &&
                accelerometerSensor != null &&
                magnetometerSensor != null
        useLegacyFusion = !useRotationVector && !useGameRotation && !useGyroAndMagAccel &&
                accelerometerSensor != null && magnetometerSensor != null

        // Log which strategy we're using
        when {
            useRotationVector -> Timber.d("Using ROTATION_VECTOR sensor (best accuracy)")
            useGameRotation -> Timber.d("Using GAME_ROTATION_VECTOR sensor (good accuracy, no magnetometer)")
            useGyroAndMagAccel -> Timber.d("Using gyroscope + accelerometer + magnetometer fusion")
            useLegacyFusion -> Timber.d("Using legacy accelerometer + magnetometer fusion")
            else -> Timber.e("No suitable sensors for orientation - AR functionality limited")
        }
    }

    private fun registerSensors() {
        // Unregister any existing listeners
        sensorManager.unregisterListener(this)

        // Register with appropriate sensors based on strategy
        when {
            useRotationVector -> {
                sensorManager.registerListener(
                    this,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                Timber.d("Registered with rotation vector sensor")
            }
            useGameRotation -> {
                sensorManager.registerListener(
                    this,
                    gameRotationSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                Timber.d("Registered with game rotation vector sensor")
            }
            useGyroAndMagAccel -> {
                sensorManager.registerListener(
                    this,
                    gyroscopeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
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
                Timber.d("Registered with gyro + accel + mag sensors")
            }
            useLegacyFusion -> {
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
                Timber.d("Registered with accel + mag sensors")
            }
        }
    }

    private fun updateCelestialPositions() {
        val currentTime = Date()

        Timber.d("Updating celestial positions for lat=$latitude, long=$longitude")

        try {
            // For testing, let's put objects in fixed, visible positions
            val testObjects = listOf(
                CelestialObject(
                    name = "Sun",
                    skyCoordinate = SkyCoordinate(90f, 30f),  // Fixed position for testing
                    magnitude = -26.7f,
                    imageResourceId = com.example.skywalk.R.drawable.sun,
                    type = "Star"
                ),
                CelestialObject(
                    name = "Moon",
                    skyCoordinate = SkyCoordinate(180f, 0f),  // Fixed position for testing
                    magnitude = -12.7f,
                    imageResourceId = com.example.skywalk.R.drawable.moon,
                    type = "Moon"
                ),
                CelestialObject(
                    name = "Mercury",
                    skyCoordinate = SkyCoordinate(270f, -20f),  // Fixed position for testing
                    magnitude = 0.5f,
                    imageResourceId = com.example.skywalk.R.drawable.mercury,
                    type = "Planet"
                ),
                CelestialObject(
                    name = "Venus",
                    skyCoordinate = SkyCoordinate(45f, 15f),  // Fixed position for testing
                    magnitude = -4.6f,
                    imageResourceId = com.example.skywalk.R.drawable.venus,
                    type = "Planet"
                ),
                CelestialObject(
                    name = "Mars",
                    skyCoordinate = SkyCoordinate(135f, -15f),  // Fixed position for testing
                    magnitude = 0.7f,
                    imageResourceId = com.example.skywalk.R.drawable.mars,
                    type = "Planet"
                ),
                CelestialObject(
                    name = "Jupiter",
                    skyCoordinate = SkyCoordinate(225f, 30f),  // Fixed position for testing
                    magnitude = -2.7f,
                    imageResourceId = com.example.skywalk.R.drawable.jupiter,
                    type = "Planet"
                ),
                CelestialObject(
                    name = "Saturn",
                    skyCoordinate = SkyCoordinate(315f, -30f),  // Fixed position for testing
                    magnitude = 0.6f,
                    imageResourceId = com.example.skywalk.R.drawable.saturn,
                    type = "Planet"
                )
            )

            _celestialObjects.postValue(testObjects)

            // Log the objects we're trying to display
            for (obj in testObjects) {
                Timber.d("Positioned object: ${obj.name} at RA=${obj.skyCoordinate.rightAscension}, " +
                        "Dec=${obj.skyCoordinate.declination}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating celestial positions")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                if (useRotationVector) {
                    processRotationVector(event.values)
                }
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                if (useGameRotation) {
                    processRotationVector(event.values)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (useGyroAndMagAccel) {
                    processGyroscope(event)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                if (useGyroAndMagAccel || useLegacyFusion) {
                    processFusedSensorData()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                if (useGyroAndMagAccel || useLegacyFusion) {
                    processFusedSensorData()
                }
            }
        }
    }

    private fun processRotationVector(rotationVector: FloatArray) {
        // Apply our smoothing filter first
        for (i in 0 until min(rotationVector.size, filteredRotationVector.size)) {
            if (i == 3) continue // Skip the scalar component for filtering
            filteredRotationVector[i] = applyComplementaryFilter(
                filteredRotationVector[i],
                rotationVector[i],
                ROTATION_FILTER_COEFFICIENT
            )
        }

        // Convert the rotation-vector to a rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, filteredRotationVector)

        // Adjust for screen rotation
        adjustForScreenRotation()

        // Get orientation angles from the adjusted rotation matrix
        SensorManager.getOrientation(rotationMatrixAdjusted, orientationAngles)

        // Apply filter to orientation angles for extra stability
        for (i in orientationAngles.indices) {
            filteredOrientationAngles[i] = applyComplementaryFilter(
                filteredOrientationAngles[i],
                orientationAngles[i],
                COMPLEMENTARY_FILTER_COEFFICIENT
            )
        }

        // Update view orientation vectors
        updateOrientationVectors()
    }

    private fun processGyroscope(event: SensorEvent) {
        // Gyroscope returns angular velocity, we need to integrate it over time
        if (timestamp != 0L) {
            val dT = (event.timestamp - timestamp) * 1.0f / 1000000000.0f // Convert to seconds

            // Axis of the rotation sample, normalized to unit length
            var axisX = event.values[0]
            var axisY = event.values[1]
            var axisZ = event.values[2]

            // Calculate angular speed
            val omegaMagnitude = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)

            // Normalize the rotation vector
            if (omegaMagnitude > 0.001f) {
                axisX /= omegaMagnitude
                axisY /= omegaMagnitude
                axisZ /= omegaMagnitude
            }

            // Integrate angular velocity to get rotation
            val thetaOverTwo = omegaMagnitude * dT / 2.0f
            val sinThetaOverTwo = sin(thetaOverTwo)
            val cosThetaOverTwo = cos(thetaOverTwo)

            deltaRotationVector[0] = sinThetaOverTwo * axisX
            deltaRotationVector[1] = sinThetaOverTwo * axisY
            deltaRotationVector[2] = sinThetaOverTwo * axisZ
            deltaRotationVector[3] = cosThetaOverTwo

            // Multiply current rotation by delta rotation
            multiplyQuaternions(rotationVectorFromGyro, deltaRotationVector)
        }

        timestamp = event.timestamp
    }

    private fun processFusedSensorData() {
        // Only process if we have valid readings from both sensors
        if (!accelerometerReading.all { it == 0f } && !magnetometerReading.all { it == 0f }) {
            // Get rotation matrix from accelerometer and magnetometer
            val rotationOK = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )

            if (rotationOK) {
                if (useGyroAndMagAccel) {
                    // Fuse with gyroscope data using quaternion
                    SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, rotationVectorFromGyro)

                    // Apply filter - blend between gyro-derived and mag/accel-derived matrices
                    // (Simplified matrix blending - a proper approach would use quaternions)
                    for (i in rotationMatrix.indices) {
                        filteredRotationMatrix[i] = COMPLEMENTARY_FILTER_COEFFICIENT * deltaRotationMatrix[i] +
                                (1 - COMPLEMENTARY_FILTER_COEFFICIENT) * rotationMatrix[i]
                    }

                    // Use the fused matrix
                    System.arraycopy(filteredRotationMatrix, 0, rotationMatrix, 0, 9)
                }

                // Adjust for screen rotation
                adjustForScreenRotation()

                // Get orientation angles from the adjusted rotation matrix
                SensorManager.getOrientation(rotationMatrixAdjusted, orientationAngles)

                // Apply filter to orientation angles
                for (i in orientationAngles.indices) {
                    filteredOrientationAngles[i] = applyComplementaryFilter(
                        filteredOrientationAngles[i],
                        orientationAngles[i],
                        COMPLEMENTARY_FILTER_COEFFICIENT
                    )
                }

                // Update view orientation vectors
                updateOrientationVectors()
            }
        }
    }

    private fun adjustForScreenRotation() {
        // Adjust coordinate system for screen rotation
        val rotation = windowManager.defaultDisplay.rotation

        when (rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X, SensorManager.AXIS_Y,
                rotationMatrixAdjusted
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                rotationMatrixAdjusted
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                rotationMatrixAdjusted
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                rotationMatrixAdjusted
            )
        }
    }

    private fun updateOrientationVectors() {
        // Convert radians to degrees for UI display
        val azimuth = Math.toDegrees(filteredOrientationAngles[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(filteredOrientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(filteredOrientationAngles[2].toDouble()).toFloat()

        // Update orientation angles for UI display
        _deviceOrientation.postValue(
            DeviceOrientation(
                azimuth = (azimuth + 360) % 360, // Normalize to 0-360
                pitch = pitch,
                roll = roll
            )
        )

        // Extract vectors directly from the rotation matrix for best accuracy

        // Look vector (3rd column of matrix, negated for camera direction)
        val lookX = -rotationMatrixAdjusted[2]
        val lookY = -rotationMatrixAdjusted[5]
        val lookZ = -rotationMatrixAdjusted[8]

        // Up vector (2nd column)
        val upX = rotationMatrixAdjusted[1]
        val upY = rotationMatrixAdjusted[4]
        val upZ = rotationMatrixAdjusted[7]

        _deviceOrientationVector.postValue(Vector3(lookX, lookY, lookZ))
        _deviceUpVector.postValue(Vector3(upX, upY, upZ))
    }

    private fun applyComplementaryFilter(current: Float, new: Float, alpha: Float): Float {
        // Handle the case where we're crossing the -pi/pi boundary for angles
        if (current < -3 && new > 3) {
            return alpha * (current + 2 * Math.PI.toFloat()) + (1 - alpha) * new
        } else if (current > 3 && new < -3) {
            return alpha * current + (1 - alpha) * (new + 2 * Math.PI.toFloat())
        }

        // Standard case
        return alpha * current + (1 - alpha) * new
    }

    private fun multiplyQuaternions(q1: FloatArray, q2: FloatArray) {
        val a = q1[3] * q2[0] + q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1]
        val b = q1[3] * q2[1] - q1[0] * q2[2] + q1[1] * q2[3] + q1[2] * q2[0]
        val c = q1[3] * q2[2] + q1[0] * q2[1] - q1[1] * q2[0] + q1[2] * q2[3]
        val d = q1[3] * q2[3] - q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2]

        q1[0] = a
        q1[1] = b
        q1[2] = c
        q1[3] = d
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up
        sensorManager.unregisterListener(this)
        scheduler.shutdown()
    }

    fun setLocation(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude

        // Update positions with new location
        updateCelestialPositions()
    }

    fun getFieldOfViewRadians(): Float {
        return (fieldOfViewDegrees * Math.PI / 180).toFloat()
    }

    // For testing/debugging - force a different sensor strategy
    fun setForcedSensorStrategy(strategy: String) {
        when (strategy) {
            "ROTATION_VECTOR" -> {
                useRotationVector = true
                useGameRotation = false
                useGyroAndMagAccel = false
                useLegacyFusion = false
            }
            "GAME_ROTATION" -> {
                useRotationVector = false
                useGameRotation = true
                useGyroAndMagAccel = false
                useLegacyFusion = false
            }
            "GYRO_FUSION" -> {
                useRotationVector = false
                useGameRotation = false
                useGyroAndMagAccel = true
                useLegacyFusion = false
            }
            "LEGACY" -> {
                useRotationVector = false
                useGameRotation = false
                useGyroAndMagAccel = false
                useLegacyFusion = true
            }
        }

        registerSensors()
        Timber.d("Forced sensor strategy to: $strategy")
    }
}