// features/ar/presentation/components/SkyOverlayView.kt

package com.example.skywalk.features.ar.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.Vector3
import com.example.skywalk.features.ar.presentation.viewmodel.AstronomyViewModel
import com.example.skywalk.features.ar.utils.AstronomyUtils
import timber.log.Timber

class SkyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
    }

    private var currentOrientation: DeviceOrientation? = null
    private var deviceLookVector: Vector3? = null
    private var deviceUpVector: Vector3? = null
    private var celestialObjects: List<CelestialObject> = emptyList()
    private val objectBitmaps = mutableMapOf<Int, Bitmap>()
    private var viewModel: AstronomyViewModel? = null
    private var viewWidth = 0
    private var viewHeight = 0

    fun initialize(viewModel: AstronomyViewModel) {
        this.viewModel = viewModel

        findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
            viewModel.deviceOrientation.observe(lifecycleOwner) { orientation ->
                currentOrientation = orientation
                invalidate()
            }

            viewModel.deviceOrientationVector.observe(lifecycleOwner) { vector ->
                deviceLookVector = vector
                invalidate()
            }

            viewModel.deviceUpVector.observe(lifecycleOwner) { vector ->
                deviceUpVector = vector
                invalidate()
            }

            viewModel.celestialObjects.observe(lifecycleOwner) { objects ->
                celestialObjects = objects
                prepareObjectBitmaps(objects)
                invalidate()
            }
        }
    }

    private fun prepareObjectBitmaps(objects: List<CelestialObject>) {
        objects.forEach { obj ->
            if (!objectBitmaps.containsKey(obj.imageResourceId)) {
                try {
                    // Load and scale the bitmap
                    val originalBitmap = BitmapFactory.decodeResource(resources, obj.imageResourceId)

                    // Scale based on magnitude - brighter objects appear larger
                    val scaleFactor = when {
                        obj.magnitude < -10 -> 0.5f  // Sun
                        obj.magnitude < -4 -> 0.3f   // Very bright (Venus)
                        obj.magnitude < 0 -> 0.25f   // Bright (Jupiter)
                        obj.magnitude < 2 -> 0.2f    // Medium (Mars)
                        else -> 0.15f                // Dim
                    }

                    val size = (Math.min(viewWidth, viewHeight) * scaleFactor).toInt().coerceAtLeast(50)

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        originalBitmap,
                        size,
                        size,
                        true
                    )
                    objectBitmaps[obj.imageResourceId] = scaledBitmap
                    originalBitmap.recycle()
                } catch (e: Exception) {
                    Timber.e(e, "Error loading bitmap for ${obj.name}")
                }
            }
        }
    }

    fun updateOrientation(orientation: DeviceOrientation) {
        currentOrientation = orientation
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // Reload bitmaps at the new size
        objectBitmaps.clear()
        celestialObjects.let { prepareObjectBitmaps(it) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val lookVector = deviceLookVector ?: return
        val upVector = deviceUpVector ?: return
        val fieldOfView = viewModel?.getFieldOfViewRadians() ?: (60 * Math.PI / 180).toFloat()

        // Draw each celestial object
        celestialObjects.forEach { obj ->
            // Calculate screen coordinates using proper astronomical positioning
            val screenCoordinates = AstronomyUtils.celestialToScreenCoordinates(
                obj.skyCoordinate,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            )

            // Only draw if the object would be in the field of view
            if (screenCoordinates != null) {
                val screenX = screenCoordinates.first
                val screenY = screenCoordinates.second

                if (screenX in 0f..viewWidth.toFloat() &&
                    screenY in 0f..viewHeight.toFloat()) {

                    // Draw the object image
                    objectBitmaps[obj.imageResourceId]?.let { bitmap ->
                        canvas.drawBitmap(
                            bitmap,
                            screenX - bitmap.width / 2,
                            screenY - bitmap.height / 2,
                            paint
                        )
                    }

                    // Draw the object name
                    paint.color = android.graphics.Color.WHITE
                    canvas.drawText(
                        obj.name,
                        screenX,
                        screenY + (objectBitmaps[obj.imageResourceId]?.height ?: 0) / 2 + 40, // Position below the object
                        paint
                    )
                }
            }
        }

        // Debug info - display current azimuth, pitch, roll
        currentOrientation?.let { orientation ->
            paint.color = android.graphics.Color.GREEN
            paint.textSize = 30f
            canvas.drawText(
                "Azimuth: ${orientation.azimuth.toInt()}° Pitch: ${orientation.pitch.toInt()}° Roll: ${orientation.roll.toInt()}°",
                20f,
                viewHeight - 50f,
                paint
            )
        }
    }
}