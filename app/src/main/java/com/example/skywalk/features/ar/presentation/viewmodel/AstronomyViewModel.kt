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
import androidx.lifecycle.viewModelScope
import com.example.skywalk.R
import com.example.skywalk.features.ar.data.StarRepository
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Star
import com.example.skywalk.features.ar.domain.models.Vector3
import com.example.skywalk.features.ar.utils.AstronomyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.PI

class AstronomyViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Repositories
    private val starRepository = StarRepository(application)

    // Sensors
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // LiveData objects
    private val _deviceOrientation = MutableLiveData<DeviceOrientation>()
    val deviceOrientation: LiveData<DeviceOrientation> = _deviceOrientation

    private val _deviceOrientationVector = MutableLiveData<Vector3>()
    val deviceOrientationVector: LiveData<Vector3> = _deviceOrientationVector

    private val _deviceUpVector = MutableLiveData<Vector3>()
    val deviceUpVector: LiveData<Vector3> = _deviceUpVector

    private val _celestialObjects = MutableLiveData<List<CelestialObject>>()
    val celestialObjects: LiveData<List<CelestialObject>> = _celestialObjects

    private val _stars = MutableLiveData<List<Star>>()
    val stars: LiveData<List<Star>> = _stars

    // Sensor data arrays
    private val rotationMatrix = FloatArray(9)
    private val rotationMatrixAdjusted = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationVector = FloatArray(4) { if (it == 3) 1f else 0f }

    // Filtering for smooth movement
    private val LOW_PASS_ALPHA = 0.1f
    private val filteredMatrix = FloatArray(9)
    private val prevFilteredMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f }

    // Field of view and zoom
    private var fieldOfViewRadians = (60f * PI.toFloat() / 180f)
    private var currentZoom = 1.0f

    // User location
    private var latitude = 34.0522  // Default: Los Angeles
    private var longitude = -118.2437

    // Scheduling and time
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var currentDate = Date()
    private val updateTimeFrequencyMs = 1000L

    init {
        // Load stars in background
        viewModelScope.launch(Dispatchers.IO) {
            loadStars()
        }

        // Initialize all celestial objects
        initializeCelestialObjects()
        registerSensors()

        // Schedule regular updates
        scheduler.scheduleAtFixedRate({
            currentDate = Date() // Update current time
            updateCelestialPositions()
        }, 0, updateTimeFrequencyMs, TimeUnit.MILLISECONDS)
    }

    private fun loadStars() {
        try {
            val stars = starRepository.getStarsForZoomLevel(currentZoom)
            _stars.postValue(stars)
            Timber.d("Loaded ${stars.size} stars for zoom level $currentZoom")
        } catch (e: Exception) {
            Timber.e(e, "Error loading stars: ${e.message}")
        }
    }

    private fun initializeCelestialObjects() {
        // Initialize with placeholder positions - will be updated immediately
        val objects = listOf(
            CelestialObject(
                name = "Sun",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -26.7f,
                imageResourceId = R.drawable.sun,
                type = "Star"
            ),
            CelestialObject(
                name = "Moon",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -12.7f,
                imageResourceId = R.drawable.moon,
                type = "Moon"
            ),
            CelestialObject(
                name = "Mercury",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.5f,
                imageResourceId = R.drawable.mercury,
                type = "Planet"
            ),
            CelestialObject(
                name = "Venus",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -4.6f,
                imageResourceId = R.drawable.venus,
                type = "Planet"
            ),
            CelestialObject(
                name = "Mars",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.7f,
                imageResourceId = R.drawable.mars,
                type = "Planet"
            ),
            CelestialObject(
                name = "Jupiter",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = -2.7f,
                imageResourceId = R.drawable.jupiter,
                type = "Planet"
            ),
            CelestialObject(
                name = "Saturn",
                skyCoordinate = SkyCoordinate(0f, 0f),
                magnitude = 0.6f,
                imageResourceId = R.drawable.saturn,
                type = "Planet"
            )
        )

        _celestialObjects.postValue(objects)
        updateCelestialPositions()
    }

    private fun updateCelestialPositions() {
        try {
            val currentObjects = _celestialObjects.value ?: return

            // Calculate local sidereal time
            val lst = AstronomyUtils.calculateSiderealTime(currentDate, longitude.toFloat())

            // Update positions for planets and sun/moon
            val updatedObjects = currentObjects.map { obj ->
                val skyCoordinate = when (obj.name) {
                    "Sun" -> AstronomyUtils.calculateSunPosition(
                        currentDate,
                        latitude.toFloat(),
                        longitude.toFloat()
                    )
                    "Moon" -> AstronomyUtils.calculateMoonPosition(
                        currentDate,
                        latitude.toFloat(),
                        longitude.toFloat()
                    )
                    "Mercury", "Venus", "Mars", "Jupiter", "Saturn" -> AstronomyUtils.calculatePlanetPosition(
                        currentDate,
                        latitude.toFloat(),
                        longitude.toFloat(),
                        obj.name
                    )
                    else -> obj.skyCoordinate
                }

                obj.copy(skyCoordinate = skyCoordinate)
            }

            _celestialObjects.postValue(updatedObjects)

            // Also update star positions to horizontal coordinates
            updateStarPositions(lst)

        } catch (e: Exception) {
            Timber.e(e, "Error updating celestial positions")
        }
    }

    // Update star positions to current horizontal coordinates
    private fun updateStarPositions(lst: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentStars = _stars.value ?: return@launch

            // No need to recalculate all stars if we have too many
            // Just process the visible ones that are likely to be on screen
            val maxStarsToProcess = 2000
            val starsToProcess = if (currentStars.size > maxStarsToProcess) {
                currentStars.subList(0, maxStarsToProcess)
            } else {
                currentStars
            }

            // We don't need to update the actual star objects since RA/Dec doesn't change
            // The conversion to horizontal coordinates happens in the drawing phase
            // This is just a notification that time has changed and stars need redrawing
            _stars.postValue(starsToProcess)
        }
    }

    private fun registerSensors() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(
                this,
                rotationVectorSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            Timber.d("Using rotation vector sensor")
        } else {
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
            Timber.d("Using accelerometer and magnetometer")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                System.arraycopy(event.values, 0, rotationVector, 0,
                    minOf(event.values.size, rotationVector.size))
                SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
                processRotationMatrix()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0,
                    minOf(event.values.size, accelerometerReading.size))
                updateOrientationFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0,
                    minOf(event.values.size, magnetometerReading.size))
                updateOrientationFromAccelMag()
            }
        }
    }

    private fun updateOrientationFromAccelMag() {
        if (accelerometerReading.isNotEmpty() && magnetometerReading.isNotEmpty()) {
            val success = SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )

            if (success) {
                processRotationMatrix()
            }
        }
    }

    private fun processRotationMatrix() {
        // Adjust for screen rotation
        adjustForScreenRotation()

        // Apply complementary filtering
        applyMatrixFilter()

        // Get orientation angles
        SensorManager.getOrientation(filteredMatrix, orientationAngles)

        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        _deviceOrientation.postValue(
            DeviceOrientation(
                azimuth = (azimuth + 360) % 360,
                pitch = pitch,
                roll = roll
            )
        )

        // Extract look and up vectors
        val lookVector = Vector3(
            x = -filteredMatrix[2],
            y = -filteredMatrix[5],
            z = -filteredMatrix[8]
        ).normalize()

        val upVector = Vector3(
            x = filteredMatrix[1],
            y = filteredMatrix[4],
            z = filteredMatrix[7]
        ).normalize()

        _deviceOrientationVector.postValue(lookVector)
        _deviceUpVector.postValue(upVector)

        // Store current filtered matrix for next update
        System.arraycopy(filteredMatrix, 0, prevFilteredMatrix, 0, 9)
    }

    private fun applyMatrixFilter() {
        for (i in 0 until 9) {
            filteredMatrix[i] = LOW_PASS_ALPHA * rotationMatrixAdjusted[i] +
                    (1 - LOW_PASS_ALPHA) * prevFilteredMatrix[i]
        }
        orthonormalizeMatrix(filteredMatrix)
    }

    private fun orthonormalizeMatrix(matrix: FloatArray) {
        // Extract and normalize column vectors
        val v1 = Vector3(matrix[0], matrix[3], matrix[6]).normalize()
        var v2 = Vector3(matrix[1], matrix[4], matrix[7])

        // Make v2 orthogonal to v1
        val dot = v1.dot(v2)
        v2 = Vector3(
            v2.x - v1.x * dot,
            v2.y - v1.y * dot,
            v2.z - v1.z * dot
        ).normalize()

        // v3 is cross product of v1 and v2
        val v3 = v1.cross(v2).normalize()

        // Put the orthonormal vectors back into the matrix
        matrix[0] = v1.x; matrix[3] = v1.y; matrix[6] = v1.z
        matrix[1] = v2.x; matrix[4] = v2.y; matrix[7] = v2.z
        matrix[2] = v3.x; matrix[5] = v3.y; matrix[8] = v3.z
    }

    private fun adjustForScreenRotation() {
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
        scheduler.shutdown()
    }

    fun getFieldOfViewRadians(): Float {
        return fieldOfViewRadians
    }

    fun setFieldOfView(degrees: Float) {
        fieldOfViewRadians = (degrees * PI.toFloat() / 180f)
    }

    fun setZoom(zoom: Float) {
        currentZoom = zoom

        // Reload stars with appropriate filtering
        viewModelScope.launch(Dispatchers.IO) {
            val stars = starRepository.getStarsForZoomLevel(zoom)
            _stars.postValue(stars)
        }
    }

    fun setLocation(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
        updateCelestialPositions()
        Timber.d("Location set to lat=$latitude, long=$longitude")
    }

    fun getCurrentDate(): Date = currentDate

    fun getLatitude(): Float = latitude.toFloat()

    fun getLongitude(): Float = longitude.toFloat()
}