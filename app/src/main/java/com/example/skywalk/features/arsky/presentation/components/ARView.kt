package com.example.skywalk.features.arsky.presentation.components

import android.content.Context
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.skywalk.R
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import coil.load
import timber.log.Timber

@Composable
fun ARView(
    celestialObject: CelestialObject?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var arView by remember { mutableStateOf<ArSceneView?>(null) }
    var hasPlacedObject by remember { mutableStateOf(false) }
    var frameCount by remember { mutableStateOf(0) }

    DisposableEffect(celestialObject) {
        // Reset placement when celestial object changes
        hasPlacedObject = false
        frameCount = 0
        onDispose {}
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Note: This is a simplified view for the compose preview
            // In a real implementation, this would come from the AR fragment in the activity
            ArSceneView(context).apply {
                arView = this
            }
        },
        update = { view ->
            // In a real implementation, this update block would contain our rendering logic
            // or would synchronize with the AR fragment in the activity

            // For demonstration purposes, we'll log that we're updating
            Timber.d("ARView update called with celestial object: ${celestialObject?.name}")
        }
    )
}

@Composable
fun ARPlaceholder(
    celestialObject: CelestialObject?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // This is a non-AR placeholder composable to show in previews
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP

                if (celestialObject != null && celestialObject.imageUrl.isNotEmpty()) {
                    load(celestialObject.imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.placeholder_celestial)
                    }
                } else {
                    setImageResource(R.drawable.placeholder_celestial)
                }
            }
        },
        update = { view ->
            if (celestialObject != null && celestialObject.imageUrl.isNotEmpty()) {
                (view as ImageView).load(celestialObject.imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_celestial)
                }
            }
        }
    )
}