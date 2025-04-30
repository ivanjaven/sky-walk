package com.example.skywalk.features.ar.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.SkyCoordinate
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
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val altAzPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = Color.LTGRAY
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }

    private val compassPaint = Paint().apply {
        isAntiAlias = true
        textSize = 32f
        color = Color.WHITE
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val guidePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(100, 255, 255, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
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

    // Track last screen positions of objects for smoother transitions
    private val lastScreenPositions = mutableMapOf<String, Pair<Float, Float>>()

    // Compass directions for horizon line
    private val compassDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    // Debug mode flag
    private val SHOW_COMPASS = true
    private val SHOW_ALTITUDE_GUIDES = true
    private val SHOW_OBJECT_INFO = true

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

                    // Scale the image based on object type and magnitude
                    val scaleFactor = when (obj.name) {
                        "Sun" -> 0.45f
                        "Moon" -> 0.36f
                        "Jupiter" -> 0.24f
                        "Saturn" -> 0.24f
                        "Venus" -> 0.20f
                        else -> 0.18f
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
        val orientation = currentOrientation

        // Skip drawing if we don't have orientation data
        if (lookVector == null || upVector == null || orientation == null) {
            return
        }

        // Get field of view
        val fieldOfView = viewModel?.getFieldOfViewRadians() ?: (Math.PI / 3).toFloat()

        // Draw altitude guide circles if enabled
        if (SHOW_ALTITUDE_GUIDES) {
            drawAltitudeGuides(canvas, lookVector, upVector, fieldOfView)
        }

        // Draw compass directions if enabled
        if (SHOW_COMPASS) {
            drawCompassDirections(canvas, lookVector, upVector, fieldOfView)
        }

        // Clear objects not visible in this frame
        val visibleObjects = mutableSetOf<String>()

        // Sort objects by declination so that objects higher in the sky are drawn on top
        // For horizontal coordinates: declination = altitude, rightAscension = azimuth
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

                    // Determine text size based on object importance
                    textPaint.textSize = when (obj.name) {
                        "Sun", "Moon" -> 48f
                        "Jupiter", "Saturn", "Venus" -> 42f
                        else -> 36f
                    }

                    // Draw the object name
                    canvas.drawText(
                        obj.name,
                        screenX,
                        screenY + (bitmap.height / 2) + textPaint.textSize,
                        textPaint
                    )

                    // Show azimuth and altitude if enabled
                    if (SHOW_OBJECT_INFO) {
                        val objectInfo = String.format(
                            "Az: %.1f° Alt: %.1f°",
                            obj.skyCoordinate.rightAscension,
                            obj.skyCoordinate.declination
                        )

                        canvas.drawText(
                            objectInfo,
                            screenX,
                            screenY + (bitmap.height / 2) + textPaint.textSize + 30f,
                            altAzPaint
                        )
                    }
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

        // Draw current azimuth (compass direction)
        drawAzimuthIndicator(canvas, orientation.azimuth)
    }

    private fun drawAltitudeGuides(canvas: Canvas, lookVector: Vector3, upVector: Vector3, fieldOfView: Float) {
        // Draw altitude circles at 0° (horizon), 30°, 60°
        val altitudes = listOf(0f, 30f, 60f)

        for (altitude in altitudes) {
            val path = Path()
            var firstPoint = true

            // Draw points around a circle of constant altitude
            for (azimuth in 0 until 360 step 10) {
                val coord = SkyCoordinate(azimuth.toFloat(), altitude)

                val screenCoord = AstronomyUtils.celestialToScreenCoordinates(
                    coord,
                    lookVector,
                    upVector,
                    fieldOfView,
                    viewWidth,
                    viewHeight
                ) ?: continue

                if (firstPoint) {
                    path.moveTo(screenCoord.first, screenCoord.second)
                    firstPoint = false
                } else {
                    path.lineTo(screenCoord.first, screenCoord.second)
                }
            }

            // Draw the path
            canvas.drawPath(path, guidePaint)

            // Label the altitude circle
            val labelCoord = SkyCoordinate(currentOrientation?.azimuth ?: 0f, altitude)
            val screenLabelPos = AstronomyUtils.celestialToScreenCoordinates(
                labelCoord,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            )

            screenLabelPos?.let {
                val label = if (altitude == 0f) "Horizon" else "${altitude.toInt()}°"
                altAzPaint.color = Color.YELLOW
                canvas.drawText(label, it.first, it.second, altAzPaint)
                altAzPaint.color = Color.LTGRAY
            }
        }
    }

    private fun drawCompassDirections(canvas: Canvas, lookVector: Vector3, upVector: Vector3, fieldOfView: Float) {
        // Draw compass directions along the horizon
        for (i in 0 until 8) {
            val azimuth = i * 45f  // N, NE, E, SE, S, SW, W, NW
            val coord = SkyCoordinate(azimuth, 0f)  // At the horizon

            val screenCoord = AstronomyUtils.celestialToScreenCoordinates(
                coord,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            ) ?: continue

            // Draw the direction indicator
            compassPaint.color = when (compassDirections[i]) {
                "N" -> Color.RED
                "S" -> Color.BLUE
                "E", "W" -> Color.GREEN
                else -> Color.WHITE
            }

            canvas.drawText(
                compassDirections[i],
                screenCoord.first,
                screenCoord.second + 40f,
                compassPaint
            )
        }
    }

    private fun drawAzimuthIndicator(canvas: Canvas, azimuth: Float) {
        // Draw current azimuth at the bottom of the screen
        compassPaint.textSize = 48f
        compassPaint.color = Color.WHITE

        val compassDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")

        // Calculate nearest direction
        val sector = ((azimuth + 22.5f) % 360f) / 45f
        val index = sector.toInt()

        val direction = compassDirections[index]
        val azimuthText = String.format("%s  %.1f°", direction, azimuth)

        canvas.drawText(
            azimuthText,
            viewWidth / 2f,
            viewHeight - 50f,
            compassPaint
        )
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