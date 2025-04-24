package com.example.skywalk.features.arsky.presentation

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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

class ARSkyActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var viewModel: ARSkyViewModel
    private var anchorNode: AnchorNode? = null
    private var objectNode: Node? = null
    private var hasPlacedObject = false
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

        // Initialize AR Fragment
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment

        // Configure AR scene
        arFragment.planeDiscoveryController.hide()
        arFragment.planeDiscoveryController.setInstructionView(null)
        arFragment.arSceneView.planeRenderer.isEnabled = false

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

        // Load celestial object
        viewModel.loadFirstCelestialObject()
    }

    private fun setUpFrameListener() {
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            val frame = arFragment.arSceneView.arFrame ?: return@addOnUpdateListener
            val camera = frame.camera

            if (camera.trackingState == TrackingState.TRACKING) {
                // We have good tracking
                frameCount++

                // Wait a bit before placing the object (give AR time to initialize)
                if (!hasPlacedObject && viewModel.selectedCelestialObject.value != null && frameCount > 30) {
                    placeCelestialObjectInSky()
                    hasPlacedObject = true
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.selectedCelestialObject.collectLatest { celestialObject ->
                Timber.d("Celestial object received: ${celestialObject?.name}")
                updateUiWithCelestialObject(celestialObject)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                // Show/hide loading indicator
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

    private fun placeCelestialObjectInSky() {
        val celestialObject = viewModel.selectedCelestialObject.value ?: return

        try {
            Timber.d("Placing celestial object in sky: ${celestialObject.name}")

            // Get the camera pose
            val frame = arFragment.arSceneView.arFrame
            val camera = frame?.camera ?: return

            if (camera.trackingState != TrackingState.TRACKING) return

            val cameraPose = camera.pose

            // Calculate position 2 meters in front of camera
            val forward = cameraPose.zAxis
            val tx = cameraPose.tx() - forward[0] * 2f
            val ty = cameraPose.ty() - forward[1] * 2f + 0.5f // Slightly above eye level
            val tz = cameraPose.tz() - forward[2] * 2f

            // Create anchor pose
            val anchorPose = Pose.makeTranslation(tx, ty, tz)

            // Create the anchor
            val session = arFragment.arSceneView.session ?: return
            val anchor = session.createAnchor(anchorPose)

            // Create an anchor node
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)
            this.anchorNode = anchorNode

            // Create a view for the celestial object
            val viewRenderableFuture = ViewRenderable.builder()
                .setView(this, R.layout.celestial_object_card)
                .build()

            viewRenderableFuture.thenAccept { renderable ->
                // Get the celestial name TextView from the layout
                val view = renderable.view
                val nameTextView = view.findViewById<TextView>(R.id.celestial_name)
                nameTextView.text = celestialObject.name

                // Create a node with the renderable
                val node = Node()
                node.renderable = renderable
                node.setParent(anchorNode)

                // Set position and scale
                val scale = 0.5f
                node.localScale = Vector3(scale, scale, scale)

                // Make the object face the camera
                val cameraPos = Vector3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())
                val objPos = Vector3(tx, ty, tz)

                val direction = Vector3.subtract(cameraPos, objPos)
                val lookRotation = Quaternion.lookRotation(direction, Vector3.up())

                node.localRotation = lookRotation

                // Set up tap listener
                node.setOnTapListener { _, _ ->
                    toggleInfoPanel()
                }

                this.objectNode = node

                Toast.makeText(
                    this,
                    "Viewing ${celestialObject.name} in AR. Tap to see details.",
                    Toast.LENGTH_SHORT
                ).show()
            }.exceptionally { throwable ->
                Timber.e(throwable, "Error creating renderable")
                Toast.makeText(
                    this,
                    "Error displaying AR content: ${throwable.message}",
                    Toast.LENGTH_SHORT
                ).show()
                null
            }

        } catch (e: Exception) {
            Timber.e(e, "Error placing celestial object in sky")
            Toast.makeText(
                this,
                "Error creating AR experience: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun toggleInfoPanel() {
        if (infoPanel.visibility == View.VISIBLE) {
            infoPanel.visibility = View.GONE
        } else {
            infoPanel.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        Timber.d("ARSkyActivity onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        anchorNode?.anchor?.detach()
        anchorNode = null
        objectNode = null
    }
}