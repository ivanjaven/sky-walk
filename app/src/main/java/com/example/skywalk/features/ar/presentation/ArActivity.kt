package com.example.skywalk.features.ar.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.example.skywalk.R
import com.example.skywalk.core.util.PermissionHelper
import com.example.skywalk.features.ar.presentation.components.SkyOverlayView
import com.example.skywalk.features.ar.presentation.viewmodel.AstronomyViewModel
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class ARActivity : ComponentActivity(), LocationListener {
    // View references
    private lateinit var previewView: PreviewView
    private lateinit var skyOverlayView: SkyOverlayView
    private lateinit var backButton: ImageButton

    // Camera components
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService

    // Viewmodel and system services
    private lateinit var astronomyViewModel: AstronomyViewModel
    private lateinit var locationManager: LocationManager

    // Track whether the camera was successfully started
    private var cameraStarted = false

    // Location settings
    private var lastKnownLocation: Location? = null
    private val MIN_LOCATION_UPDATE_INTERVAL_MS = 30000L  // 30 seconds
    private val MIN_LOCATION_DISTANCE_M = 100f            // 100 meters

    // Gesture handling
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoom = 1.0f
    private val MIN_ZOOM = 0.8f
    private val MAX_ZOOM = 1.5f
    private val ZOOM_SPEED = 0.5f

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasCamera = permissions[Manifest.permission.CAMERA] == true
        val hasLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        when {
            hasCamera && hasLocation -> {
                startCamera()
                startLocationUpdates()
            }
            hasCamera -> {
                startCamera()
                Toast.makeText(this, "Using approximate location - accuracy may be reduced",
                    Toast.LENGTH_SHORT).show()
            }
            hasLocation -> {
                startLocationUpdates()
                Toast.makeText(this, "Camera permission denied. Sky overlay won't work properly.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
            else -> {
                Toast.makeText(this, "Camera and location permissions are required",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during AR experience
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set full screen mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        setContentView(R.layout.activity_ar)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        skyOverlayView = findViewById(R.id.skyOverlayView)
        backButton = findViewById(R.id.backButton)

        // Initialize ViewModel
        astronomyViewModel = AstronomyViewModel(application)

        // Get location service
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Setup gesture detectors
        setupGestureDetectors()

        // Initialize the SkyOverlayView
        skyOverlayView.initialize(astronomyViewModel)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Check and request permissions
        checkPermissions()
    }

    private fun setupGestureDetectors() {
        // Simple tap gesture detector
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleSystemUi()
                return true
            }

            // Handle double tap to reset zoom
            override fun onDoubleTap(e: MotionEvent): Boolean {
                currentZoom = 1.0f
                updateFieldOfView()
                return true
            }
        })

        // Pinch to zoom gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Adjust zoom based on the scale factor
                currentZoom *= (1.0f / (1.0f + (detector.scaleFactor - 1.0f) * ZOOM_SPEED))

                // Constrain zoom
                currentZoom = max(MIN_ZOOM, min(MAX_ZOOM, currentZoom))

                // Update the field of view
                updateFieldOfView()
                return true
            }
        })
    }

    private fun updateFieldOfView() {
        // Calculate new field of view - more zoom = smaller FOV
        // The base FOV is 60 degrees. Zoom of 0.8 = 75 degrees, zoom of 1.5 = 40 degrees
        val baseFov = 60f
        val newFov = baseFov / currentZoom

        // TODO: Update FOV in ViewModel when implemented
        // For now, just show a toast
        Toast.makeText(this, "Zoom: ${String.format("%.1fx", currentZoom)}",
            Toast.LENGTH_SHORT).show()
    }

    private fun toggleSystemUi() {
        if ((window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
            // Show UI
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            // Hide UI
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    private fun checkPermissions() {
        val neededPermissions = mutableListOf<String>()

        // Check camera permission
        if (!PermissionHelper.hasCameraPermission(this)) {
            neededPermissions.add(Manifest.permission.CAMERA)
        }

        // Check location permissions
        if (!PermissionHelper.hasLocationPermission(this)) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (neededPermissions.isNotEmpty()) {
            // Need to request permissions
            requestPermissionsLauncher.launch(neededPermissions.toTypedArray())
        } else {
            // Already have permissions
            startCamera()
            startLocationUpdates()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Configure camera
                val preview = Preview.Builder()
                    .build()

                // Connect the preview to the PreviewView
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)

                cameraStarted = true
                Timber.d("Camera started successfully")

            } catch (e: Exception) {
                Timber.e(e, "Camera initialization failed")
                Toast.makeText(this, "Camera initialization failed: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationUpdates() {
        if (!PermissionHelper.hasLocationPermission(this)) {
            Timber.d("No location permission, using default location")
            return
        }

        try {
            // Get the best last known location from available providers
            val providers = locationManager.getProviders(true)

            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null && (lastKnownLocation == null ||
                            location.accuracy < lastKnownLocation!!.accuracy)) {
                    lastKnownLocation = location
                }
            }

            // Use the best location we found
            lastKnownLocation?.let {
                updateLocation(it)
                Timber.d("Using last known location: ${it.latitude}, ${it.longitude}")
            }

            // Request location updates from GPS
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_LOCATION_UPDATE_INTERVAL_MS,
                MIN_LOCATION_DISTANCE_M,
                this
            )

            // Also request network location updates as backup
            if (locationManager.getProviders(true).contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_LOCATION_UPDATE_INTERVAL_MS,
                    MIN_LOCATION_DISTANCE_M,
                    this
                )
            }

        } catch (e: SecurityException) {
            Timber.e(e, "Security exception when accessing location")
        } catch (e: Exception) {
            Timber.e(e, "Error getting location")
            Toast.makeText(this, "Unable to access location: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocation(location: Location) {
        lastKnownLocation = location
        astronomyViewModel.setLocation(location.latitude, location.longitude)
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location update: ${location.latitude}, ${location.longitude}")
        updateLocation(location)
    }

    // Required LocationListener interface methods
    override fun onProviderDisabled(provider: String) {
        Timber.d("Location provider disabled: $provider")
    }

    override fun onProviderEnabled(provider: String) {
        Timber.d("Location provider enabled: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        Timber.d("Location provider status changed: $provider, status: $status")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle all touch events with our gesture detectors
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()

        // Re-register for location updates
        if (PermissionHelper.hasLocationPermission(this)) {
            startLocationUpdates()
        }

        // Reset UI visibility
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onPause() {
        super.onPause()

        // Remove location updates to conserve battery
        locationManager.removeUpdates(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        locationManager.removeUpdates(this)
    }
}