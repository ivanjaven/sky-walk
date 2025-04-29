package com.example.skywalk.features.ar.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.min

class SkyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects for drawing
    private val paint = Paint().apply {
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 36f
        color = Color.WHITE
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }

    // State tracking
    private var currentOrientation: DeviceOrientation? = null
    private var deviceLookVector: Vector3? = null
    private var deviceUpVector: Vector3? = null
    private var celestialObjects: List<CelestialObject> = emptyList()
    private val objectBitmaps = mutableMapOf<Int, Bitmap>()
    private var viewModel: AstronomyViewModel? = null
    private var viewWidth = 0
    private var viewHeight = 0

    // Track last screen positions of objects
    private val lastScreenPositions = mutableMapOf<String, Pair<Float, Float>>()

    // Debug mode disabled for clean render
    private val DEBUG_MODE = false

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
        if (viewWidth == 0 || viewHeight == 0) return

        objects.forEach { obj ->
            if (!objectBitmaps.containsKey(obj.imageResourceId)) {
                try {
                    // Load the bitmap
                    val originalBitmap = BitmapFactory.decodeResource(resources, obj.imageResourceId)

                    // Scale the image based on object type - now 3x larger
                    val scaleFactor = when (obj.name) {
                        "Sun" -> 0.45f  // 3x larger than before
                        "Moon" -> 0.36f  // 3x larger than before
                        "Jupiter" -> 0.24f  // 3x larger than before
                        "Saturn" -> 0.24f  // 3x larger than before
                        else -> 0.18f  // 3x larger than before
                    }

                    val minDimension = min(viewWidth, viewHeight)
                    val size = (minDimension * scaleFactor).toInt().coerceAtLeast(120)

                    val scaledBitmap = Bitmap.createScaledBitmap(
                        originalBitmap,
                        size,
                        size,
                        true
                    )

                    objectBitmaps[obj.imageResourceId] = scaledBitmap
                    originalBitmap.recycle()

                    Timber.d("Created bitmap for ${obj.name} with size $size")
                } catch (e: Exception) {
                    Timber.e(e, "Error loading bitmap for ${obj.name}")
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // Clear and reload bitmaps at the new size
        objectBitmaps.values.forEach { it.recycle() }
        objectBitmaps.clear()
        celestialObjects.let { prepareObjectBitmaps(it) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Get current orientation vectors
        val lookVector = deviceLookVector
        val upVector = deviceUpVector

        // Skip drawing if we don't have orientation data
        if (lookVector == null || upVector == null) {
            return
        }

        // Get field of view
        val fieldOfView = viewModel?.getFieldOfViewRadians() ?: (Math.PI / 3).toFloat()

        // Clear objects not visible in this frame
        val visibleObjects = mutableSetOf<String>()

        // Sort objects by declination so that objects higher in the sky are drawn on top
        val sortedObjects = celestialObjects.sortedBy { it.skyCoordinate.declination }

        // Draw all celestial objects
        for (obj in sortedObjects) {
            // Calculate screen position
            val screenCoordinates = AstronomyUtils.celestialToScreenCoordinates(
                obj.skyCoordinate,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            )

            if (screenCoordinates != null) {
                val (screenX, screenY) = screenCoordinates

                // Store current position
                lastScreenPositions[obj.name] = Pair(screenX, screenY)
                visibleObjects.add(obj.name)

                // Draw the object
                objectBitmaps[obj.imageResourceId]?.let { bitmap ->
                    canvas.drawBitmap(
                        bitmap,
                        screenX - bitmap.width / 2f,
                        screenY - bitmap.height / 2f,
                        paint
                    )

                    // Determine text size based on object importance - also 3x larger
                    textPaint.textSize = when (obj.name) {
                        "Sun", "Moon" -> 48f
                        "Jupiter", "Saturn" -> 42f
                        else -> 36f
                    }

                    // Draw the object name
                    canvas.drawText(
                        obj.name,
                        screenX,
                        screenY + (bitmap.height / 2) + textPaint.textSize,
                        textPaint
                    )
                }
            } else {
                // Object is not on screen
                lastScreenPositions.remove(obj.name)
            }
        }

        // Remove objects that are no longer visible
        lastScreenPositions.keys.toList().forEach { name ->
            if (!visibleObjects.contains(name)) {
                lastScreenPositions.remove(name)
            }
        }
    }

    /**
     * Captures the current view as a bitmap image
     * @return The captured bitmap or null if it couldn't be captured
     */
    fun captureView(): Bitmap? {
        if (width <= 0 || height <= 0) {
            return null
        }

        // Create a bitmap with the view's dimensions
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Create a canvas with the bitmap
        val canvas = Canvas(bitmap)

        // Draw the view into the canvas
        draw(canvas)

        return bitmap
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up bitmaps to prevent memory leaks
        objectBitmaps.values.forEach { it.recycle() }
        objectBitmaps.clear()
    }
}