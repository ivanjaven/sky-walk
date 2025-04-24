// features/arsky/presentation/components/ArSceneViewFragment.kt
package com.example.skywalk.features.arsky.presentation.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment

class ArSceneViewFragment : ArFragment() {

    companion object {
        fun newInstance(): ArSceneViewFragment {
            return ArSceneViewFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Use the planeDiscoveryController to hide the hand animation
        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)

        // Disable the plane renderer to not see the planes
        arSceneView.planeRenderer.isEnabled = false

        return view
    }

    override fun getSessionFeatures(): MutableSet<Session.Feature> {
        return mutableSetOf(Session.Feature.SHARED_CAMERA)
    }
}