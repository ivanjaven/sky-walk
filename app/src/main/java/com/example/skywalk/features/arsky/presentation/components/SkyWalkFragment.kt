package com.example.skywalk.features.arsky.presentation.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment
import timber.log.Timber

class SkyWalkArFragment : ArFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = try {
            Timber.d("Creating ArFragment view")
            super.onCreateView(inflater, container, savedInstanceState)
        } catch (e: Exception) {
            Timber.e(e, "Error creating ArFragment view")
            context?.let {
                Toast.makeText(it, "AR not supported on this device", Toast.LENGTH_LONG).show()
            }
            // Return a plain view if AR is not supported
            View(context)
        }

        try {
            // Use the planeDiscoveryController to hide the hand animation
            planeDiscoveryController.hide()
            planeDiscoveryController.setInstructionView(null)

            // Disable the plane renderer to not see the planes
            arSceneView.planeRenderer.isEnabled = false

            Timber.d("ArFragment view setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Error configuring ArFragment view")
        }

        return view
    }

    override fun getSessionConfiguration(session: Session): Config {
        val config = try {
            super.getSessionConfiguration(session)
        } catch (e: Exception) {
            Timber.e(e, "Error getting base session configuration")
            Config(session)
        }

        try {
            // Configure to use the back camera
            config.focusMode = Config.FocusMode.AUTO

            // We don't need plane detection for sky observations
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED

            // We need light estimation for better rendering
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

            Timber.d("Session configuration completed")
        } catch (e: Exception) {
            Timber.e(e, "Error configuring AR session")
        }

        return config
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            Timber.d("ArFragment view created")
            super.onViewCreated(view, savedInstanceState)
        } catch (e: Exception) {
            Timber.e(e, "Error in onViewCreated: ${e.message}")
        }
    }
}