package com.example.skywalk.features.ar.presentation

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class ARActivity : ComponentActivity() {
    // View references
    private lateinit var previewView: PreviewView
    private lateinit var skyOverlayView: SkyOverlayView
    private lateinit var backButton: ImageButton
    private lateinit var captureButton: ImageButton
    // Add new constellation toggle button
    private lateinit var constellationToggleButton: ImageButton

    // Camera components
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService

    // Viewmodel
    private lateinit var astronomyViewModel: AstronomyViewModel

    // Gesture handling
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoom = 1.0f
    private val MIN_ZOOM = 0.8f
    private val MAX_ZOOM = 1.5f
    private val ZOOM_SPEED = 0.5f

    // Constellation toggle state
    private var showConstellations = true

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasCamera = permissions[Manifest.permission.CAMERA] == true

        if (hasCamera) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for AR view", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Storage permission launcher for saving images
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            captureAndSaveImage()
        } else {
            Toast.makeText(this, "Storage permission denied. Cannot save image.", Toast.LENGTH_SHORT).show()
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
        captureButton = findViewById(R.id.captureButton)
        // Initialize constellation toggle button
        constellationToggleButton = findViewById(R.id.constellationToggleButton)

        // Initialize ViewModel
        astronomyViewModel = AstronomyViewModel(application)

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

        // Set up capture button
        captureButton.setOnClickListener {
            checkStoragePermissionAndCapture()
        }

        // Set up constellation toggle button
        constellationToggleButton.setOnClickListener {
            showConstellations = !showConstellations
            astronomyViewModel.toggleConstellationVisibility()

            // Update button appearance based on state
            updateConstellationButtonAppearance()
        }

        // Check and request camera permission
        checkCameraPermission()

        // Try to get the location from intent extras
        intent.extras?.let {
            if (it.containsKey("latitude") && it.containsKey("longitude")) {
                val latitude = it.getDouble("latitude")
                val longitude = it.getDouble("longitude")
                astronomyViewModel.setLocation(latitude, longitude)
            }
        }
    }

    private fun updateConstellationButtonAppearance() {
        // Change the button appearance based on state
        if (showConstellations) {
            constellationToggleButton.alpha = 1.0f
            // Optional: change icon or background if needed
        } else {
            constellationToggleButton.alpha = 0.5f
            // Optional: change icon or background if needed
        }
    }

    private fun checkStoragePermissionAndCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, we don't need WRITE_EXTERNAL_STORAGE permission
            captureAndSaveImage()
        } else {
            // For older versions, we need to check and request the permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                captureAndSaveImage()
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun captureAndSaveImage() {
        val bitmap = skyOverlayView.captureView()
        if (bitmap != null) {
            saveImageToStorage(bitmap)
        } else {
            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToStorage(bitmap: Bitmap) {
        // Generate a file name with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "SkyWalk_$timestamp.jpg"

        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SkyWalk")
                }

                contentResolver.also { resolver ->
                    uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = uri?.let { resolver.openOutputStream(it) }
                }
            } else {
                // For older Android versions
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/SkyWalk"
                val file = File(imagesDir)
                if (!file.exists()) {
                    file.mkdirs()
                }

                val image = File(imagesDir, filename)
                outputStream = FileOutputStream(image)

                // Add the image to the gallery
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                uri = Uri.fromFile(image)
                mediaScanIntent.data = uri
                sendBroadcast(mediaScanIntent)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving image")
            Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            outputStream?.close()
        }
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
        val baseFov = 60f
        val newFov = baseFov / currentZoom

        astronomyViewModel.setFieldOfView(newFov)

        // Also update the zoom factor for star filtering
        astronomyViewModel.setZoom(currentZoom)
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

    private fun checkCameraPermission() {
        if (!PermissionHelper.hasCameraPermission(this)) {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            startCamera()
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

                Timber.d("Camera started successfully")

            } catch (e: Exception) {
                Timber.e(e, "Camera initialization failed")
                Toast.makeText(this, "Camera initialization failed: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle all touch events with our gesture detectors
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()

        // Reset UI visibility
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}