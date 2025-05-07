package com.example.skywalk.features.ar.presentation.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.example.skywalk.features.ar.domain.models.CelestialObject
import com.example.skywalk.features.ar.domain.models.Constellation
import com.example.skywalk.features.ar.domain.models.DeviceOrientation
import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Star
import com.example.skywalk.features.ar.domain.models.Vector3
import com.example.skywalk.features.ar.presentation.viewmodel.AstronomyViewModel
import com.example.skywalk.features.ar.utils.AstronomyUtils
import timber.log.Timber
import java.util.Date
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class SkyOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Add members for galaxy background
    private var useGalaxyBackground = false
    private var galaxyPaint = Paint()
    private var galaxyStars = mutableListOf<GalaxyStar>()
    private val random = java.util.Random()

    // Inner class for galaxy stars
    private data class GalaxyStar(
        val x: Float,
        val y: Float,
        val size: Float,
        val alpha: Int,
        val color: Int
    )

    // Paint objects
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

    private val starPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
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
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    // Add constellation paint objects
    private val constellationPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3.5f  // INCREASED FROM 1.5f to 3.5f for thicker lines
        color = Color.argb(220, 100, 150, 255) // Made more opaque (180 to 220)
    }

    private val constellationLabelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f  // INCREASED from 24f to 30f
        color = Color.argb(230, 150, 200, 255) // Made more opaque (200 to 230)
        setShadowLayer(4f, 0f, 0f, Color.BLACK)  // Added larger shadow
        textAlign = Paint.Align.CENTER
    }

    // Focused object tracking
    private var focusedObject: CelestialObject? = null
    private var focusedStar: Star? = null
    private var focusedConstellation: Constellation? = null
    private val CENTER_FOCUS_RADIUS = 60f
    private val objectInfoListeners = mutableListOf<(Any?, String, String) -> Unit>()

    // Description maps
    private val planetDescriptions = mapOf(
        "Sun" to "The star at the center of our Solar System. It's about 93 million miles from Earth.",
        "Moon" to "Earth's only natural satellite, orbiting at an average distance of 238,855 miles.",
        "Mercury" to "The smallest and innermost planet in the Solar System, orbiting the Sun every 88 days.",
        "Venus" to "The second planet from the Sun, similar in size to Earth with a thick toxic atmosphere.",
        "Mars" to "The fourth planet from the Sun, known as the Red Planet due to iron oxide on its surface.",
        "Jupiter" to "The largest planet in the Solar System, a gas giant with a distinctive Great Red Spot.",
        "Saturn" to "The sixth planet from the Sun, famous for its extensive ring system.",
        "Uranus" to "The seventh planet from the Sun, an ice giant with a tilted rotation axis.",
        "Neptune" to "The eighth planet from the Sun, the farthest known planet with strong winds.",
        "Pluto" to "A dwarf planet in the Kuiper belt, formerly classified as the ninth planet."
    )

    private val starDescriptions = mapOf(
        "Sirius" to "The brightest star in the night sky, located in Canis Major, 8.6 light-years away.",
        "Canopus" to "The second brightest star in the night sky, located in Carina, 310 light-years away.",
        "Vega" to "The fifth brightest star in the night sky, part of Lyra constellation, 25 light-years away.",
        "Arcturus" to "The brightest star in the northern celestial hemisphere, in Boötes, 37 light-years away.",
        "Capella" to "The sixth brightest star in the night sky, in Auriga, 43 light-years away.",
        "Rigel" to "A blue supergiant star in Orion, approximately 860 light-years from Earth.",
        "Betelgeuse" to "A red supergiant star in Orion, one of the largest stars visible to the naked eye.",
        "Procyon" to "The eighth brightest star in the night sky, in Canis Minor, 11.5 light-years away.",
        "Altair" to "The twelfth brightest star in the night sky, in Aquila, 16.7 light-years from Earth.",
        "Antares" to "A red supergiant star in Scorpius, approximately 550 light-years from Earth.",
        "Deneb" to "The 19th brightest star and part of the Summer Triangle, in Cygnus.",
        "Regulus" to "The brightest star in Leo, approximately 79 light-years from Earth.",
        "Spica" to "The brightest star in Virgo, a binary star system 250 light-years from Earth.",
        "Pollux" to "The brightest star in Gemini, an orange-hued giant 34 light-years from Earth.",
        "Fomalhaut" to "The 18th brightest star, with a confirmed exoplanet, in Piscis Austrinus.",
        "Polaris" to "The North Star, used for navigation for centuries, in Ursa Minor.",
        "Aldebaran" to "The brightest star in Taurus, an orange giant about 65 light-years from Earth.",
        "Castor" to "A sextuple star system in Gemini, about 52 light-years from Earth.",
        "Achernar" to "The ninth-brightest star in the night sky, in Eridanus, 139 light-years away.",
        "Hadar" to "Also known as Beta Centauri, the 11th brightest star, 525 light-years from Earth.",
        "Acrux" to "The brightest star in the Southern Cross (Crux), 320 light-years from Earth.",
        "Mizar" to "A famous double star in Ursa Major, part of the Big Dipper's handle.",
        "α Cen" to "Alpha Centauri, the closest star system to our Solar System at 4.37 light-years."
    )

    private val constellationDescriptions = mapOf(
        "Orion" to "One of the most recognizable constellations, depicting a hunter with a belt of three stars.",
        "Ursa Major" to "The Great Bear, containing the Big Dipper asterism visible throughout the year.",
        "Ursa Minor" to "The Little Bear, containing the Little Dipper and Polaris (the North Star).",
        "Cassiopeia" to "Named after the vain queen in Greek mythology, with a distinctive W-shaped pattern.",
        "Leo" to "Represents the Nemean Lion from Greek mythology, visible in spring.",
        "Scorpius" to "Resembles a scorpion with a distinctive curved tail, visible in summer.",
        "Cygnus" to "Represents a swan flying along the Milky Way, with Deneb at its tail.",
        "Lyra" to "A small constellation representing Orpheus' lyre, containing the bright star Vega.",
        "Gemini" to "Represents the twins Castor and Pollux from Greek mythology, visible in winter.",
        "Taurus" to "Depicts a bull, with the bright star Aldebaran as its eye and the Pleiades cluster.",
        "Andromeda" to "Named after the princess from Greek mythology, contains the Andromeda Galaxy.",
        "Sagittarius" to "Often depicted as a centaur drawing a bow, points toward the center of our galaxy.",
        "Pisces" to "Depicts two fish connected by a cord, one of the zodiac constellations.",
        "Virgo" to "One of the zodiac constellations, represents a maiden holding an ear of wheat (Spica)."
    )

    // State tracking
    private var currentOrientation: DeviceOrientation? = null
    private var deviceLookVector: Vector3? = null
    private var deviceUpVector: Vector3? = null
    private var celestialObjects: List<CelestialObject> = emptyList()
    private var stars: List<Star> = emptyList()
    private var constellations: List<Constellation> = emptyList()
    private var showConstellations = true
    private val objectBitmaps = mutableMapOf<Int, Bitmap>()
    private var viewModel: AstronomyViewModel? = null
    private var viewWidth = 0
    private var viewHeight = 0

    // Tracking screen positions
    private val lastScreenPositions = mutableMapOf<String, Pair<Float, Float>>()

    // Star rendering options
    private val SHOW_STAR_NAMES = true
    private val MAX_LABELED_STARS = 20

    // Debug and UI options
    private val SHOW_COMPASS = true
    private val SHOW_ALTITUDE_GUIDES = true
    private val SHOW_OBJECT_INFO = true

    // Compass directions
    private val compassDirections = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    // Add listener methods
    fun addObjectInfoListener(listener: (Any?, String, String) -> Unit) {
        objectInfoListeners.add(listener)
    }

    fun removeObjectInfoListener(listener: (Any?, String, String) -> Unit) {
        objectInfoListeners.remove(listener)
    }

    private fun notifyObjectInfoListeners(obj: Any?, name: String, description: String) {
        objectInfoListeners.forEach { it(obj, name, description) }
    }

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

            viewModel.stars.observe(lifecycleOwner) { newStars ->
                stars = newStars
                invalidate()
            }

            // Add constellation observers
            viewModel.constellations.observe(lifecycleOwner) { constellations ->
                this.constellations = constellations
                invalidate()
            }

            viewModel.showConstellations.observe(lifecycleOwner) { show ->
                this.showConstellations = show
                invalidate()
            }
        }
    }

    private fun prepareObjectBitmaps(objects: List<CelestialObject>) {
        if (viewWidth == 0 || viewHeight == 0) return

        objects.forEach { obj ->
            if (!objectBitmaps.containsKey(obj.imageResourceId)) {
                try {
                    val originalBitmap = BitmapFactory.decodeResource(resources, obj.imageResourceId)

                    val scaleFactor = when (obj.name) {
                        "Sun" -> 0.28f
                        "Moon" -> 0.23f
                        "Jupiter" -> 0.14f
                        "Saturn" -> 0.14f
                        "Venus" -> 0.10f
                        "Mars" -> 0.18f
                        "Uranus" -> 0.08f    // Smaller size for Uranus
                        "Neptune" -> 0.07f   // Smaller size for Neptune
                        "Pluto" -> 0.05f
                        else -> 0.10f
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

                } catch (e: Exception) {
                    Timber.e(e, "Error loading bitmap for ${obj.name}")
                }
            }
        }
    }

    private fun initGalaxyBackground() {
        // Clear existing stars
        galaxyStars.clear()

        // Create random stars for the galaxy background
        val numStars = 600
        for (i in 0 until numStars) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val size = when {
                random.nextFloat() < 0.05f -> random.nextFloat() * 3f + 2f // Bright stars
                random.nextFloat() < 0.2f -> random.nextFloat() * 2f + 1f // Medium stars
                else -> random.nextFloat() * 1f + 0.5f // Small stars
            }

            val alpha = (random.nextFloat() * 155 + 100).toInt()

            // Generate star color - mostly white, some blue/yellow/red
            val color = when {
                random.nextFloat() < 0.7f -> Color.WHITE // 70% white stars
                random.nextFloat() < 0.5f -> Color.rgb(155, 190, 255) // 15% blue stars
                random.nextFloat() < 0.5f -> Color.rgb(255, 240, 180) // 7.5% yellow stars
                else -> Color.rgb(255, 170, 170) // 7.5% red stars
            }

            galaxyStars.add(GalaxyStar(x, y, size, alpha, color))
        }
    }

    // Set galaxy background mode
    fun setUseGalaxyBackground(use: Boolean) {
        useGalaxyBackground = use

        // Set plain black background instead of generating stars
        setBackgroundColor(if (use) Color.BLACK else Color.TRANSPARENT)

        // Optionally add just a few stars (much fewer than before)
        if (use) {
            // Clear existing stars
            galaxyStars.clear()

            // Add just a few subtle stars (reduced from 600 to 100)
//            val numStars = 10
//            for (i in 0 until numStars) {
//                val x = random.nextFloat() * width
//                val y = random.nextFloat() * height
//                val size = random.nextFloat() * 1.5f + 0.5f // Smaller stars
//                val alpha = (random.nextFloat() * 100 + 50).toInt() // More transparent
//                val color = Color.WHITE // Just white stars
//
//                galaxyStars.add(GalaxyStar(x, y, size, alpha, color))
//            }
        }

        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h

        // Clear and reload bitmaps at the new size
        objectBitmaps.values.forEach { it.recycle() }
        objectBitmaps.clear()
        celestialObjects.let { prepareObjectBitmaps(it) }

        // Re-initialize galaxy if needed
        if (useGalaxyBackground) {
            initGalaxyBackground()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Draw galaxy background first if enabled
        if (useGalaxyBackground) {
            // Fill with black (already done with backgroundColor, but just to be sure)
            canvas.drawColor(Color.BLACK)

            // Draw galaxy stars
            for (star in galaxyStars) {
                galaxyPaint.color = star.color
                galaxyPaint.alpha = star.alpha
                canvas.drawCircle(star.x, star.y, star.size, galaxyPaint)

                // Add glow to larger stars
                if (star.size > 1.5f) {
                    galaxyPaint.alpha = (star.alpha * 0.4f).toInt()
                    canvas.drawCircle(star.x, star.y, star.size * 2f, galaxyPaint)
                }
            }

            // Add a subtle blue-purple gradient for nebula effect
            val gradientPaint = Paint()
            gradientPaint.shader = RadialGradient(
                width / 2f, height / 2f,
                width * 0.8f,
                intArrayOf(
                    Color.argb(5, 100, 50, 160),  // Very transparent purple
                    Color.argb(15, 30, 40, 120),  // Slightly more visible blue
                    Color.argb(0, 0, 0, 0)        // Transparent
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
        }

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

        // Calculate local sidereal time
        val currentDate = viewModel?.getCurrentDate() ?: Date()
        val latitude = viewModel?.getLatitude() ?: 0f
        val longitude = viewModel?.getLongitude() ?: 0f
        val lst = AstronomyUtils.calculateSiderealTime(currentDate, longitude)

        // Draw constellations if enabled (draw BEFORE stars so they appear behind)
        if (showConstellations) {
            drawConstellations(canvas, lookVector, upVector, fieldOfView, lst, latitude)
        }

        // Draw stars
        drawStars(canvas, lookVector, upVector, fieldOfView, lst, latitude)

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
                    // Check if this is the focused object
                    if (obj == focusedObject) {
                        // Draw highlight effect
                        val originalColor = paint.color
                        val originalStyle = paint.style
                        val originalWidth = paint.strokeWidth

                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        paint.color = Color.YELLOW
                        paint.alpha = 200

                        canvas.drawCircle(
                            screenX,
                            screenY,
                            bitmap.width / 1.8f,
                            paint
                        )

                        // Reset paint
                        paint.style = originalStyle
                        paint.strokeWidth = originalWidth
                        paint.color = originalColor
                        paint.alpha = 255
                    }

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

        // Check for objects in focus
        checkObjectInFocus()
    }

    /**
     * Draw constellations on the canvas
     */
    private fun drawConstellations(
        canvas: Canvas,
        lookVector: Vector3,
        upVector: Vector3,
        fieldOfView: Float,
        localSiderealTime: Float,
        latitude: Float
    ) {
        if (constellations.isEmpty()) return

        // Store visible constellation centers for label placement
        val visibleConstellationCenters = mutableMapOf<String, Pair<Float, Float>>()
        val pointsCount = mutableMapOf<String, Int>()

        // First, draw all constellation lines
        for (constellation in constellations) {
            var isVisible = false
            var sumX = 0f
            var sumY = 0f
            var count = 0

            // Check if this is the focused constellation
            val isFocused = constellation == focusedConstellation

            // If focused, save original paint properties and set highlight
            if (isFocused) {
                val originalColor = constellationPaint.color
                val originalWidth = constellationPaint.strokeWidth
                val originalAlpha = constellationPaint.alpha

                constellationPaint.color = Color.YELLOW
                constellationPaint.strokeWidth = 5f
                constellationPaint.alpha = 230
            }

            // For each line in the constellation
            for (line in constellation.lines) {
                // Convert from equatorial to horizontal coordinates
                val startCoord = AstronomyUtils.equatorialToHorizontal(
                    line.startRa, line.startDec, latitude, localSiderealTime
                )

                val endCoord = AstronomyUtils.equatorialToHorizontal(
                    line.endRa, line.endDec, latitude, localSiderealTime
                )

                // Skip if both points are below horizon
                if (startCoord.declination < 0 && endCoord.declination < 0) {
                    continue
                }

                // Calculate screen positions
                val startScreen = AstronomyUtils.celestialToScreenCoordinates(
                    startCoord, lookVector, upVector, fieldOfView, viewWidth, viewHeight
                )

                val endScreen = AstronomyUtils.celestialToScreenCoordinates(
                    endCoord, lookVector, upVector, fieldOfView, viewWidth, viewHeight
                )

                // Draw line if both points are visible
                if (startScreen != null && endScreen != null) {
                    canvas.drawLine(
                        startScreen.first, startScreen.second,
                        endScreen.first, endScreen.second,
                        constellationPaint
                    )

                    // Track the center for this constellation for label placement
                    sumX += startScreen.first + endScreen.first
                    sumY += startScreen.second + endScreen.second
                    count += 2
                    isVisible = true
                }
            }

            // Reset paint properties if this was the focused constellation
            if (isFocused) {
                constellationPaint.color = Color.argb(220, 100, 150, 255)
                constellationPaint.strokeWidth = 3.5f
                constellationPaint.alpha = 220
            }

            // If this constellation has visible lines, record its center
            if (isVisible && count > 0) {
                visibleConstellationCenters[constellation.name] = Pair(sumX / count, sumY / count)
                pointsCount[constellation.name] = count
            }
        }

        // Now draw labels for visible constellations, but only for important ones (rank 1-2)
        for (constellation in constellations) {
            if (constellation.rank <= 2) {  // Only label important constellations
                val center = visibleConstellationCenters[constellation.name] ?: continue

                // Draw constellation name at the calculated center
                constellationLabelPaint.textSize = when (constellation.rank) {
                    1 -> 28f  // Major constellations get larger text
                    else -> 22f
                }

                canvas.drawText(
                    constellation.name,
                    center.first,
                    center.second,
                    constellationLabelPaint
                )
            }
        }
    }

    /**
     * Draw stars on the canvas
     */
    private fun drawStars(
        canvas: Canvas,
        lookVector: Vector3,
        upVector: Vector3,
        fieldOfView: Float,
        localSiderealTime: Float,
        latitude: Float
    ) {
        if (stars.isEmpty()) return

        // Process stars
        for (star in stars) {
            // Convert from equatorial (RA/Dec) to horizontal coordinates (Az/Alt)
            val horizontalCoord = AstronomyUtils.equatorialToHorizontal(
                star.skyCoordinate.rightAscension,
                star.skyCoordinate.declination,
                latitude,
                localSiderealTime
            )

            // Skip stars below horizon (negative altitude)
            if (horizontalCoord.declination < 0) continue

            // Calculate screen position
            val screenCoord = AstronomyUtils.celestialToScreenCoordinates(
                horizontalCoord,
                lookVector,
                upVector,
                fieldOfView,
                viewWidth,
                viewHeight
            ) ?: continue

            // Draw the star
            val (screenX, screenY) = screenCoord

            // Set star color based on B-V index
            starPaint.color = star.getStarColor()

            // Check if this is the focused star
            if (star == focusedStar) {
                // Save original paint settings
                val originalColor = starPaint.color
                val originalStyle = starPaint.style
                val originalAlpha = starPaint.alpha

                // Draw highlight effect
                starPaint.style = Paint.Style.STROKE
                starPaint.strokeWidth = 3f
                starPaint.color = Color.YELLOW
                starPaint.alpha = 200

                // Determine size based on magnitude
                val starSize = when {
                    star.magnitude < 0 -> 12f
                    star.magnitude < 1 -> 10f
                    star.magnitude < 2 -> 8f
                    star.magnitude < 3 -> 6.5f
                    star.magnitude < 4 -> 5f
                    else -> 3.5f
                }

                canvas.drawCircle(screenX, screenY, starSize * 2.5f, starPaint)

                // Reset paint
                starPaint.style = originalStyle
                starPaint.color = originalColor
                starPaint.alpha = originalAlpha
            }

            // Determine size based on magnitude - INCREASED all sizes
            val starSize = when {
                star.magnitude < 0 -> 12f
                star.magnitude < 1 -> 10f
                star.magnitude < 2 -> 8f
                star.magnitude < 3 -> 6.5f
                star.magnitude < 4 -> 5f
                else -> 3.5f
            }

            // Draw star as circle
            canvas.drawCircle(screenX, screenY, starSize, starPaint)

            // Enhanced glow effect for stars
            if (star.magnitude < 3.0f) {
                starPaint.alpha = 150
                canvas.drawCircle(screenX, screenY, starSize * 2.2f, starPaint)
                starPaint.alpha = 255
            }

            // Get the display name
            val name = star.getDisplayName()

            // Check if this star has an important name (proper name or Bayer designation)
            val isImportantStar = star.isNamedStar()

            // Only show names for important stars or when constellations are visible
            if (isImportantStar || showConstellations) {
                // Set text size based on magnitude and whether it's an important star
                textPaint.textSize = when {
                    isImportantStar && star.magnitude < 0 -> 32f
                    isImportantStar && star.magnitude < 1 -> 30f
                    isImportantStar && star.magnitude < 2 -> 28f
                    isImportantStar && star.magnitude < 3 -> 26f
                    isImportantStar -> 24f
                    star.magnitude < 1 -> 16f
                    star.magnitude < 3 -> 14f
                    else -> 12f
                }

                // Adjust alpha (transparency) based on brightness and name type
                val alpha = when {
                    isImportantStar && star.magnitude < 1 -> 255
                    isImportantStar && star.magnitude < 2 -> 230
                    isImportantStar && star.magnitude < 3 -> 200
                    isImportantStar -> 180
                    showConstellations && star.magnitude < 1 -> 160
                    showConstellations && star.magnitude < 2 -> 140
                    showConstellations && star.magnitude < 3 -> 120
                    else -> 0  // Hide catalog names when constellations are off
                }

                textPaint.alpha = alpha

                // Draw star name only if alpha > 0
                if (alpha > 0) {
                    canvas.drawText(
                        name,
                        screenX,
                        screenY + starSize + 15f,
                        textPaint
                    )
                }

                // Reset alpha
                textPaint.alpha = 255
            }
        }
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

    // Add this method to check for objects in focus
    private fun checkObjectInFocus() {
        // Center of the screen
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // Reset focus flags
        var foundFocus = false

        // First check celestial objects (planets, sun, moon)
        for (obj in celestialObjects) {
            val screenCoordinates = AstronomyUtils.celestialToScreenCoordinates(
                obj.skyCoordinate,
                deviceLookVector ?: return,
                deviceUpVector ?: return,
                viewModel?.getFieldOfViewRadians() ?: return,
                viewWidth,
                viewHeight
            ) ?: continue

            val (screenX, screenY) = screenCoordinates
            val distance = kotlin.math.sqrt((screenX - centerX).pow(2) + (screenY - centerY).pow(2))

            if (distance < CENTER_FOCUS_RADIUS) {
                if (focusedObject != obj || focusedStar != null || focusedConstellation != null) {
                    focusedObject = obj
                    focusedStar = null
                    focusedConstellation = null

                    val description = planetDescriptions[obj.name] ?: "A celestial object."
                    notifyObjectInfoListeners(obj, obj.name, description)
                }
                foundFocus = true
                break
            }
        }

        // If no celestial object is in focus, check for stars
        if (!foundFocus) {
            // Current date and location for coordinate conversion
            val currentDate = viewModel?.getCurrentDate() ?: Date()
            val latitude = viewModel?.getLatitude() ?: 0f
            val longitude = viewModel?.getLongitude() ?: 0f
            val lst = AstronomyUtils.calculateSiderealTime(currentDate, longitude)

            // Only check bright stars for performance (mag < 4)
            val brightStars = stars.filter { it.magnitude < 4.0f }

            for (star in brightStars) {
                val horizontalCoord = AstronomyUtils.equatorialToHorizontal(
                    star.skyCoordinate.rightAscension,
                    star.skyCoordinate.declination,
                    latitude,
                    lst
                )

                // Skip stars below horizon
                if (horizontalCoord.declination < 0) continue

                val screenCoord = AstronomyUtils.celestialToScreenCoordinates(
                    horizontalCoord,
                    deviceLookVector ?: return,
                    deviceUpVector ?: return,
                    viewModel?.getFieldOfViewRadians() ?: return,
                    viewWidth,
                    viewHeight
                ) ?: continue

                val (screenX, screenY) = screenCoord
                val distance = kotlin.math.sqrt((screenX - centerX).pow(2) + (screenY - centerY).pow(2))

                if (distance < CENTER_FOCUS_RADIUS) {
                    if (focusedStar != star || focusedObject != null || focusedConstellation != null) {
                        focusedStar = star
                        focusedObject = null
                        focusedConstellation = null

                        val starName = star.getDisplayName()
                        val description = if (star.isNamedStar()) {
                            // Try to get description for named star
                            starDescriptions[starName] ?: "A bright star with magnitude ${String.format("%.1f", star.magnitude)}"
                        } else {
                            // Generic description for catalog star
                            "Star with magnitude ${String.format("%.1f", star.magnitude)}"
                        }

                        notifyObjectInfoListeners(star, starName, description)
                    }
                    foundFocus = true
                    break
                }
            }
        }

        // If no celestial object or star is in focus, check for constellations
        if (!foundFocus && showConstellations) {
            // Only check major constellations (rank <= 2) for performance
            val majorConstellations = constellations.filter { it.rank <= 2 }

            for (constellation in majorConstellations) {
                // Get the center point of the constellation
                val center = constellation.getCenter()

                // Current date and location for coordinate conversion
                val currentDate = viewModel?.getCurrentDate() ?: Date()
                val latitude = viewModel?.getLatitude() ?: 0f
                val longitude = viewModel?.getLongitude() ?: 0f
                val lst = AstronomyUtils.calculateSiderealTime(currentDate, longitude)

                // Convert to horizontal coordinates
                val horizontalCoord = AstronomyUtils.equatorialToHorizontal(
                    center.rightAscension,
                    center.declination,
                    latitude,
                    lst
                )

                // Skip if below horizon
                if (horizontalCoord.declination < 0) continue

                val screenCoord = AstronomyUtils.celestialToScreenCoordinates(
                    horizontalCoord,
                    deviceLookVector ?: return,
                    deviceUpVector ?: return,
                    viewModel?.getFieldOfViewRadians() ?: return,
                    viewWidth,
                    viewHeight
                ) ?: continue

                val (screenX, screenY) = screenCoord

                // Use a larger radius for constellations since they're bigger
                val distance = kotlin.math.sqrt((screenX - centerX).pow(2) + (screenY - centerY).pow(2))
                val constellationRadius = CENTER_FOCUS_RADIUS * 1.5f

                if (distance < constellationRadius) {
                    if (focusedConstellation != constellation || focusedObject != null || focusedStar != null) {
                        focusedConstellation = constellation
                        focusedObject = null
                        focusedStar = null

                        val description = constellationDescriptions[constellation.name] ?:
                        "A constellation visible in the night sky."

                        notifyObjectInfoListeners(constellation, constellation.name, description)
                    }
                    foundFocus = true
                    break
                }
            }
        }

        // If nothing is in focus, clear all focused objects
        if (!foundFocus && (focusedObject != null || focusedStar != null || focusedConstellation != null)) {
            focusedObject = null
            focusedStar = null
            focusedConstellation = null
            notifyObjectInfoListeners(null, "", "")
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