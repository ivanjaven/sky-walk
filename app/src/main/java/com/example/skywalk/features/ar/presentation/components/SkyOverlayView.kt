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
import kotlin.math.abs
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
        textSize = 32f
        color = Color.WHITE
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }

    private val debugPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = Color.GREEN
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
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

    // Debug settings
    private val DEBUG_MODE = true

    // Frame rate tracking
    private var frameCount = 0
    private var lastFpsUpdateTime = System.currentTimeMillis()
    private var fps = 0

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

                    // Scale the image based on object type
                    val scaleFactor = when (obj.name) {
                        "Sun" -> 0.15f
                        "Moon" -> 0.12f
                        "Jupiter" -> 0.08f
                        "Saturn" -> 0.08f
                        else -> 0.06f
                    }

                    val minDimension = min(viewWidth, viewHeight)
                    val size = (minDimension * scaleFactor).toInt().coerceAtLeast(40)

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

        // Update FPS counter
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsUpdateTime > 1000) {
            fps = frameCount
            frameCount = 0
            lastFpsUpdateTime = now
        }

        // Get current orientation vectors
        val lookVector = deviceLookVector
        val upVector = deviceUpVector

        if (lookVector == null || upVector == null) {
            drawDebugInfo(canvas, "Waiting for orientation data...")
            return
        }

        // Draw reference grid for debugging if needed
        if (DEBUG_MODE) {
            drawDebugGrid(canvas)
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

                // Check if object moved significantly
                val lastPos = lastScreenPositions[obj.name]
                if (lastPos != null) {
                    val deltaX = screenX - lastPos.first
                    val deltaY = screenY - lastPos.second

                    if (DEBUG_MODE && (abs(deltaX) > 2 || abs(deltaY) > 2)) {
                        Timber.d("${obj.name} moved by $deltaX, $deltaY pixels")
                    }
                }

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

                    // Determine text size based on object importance
                    textPaint.textSize = when (obj.name) {
                        "Sun", "Moon" -> 36f
                        "Jupiter", "Saturn" -> 30f
                        else -> 28f
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

        // Always draw debug info
        drawDebugInfo(canvas, "Objects visible: ${visibleObjects.size}/${celestialObjects.size}")
    }

    private fun drawDebugGrid(canvas: Canvas) {
        // Draw a reference grid
        val gridPaint = Paint().apply {
            color = Color.argb(50, 255, 255, 255)
            strokeWidth = 1f
        }

        // Vertical lines
        for (x in 0..10) {
            val xPos = viewWidth * x / 10f
            canvas.drawLine(xPos, 0f, xPos, viewHeight.toFloat(), gridPaint)
        }

        // Horizontal lines
        for (y in 0..10) {
            val yPos = viewHeight * y / 10f
            canvas.drawLine(0f, yPos, viewWidth.toFloat(), yPos, gridPaint)
        }

        // Draw center crosshair
        val crosshairPaint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 2f
        }

        canvas.drawLine((viewWidth/2 - 20).toFloat(), (viewHeight/2).toFloat(),
            (viewWidth/2 + 20).toFloat(), (viewHeight/2).toFloat(), crosshairPaint)
        canvas.drawLine((viewWidth/2).toFloat(), (viewHeight/2 - 20).toFloat(),
            (viewWidth/2).toFloat(), (viewHeight/2 + 20).toFloat(), crosshairPaint)
    }

    private fun drawDebugInfo(canvas: Canvas, statusMessage: String) {
        if (!DEBUG_MODE) return

        val orientation = currentOrientation
        val lookVec = deviceLookVector

        val lines = mutableListOf(
            "FPS: $fps"
        )

        if (statusMessage.isNotEmpty()) {
            lines.add(statusMessage)
        }

        if (orientation != null) {
            lines.add("Az: ${orientation.azimuth.toInt()}° Pitch: ${orientation.pitch.toInt()}° " +
                    "Roll: ${orientation.roll.toInt()}°")
        }

        if (lookVec != null) {
            lines.add("Look: (${String.format("%.2f", lookVec.x)}, " +
                    "${String.format("%.2f", lookVec.y)}, " +
                    "${String.format("%.2f", lookVec.z)})")
        }

        // Draw a semi-transparent background for better readability
        val bgPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
            style = Paint.Style.FILL
        }

        canvas.drawRect(10f, 10f, 600f, 40f + lines.size * 30f, bgPaint)

        // Draw debug text
        for ((i, line) in lines.withIndex()) {
            canvas.drawText(line, 20f, 40f + i * 30f, debugPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up bitmaps to prevent memory leaks
        objectBitmaps.values.forEach { it.recycle() }
        objectBitmaps.clear()
    }
}