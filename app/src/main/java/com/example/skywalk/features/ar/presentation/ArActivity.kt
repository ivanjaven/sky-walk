// features/ar/presentation/ARActivity.kt

package com.example.skywalk.features.ar.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.skywalk.R
import com.example.skywalk.core.util.PermissionHelper
import com.example.skywalk.features.ar.presentation.components.SkyOverlayView
import com.example.skywalk.features.ar.presentation.viewmodel.AstronomyViewModel
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ARActivity : ComponentActivity(), LocationListener {
    // View references
    private lateinit var previewView: PreviewView
    private lateinit var skyOverlayView: SkyOverlayView
    private lateinit var backButton: ImageButton

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var astronomyViewModel: AstronomyViewModel
    private lateinit var locationManager: LocationManager

    // Add these required LocationListener interface methods
    override fun onProviderDisabled(provider: String) {
        // Handle case when location provider (like GPS) is disabled
        Timber.d("Location provider disabled: $provider")
    }

    override fun onProviderEnabled(provider: String) {
        // Handle case when location provider is enabled
        Timber.d("Location provider enabled: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
        // This is deprecated but still required to implement
        Timber.d("Location provider status changed: $provider, status: $status")
    }


    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startCamera()
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Camera and location permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)

        // Initialize views
        previewView = findViewById(R.id.previewView)
        skyOverlayView = findViewById(R.id.skyOverlayView)
        backButton = findViewById(R.id.backButton)

        astronomyViewModel = AstronomyViewModel(application)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check permissions
        if (PermissionHelper.hasCameraPermission(this) &&
            PermissionHelper.hasLocationPermission(this)) {
            startCamera()
            startLocationUpdates()
        } else {
            requestPermissionsLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }

        // Initialize the SkyOverlayView
        skyOverlayView.initialize(astronomyViewModel)

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview use case
                val preview = Preview.Builder().build()

                // Connect the preview to the PreviewView
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Select back camera as default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)

            } catch (e: Exception) {
                Timber.e(e, "Use case binding failed")
                Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {

                // Get last known location first
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                lastKnownLocation?.let {
                    astronomyViewModel.setLocation(it.latitude, it.longitude)
                }

                // Then request regular updates
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000, // 10 seconds
                    10f,    // 10 meters
                    this
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting location")
            Toast.makeText(this, "Unable to access location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        Timber.d("Location update: ${location.latitude}, ${location.longitude}")
        astronomyViewModel.setLocation(location.latitude, location.longitude)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        locationManager.removeUpdates(this)
    }
}