package com.example.skywalk.features.ar.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.Vector3
import com.example.skywalk.features.ar.presentation.viewmodel.AstronomyViewModel
import com.example.skywalk.features.ar.utils.AstronomyUtils
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

class SkyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Main paint for drawing objects
    private val paint = Paint().apply {
        isAntiAlias = true
    }

    // Paint for text with shadow for better visibility
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = Color.WHITE
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }

    // Paint for debug info
    private val debugPaint = Paint().apply {
        isAntiAlias = true
        textSize = 24f
        color = Color.GREEN
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    private var currentOrientation: DeviceOrientation? = null
    private var deviceLookVector: Vector3? = null
    private var deviceUpVector: Vector3? = null
    private var celestialObjects: List<CelestialObject> = emptyList()
    private val objectBitmaps = mutableMapOf<Int, Bitmap>()
    private var viewModel: AstronomyViewModel? = null
    private var viewWidth = 0
    private var viewHeight = 0

    // Debug settings - ENABLE THESE FOR TROUBLESHOOTING
    private val DEBUG_MODE = true
    private val FORCE_SHOW_ALL_OBJECTS = true  // Force all objects to show regardless of position

    // Counters for debug info
    private var frameCount = 0
    private var objectsDrawnCount = 0
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
                Timber.d("Received ${objects.size} celestial objects")
                for (obj in objects) {
                    Timber.d("Object: ${obj.name}, RA: ${obj.skyCoordinate.rightAscension}, " +
                            "Dec: ${obj.skyCoordinate.declination}")
                }
                prepareObjectBitmaps(objects)
                invalidate()
            }
        }
    }

    private fun prepareObjectBitmaps(objects: List<CelestialObject>) {
        if (viewWidth == 0 || viewHeight == 0) return  // Wait until we have dimensions

        objects.forEach { obj ->
            if (!objectBitmaps.containsKey(obj.imageResourceId)) {
                try {
                    // Load the bitmap
                    val originalBitmap = BitmapFactory.decodeResource(resources, obj.imageResourceId)

                    // Make objects larger for visibility during debugging
                    val scaleFactor = when (obj.name) {
                        "Sun" -> 0.15f
                        "Moon" -> 0.12f
                        else -> 0.08f
                    }

                    val minDimension = min(viewWidth, viewHeight)
                    val size = (minDimension * scaleFactor).toInt().coerceAtLeast(48)

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

        Timber.d("SkyOverlayView size changed to $w x $h")
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

        val lookVector = deviceLookVector
        val upVector = deviceUpVector

        // Reset counter for this frame
        objectsDrawnCount = 0

        if (lookVector == null || upVector == null) {
            drawDebugInfo(canvas, "No orientation data available")
            return
        }

        // Draw a reference grid for debugging
        if (DEBUG_MODE) {
            drawDebugGrid(canvas)
        }

        // Get field of view - use a wider FOV for testing if objects aren't visible
        val fieldOfView = (viewModel?.getFieldOfViewRadians() ?: (Math.PI / 3).toFloat()) * 1.5f

        for (obj in celestialObjects) {
            // Let's try to always draw the Sun and Moon for debugging
            var screenX = viewWidth / 2f
            var screenY = viewHeight / 2f
            var forceShow = FORCE_SHOW_ALL_OBJECTS

            val screenCoordinates = AstronomyUtils.celestialToScreenCoordinates(
                obj.skyCoordinate,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            )

            if (screenCoordinates != null) {
                screenX = screenCoordinates.first
                screenY = screenCoordinates.second
                forceShow = true

                Timber.d("Object ${obj.name} at screen position ($screenX, $screenY)")
            } else {
                Timber.d("Object ${obj.name} is outside field of view")
            }

            // Draw the object if it's in view or we're forcing all objects to show
            if (forceShow) {
                objectBitmaps[obj.imageResourceId]?.let { bitmap ->
                    // Ensure object is on screen for debugging
                    if (!FORCE_SHOW_ALL_OBJECTS) {
                        if (screenX < -bitmap.width || screenX > viewWidth + bitmap.width ||
                            screenY < -bitmap.height || screenY > viewHeight + bitmap.height) {
                            return@let
                        }
                    }

                    // Draw the object
                    canvas.drawBitmap(
                        bitmap,
                        screenX - bitmap.width / 2f,
                        screenY - bitmap.height / 2f,
                        paint
                    )

                    // Draw the name
                    textPaint.textSize = when (obj.name) {
                        "Sun" -> 40f
                        "Moon" -> 36f
                        else -> 30f
                    }

                    canvas.drawText(
                        obj.name,
                        screenX,
                        screenY + bitmap.height / 2 + 40,
                        textPaint
                    )

                    // Draw coordinates for debugging
                    if (DEBUG_MODE) {
                        debugPaint.textSize = 20f
                        canvas.drawText(
                            String.format("RA: %.1f° Dec: %.1f°",
                                obj.skyCoordinate.rightAscension,
                                obj.skyCoordinate.declination),
                            screenX,
                            screenY + bitmap.height / 2 + 70,
                            debugPaint
                        )
                    }

                    objectsDrawnCount++
                }
            }
        }

        drawDebugInfo(canvas, "Objects shown: $objectsDrawnCount/${celestialObjects.size}")
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

        canvas.drawLine((viewWidth/2 - 20).toFloat(),
            (viewHeight/2).toFloat(), (viewWidth/2 + 20).toFloat(),
            (viewHeight/2).toFloat(), crosshairPaint)
        canvas.drawLine((viewWidth/2).toFloat(),
            (viewHeight/2 - 20).toFloat(), (viewWidth/2).toFloat(),
            (viewHeight/2 + 20).toFloat(), crosshairPaint)
    }

    private fun drawDebugInfo(canvas: Canvas, statusMessage: String) {
        if (!DEBUG_MODE) return

        debugPaint.textSize = 24f
        debugPaint.textAlign = Paint.Align.LEFT

        val orientation = currentOrientation
        val lookVec = deviceLookVector
        val upVec = deviceUpVector

        val lines = mutableListOf(
            "FPS: $fps",
            statusMessage
        )

        if (orientation != null) {
            lines.add("Az: ${orientation.azimuth.toInt()}° Pitch: ${orientation.pitch.toInt()}° " +
                    "Roll: ${orientation.roll.toInt()}°")
        }

        if (lookVec != null) {
            lines.add("Look: (${String.format("%.2f", lookVec.x)}, " +
                    "${String.format("%.2f", lookVec.y)}, " +
                    "${String.format("%.2f", lookVec.z)})")
        }

        if (upVec != null) {
            lines.add("Up: (${String.format("%.2f", upVec.x)}, " +
                    "${String.format("%.2f", upVec.y)}, " +
                    "${String.format("%.2f", upVec.z)})")
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