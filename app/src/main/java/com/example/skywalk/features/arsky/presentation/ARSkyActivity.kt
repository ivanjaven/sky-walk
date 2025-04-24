// features/arsky/presentation/ARSkyActivity.kt
package com.example.skywalk.features.arsky.presentation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.skywalk.R
import com.example.skywalk.features.arsky.presentation.viewmodel.ARSkyViewModel
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.cos
import kotlin.math.sin

class ARSkyActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var viewModel: ARSkyViewModel
    private var celestialNodes = mutableListOf<Node>()
    private var hasPlacedObjects = false
    private var frameCount = 0

    // UI components
    private lateinit var objectNameTextView: TextView
    private lateinit var objectTypeTextView: TextView
    private lateinit var objectDescriptionTextView: TextView
    private lateinit var backButton: Button
    private lateinit var infoPanel: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_sky)
        Timber.d("ARSkyActivity onCreate")

        // Initialize UI components
        objectNameTextView = findViewById(R.id.object_name)
        objectTypeTextView = findViewById(R.id.object_type)
        objectDescriptionTextView = findViewById(R.id.object_description)
        backButton = findViewById(R.id.back_button)
        infoPanel = findViewById(R.id.info_panel)

        // Initially hide the info panel
        infoPanel.visibility = View.GONE

        // Initialize AR Fragment
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[ARSkyViewModel::class.java]

        // Set up back button
        backButton.setOnClickListener {
            finish()
        }

        // Set up frame listener
        setUpFrameListener()

        // Observe ViewModel
        observeViewModel()

        // Load celestial objects
        viewModel.loadCelestialObjects()
    }

    private fun setUpFrameListener() {
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            val frame = arFragment.arSceneView.arFrame ?: return@addOnUpdateListener
            val camera = frame.camera

            if (camera.trackingState == TrackingState.TRACKING) {
                // We have good tracking
                frameCount++

                // Wait a bit before placing the objects (give AR time to initialize)
                if (!hasPlacedObjects && viewModel.celestialObjects.value.isNotEmpty() && frameCount > 30) {
                    placeCelestialObjectsInSky()
                    hasPlacedObjects = true
                }

                // Update positions of celestial objects based on device orientation
                if (hasPlacedObjects) {
                    updateCelestialPositions()
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.celestialObjects.collectLatest { celestialObjects ->
                Timber.d("Received ${celestialObjects.size} celestial objects")
                if (celestialObjects.isNotEmpty()) {
                    updateUiWithCelestialObject(celestialObjects.first())
                }
            }
        }

        lifecycleScope.launch {
            viewModel.selectedCelestialObject.collectLatest { celestialObject ->
                celestialObject?.let {
                    updateUiWithCelestialObject(it)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                if (isLoading) {
                    Toast.makeText(this@ARSkyActivity, "Loading AR content...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUiWithCelestialObject(celestialObject: CelestialObject?) {
        celestialObject?.let {
            objectNameTextView.text = it.name
            objectTypeTextView.text = "Type: ${it.type.name.replace("_", " ")}"
            objectDescriptionTextView.text = it.summary.ifEmpty {
                if (it.description.length > 150) {
                    it.description.take(150) + "..."
                } else {
                    it.description
                }
            }
        }
    }

    private fun placeCelestialObjectsInSky() {
        val celestialObjects = viewModel.celestialObjects.value
        if (celestialObjects.isEmpty()) return

        try {
            Timber.d("Placing ${celestialObjects.size} celestial objects in sky")

            // Get the camera
            val frame = arFragment.arSceneView.arFrame
            val camera = frame?.camera ?: return

            // Create celestial objects and place them in a dome around the user
            for (i in celestialObjects.indices) {
                val celestialObject = celestialObjects[i]

                // Calculate position in a dome pattern
                // We'll place the objects in different parts of the sky
                val angle = i * (360.0 / celestialObjects.size)
                val elevation = 20 + (i % 3) * 20 // Vary elevation between 20-60 degrees
                val radius = 10f // Distance from user

                val x = radius * cos(Math.toRadians(angle)) * cos(Math.toRadians(elevation.toDouble()))
                val y = radius * sin(Math.toRadians(elevation.toDouble()))
                val z = radius * sin(Math.toRadians(angle)) * cos(Math.toRadians(elevation.toDouble()))

                // Create a view for the celestial object
                val viewRenderableFuture = ViewRenderable.builder()
                    .setView(this, R.layout.celestial_object_card)
                    .build()

                viewRenderableFuture.thenAccept { renderable ->
                    // Get the views from the layout
                    val view = renderable.view
                    val nameTextView = view.findViewById<TextView>(R.id.celestial_name)
                    val imageView = view.findViewById<ImageView>(R.id.celestial_image)

                    // Set the name
                    nameTextView.text = celestialObject.name

                    // Load the image
                    val imageUrl = if (celestialObject.imageUrl.isNotEmpty()) {
                        celestialObject.imageUrl
                    } else {
                        celestialObject.thumbnailUrl
                    }

                    if (imageUrl.isNotEmpty()) {
                        // Use Glide to load the image
                        Glide.with(this)
                            .load(imageUrl)
                            .into(imageView)
                    }

                    // Create a node
                    val node = Node()
                    node.renderable = renderable
                    node.setParent(arFragment.arSceneView.scene)

                    // Set position
                    node.worldPosition = Vector3(x.toFloat(), y.toFloat(), z.toFloat())

                    // Add to our list
                    celestialNodes.add(node)

                    // Store the celestial object as a tag for use in the tap listener
                    node.name = i.toString()

                    // Make object face the user (billboard)
                    val cameraPosition = camera.pose.translation
                    val direction = Vector3(
                        cameraPosition[0] - x.toFloat(),
                        cameraPosition[1] - y.toFloat(),
                        cameraPosition[2] - z.toFloat()
                    )
                    node.worldRotation = Quaternion.lookRotation(direction, Vector3.up())

                    // Set tap listener
                    node.setOnTapListener { hitTestResult, motionEvent ->
                        Timber.d("Tapped on celestial object: ${celestialObject.name}")
                        viewModel.selectCelestialObject(celestialObject)
                        infoPanel.visibility = View.VISIBLE
                    }
                }.exceptionally { throwable ->
                    Timber.e(throwable, "Error creating renderable for ${celestialObject.name}")
                    null
                }
            }

            Toast.makeText(
                this,
                "Celestial objects placed. Tap on objects to see details.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Timber.e(e, "Error placing celestial objects in sky")
            Toast.makeText(
                this,
                "Error creating AR experience: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateCelestialPositions() {
        val camera = arFragment.arSceneView.scene.camera

        // Make all nodes face the camera (billboard effect)
        for (node in celestialNodes) {
            // Create a direction from the node to the camera
            val direction = Vector3.subtract(camera.worldPosition, node.worldPosition)

            // Set the node rotation to face the camera
            node.worldRotation = Quaternion.lookRotation(direction, Vector3.up())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        for (node in celestialNodes) {
            arFragment.arSceneView.scene.removeChild(node)
        }
        celestialNodes.clear()
    }
}