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
import com.google.ar.core.Pose
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
    private var anchorNode: AnchorNode? = null
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

                // Keep objects facing the camera but maintain their positions
                updateObjectOrientations()
            }
        }
    }

    private fun updateObjectOrientations() {
        val camera = arFragment.arSceneView.scene.camera

        // Make all celestial objects face the camera (billboard effect)
        // without changing their positions
        for (node in celestialNodes) {
            try {
                // Get the direction from the node to the camera
                val nodeWorldPosition = node.worldPosition
                val directionToCamera = Vector3.subtract(camera.worldPosition, nodeWorldPosition)

                // Keep the y-component of the node's up vector to avoid tilting
                // This keeps text upright while still facing camera
                val up = Vector3(0f, 1f, 0f)

                // Apply the look rotation
                node.worldRotation = Quaternion.lookRotation(directionToCamera, up)
            } catch (e: Exception) {
                Timber.e(e, "Error updating node orientation: ${e.message}")
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

            // Get the camera position
            val frame = arFragment.arSceneView.arFrame
            val camera = frame?.camera ?: return
            val cameraPose = camera.pose

            // Create a more stable anchor slightly in front of the camera
            // This helps prevent the anchor from being lost
            val forward = cameraPose.zAxis
            val anchorPose = Pose.makeTranslation(
                cameraPose.tx() - forward[0] * 0.5f,
                cameraPose.ty() - forward[1] * 0.5f,
                cameraPose.tz() - forward[2] * 0.5f
            )

            // Create and track our anchor node
            val anchor = arFragment.arSceneView.session?.createAnchor(anchorPose)
            anchorNode = AnchorNode(anchor).apply {
                isEnabled = true
                setParent(arFragment.arSceneView.scene)
            }

            // Create celestial objects and place them in a dome around the user
            for (i in celestialObjects.indices) {
                val celestialObject = celestialObjects[i]

                // Calculate position in a dome pattern
                val angle = i * (360.0 / celestialObjects.size)
                val elevation = 20 + (i % 3) * 20 // Vary elevation between 20-60 degrees
                val radius = 7f // Reduced distance from user for better visibility

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

                    // Create a node and attach it to the anchor node
                    val node = Node().apply {
                        this.renderable = renderable
                        setParent(anchorNode)
                        localPosition = Vector3(x.toFloat(), y.toFloat(), z.toFloat())
                        name = i.toString()

                        // Initial orientation to face camera - will be updated in updateObjectOrientations()
                        val cameraPosition = camera.pose.translation
                        val direction = Vector3(
                            cameraPosition[0] - x.toFloat(),
                            0f,  // Keep Y at 0 to prevent tilting
                            cameraPosition[2] - z.toFloat()
                        )
                        localRotation = Quaternion.lookRotation(direction, Vector3.up())

                        // Make the node persistent
                        isEnabled = true
                    }

                    // Add to our list
                    celestialNodes.add(node)

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

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        for (node in celestialNodes) {
            // Remove from parent
            node.parent?.removeChild(node)
        }
        celestialNodes.clear()

        // Clean up anchor node
        anchorNode?.anchor?.detach()
        anchorNode?.setParent(null)
        anchorNode = null
    }
}