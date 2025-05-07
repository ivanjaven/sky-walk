package com.example.skywalk.features.ar.data

import android.content.Context
import com.example.skywalk.features.ar.domain.models.Star
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import timber.log.Timber
import java.io.InputStreamReader
import kotlin.math.min

class StarRepository(private val context: Context) {
    // Add star name repository
    private val starNameRepository = StarNameRepository(context)

    // Cache loaded stars to avoid repeated processing
    private var cachedStars: List<Star>? = null

    /**
     * Load star data from GeoJSON file
     * @param maxMagnitude Maximum magnitude to include (higher value = more stars)
     * @return List of Star objects sorted by brightness
     */
    fun loadStars(maxMagnitude: Float = 5.5f): List<Star> {
        // Return cached data if available
        cachedStars?.let { return it }

        try {
            // Load star names first
            val starNames = starNameRepository.loadStarNames()

            // Load the GeoJSON from assets folder
            val inputStream = context.assets.open("stars.json")
            val reader = InputStreamReader(inputStream)

            // Parse the JSON
            val jsonObject = JsonParser.parseReader(reader).asJsonObject
            val features = jsonObject.getAsJsonArray("features")

            val stars = ArrayList<Star>()

            // Process each star
            for (i in 0 until features.size()) {
                val feature = features[i].asJsonObject

                // Get properties and skip if missing critical data
                val properties = feature.getAsJsonObject("properties")
                if (!properties.has("mag")) continue

                // Get magnitude and filter by brightness
                val magnitude = properties.get("mag").asFloat
                if (magnitude > maxMagnitude) continue

                // Get B-V color index if available
                val colorIndex = if (properties.has("bv")) {
                    try {
                        properties.get("bv").asString.toFloat()
                    } catch (e: Exception) {
                        null
                    }
                } else null

                // Get ID
                val id = if (feature.has("id")) feature.get("id").asInt else i

                // Get coordinates
                val geometry = feature.getAsJsonObject("geometry")
                val coordinates = geometry.getAsJsonArray("coordinates")
                val ra = coordinates.get(0).asFloat
                val dec = coordinates.get(1).asFloat

                // Look up the star name from our repository
                val starName = starNames[id]

                // Create and add star with name data
                stars.add(Star.fromGeoJsonFeature(id, magnitude, colorIndex, ra, dec, starName))
            }

            reader.close()
            inputStream.close()

            // Sort by brightness and cache
            val sortedStars = stars.sortedBy { it.magnitude }
            cachedStars = sortedStars

            Timber.d("Loaded ${sortedStars.size} stars (magnitude <= $maxMagnitude)")
            return sortedStars
        } catch (e: Exception) {
            Timber.e(e, "Error loading star data")
            return emptyList()
        }
    }

    /**
     * Get a subset of stars appropriate for the current zoom level
     * @param zoomFactor Current zoom level (1.0 = normal)
     * @return Filtered list of stars
     */
    fun getStarsForZoomLevel(zoomFactor: Float): List<Star> {
        // Dynamically adjust max magnitude based on zoom
        val maxMag = 4.5f + (zoomFactor - 1.0f) * 2.0f

        // Get all stars up to the current magnitude limit
        val allStars = loadStars(maxMag)

        // Limit max number of stars for performance
        val maxStars = min(2000, (300 * zoomFactor).toInt())

        return if (allStars.size <= maxStars) {
            allStars
        } else {
            // Take brightest stars
            allStars.subList(0, maxStars)
        }
    }

    /**
     * Clear cache (useful when changing settings)
     */
    fun clearCache() {
        cachedStars = null
        starNameRepository.clearCache()
    }
}