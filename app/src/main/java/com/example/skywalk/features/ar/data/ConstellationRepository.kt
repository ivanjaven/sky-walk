package com.example.skywalk.features.ar.data

import android.content.Context
import com.example.skywalk.features.ar.domain.models.Constellation
import com.example.skywalk.features.ar.domain.models.ConstellationLine
import com.google.gson.JsonParser
import timber.log.Timber
import java.io.InputStreamReader

/**
 * Repository for loading and managing constellation data
 */
class ConstellationRepository(private val context: Context) {

    // Cache loaded constellation data to avoid repeated processing
    private var cachedConstellations: List<Constellation>? = null

    // Map of major constellation IDs to full names
    private val constellationNames = mapOf(
        "And" to "Andromeda",
        "Aqr" to "Aquarius",
        "Aql" to "Aquila",
        "Ari" to "Aries",
        "Aur" to "Auriga",
        "Boo" to "Boötes",
        "CMa" to "Canis Major",
        "CMi" to "Canis Minor",
        "Cap" to "Capricornus",
        "Car" to "Carina",
        "Cas" to "Cassiopeia",
        "Cen" to "Centaurus",
        "Cep" to "Cepheus",
        "Cet" to "Cetus",
        "CrB" to "Corona Borealis",
        "Cru" to "Crux",
        "Cyg" to "Cygnus",
        "Del" to "Delphinus",
        "Dra" to "Draco",
        "Gem" to "Gemini",
        "Her" to "Hercules",
        "Hya" to "Hydra",
        "Leo" to "Leo",
        "Lib" to "Libra",
        "Lyr" to "Lyra",
        "Oph" to "Ophiuchus",
        "Ori" to "Orion",
        "Peg" to "Pegasus",
        "Per" to "Perseus",
        "Psc" to "Pisces",
        "PsA" to "Piscis Austrinus",
        "Pup" to "Puppis",
        "Sgr" to "Sagittarius",
        "Sco" to "Scorpius",
        "Tau" to "Taurus",
        "TrA" to "Triangulum Australe",
        "UMa" to "Ursa Major",
        "UMi" to "Ursa Minor",
        "Vel" to "Vela",
        "Vir" to "Virgo"
    )

    /**
     * Load constellation data from JSON file
     * @return List of Constellation objects
     */
    fun loadConstellations(): List<Constellation> {
        // Return cached data if available
        cachedConstellations?.let { return it }

        try {
            // Load the GeoJSON from assets folder
            val inputStream = context.assets.open("constellations.lines.json")
            val reader = InputStreamReader(inputStream)

            // Parse the JSON
            val jsonObject = JsonParser.parseReader(reader).asJsonObject
            val features = jsonObject.getAsJsonArray("features")

            val constellations = ArrayList<Constellation>()

            // Process each constellation
            for (i in 0 until features.size()) {
                val feature = features[i].asJsonObject

                // Get constellation ID
                val id = feature.get("id").asString

                // Skip if no name mapping is found
                val name = constellationNames[id] ?: continue

                // Get rank (1 = most prominent, 3 = least)
                val properties = feature.getAsJsonObject("properties")
                val rank = if (properties.has("rank")) {
                    properties.get("rank").asString.toIntOrNull() ?: 3
                } else {
                    3 // Default rank if not specified
                }

                // Extract line segments
                val geometry = feature.getAsJsonObject("geometry")
                val geometryType = geometry.get("type").asString

                // Make sure it's a MultiLineString
                if (geometryType != "MultiLineString") continue

                val coordinates = geometry.getAsJsonArray("coordinates")
                val lines = ArrayList<ConstellationLine>()

                // Process each line segment
                for (j in 0 until coordinates.size()) {
                    val lineSegment = coordinates[j].asJsonArray

                    // Each line segment has multiple points forming a path
                    for (k in 0 until lineSegment.size() - 1) {
                        val startCoord = lineSegment[k].asJsonArray
                        val endCoord = lineSegment[k + 1].asJsonArray

                        val startRa = startCoord[0].asFloat
                        val startDec = startCoord[1].asFloat
                        val endRa = endCoord[0].asFloat
                        val endDec = endCoord[1].asFloat

                        // Add line segment
                        lines.add(
                            ConstellationLine(
                                startRa = startRa,
                                startDec = startDec,
                                endRa = endRa,
                                endDec = endDec
                            )
                        )
                    }
                }

                // Skip constellations with no lines
                if (lines.isEmpty()) continue

                // Add constellation to the list
                constellations.add(
                    Constellation(
                        id = id,
                        name = name,
                        rank = rank,
                        lines = lines
                    )
                )
            }

            reader.close()
            inputStream.close()

            // Sort constellations by rank and cache
            val sortedConstellations = constellations.sortedBy { it.rank }
            cachedConstellations = sortedConstellations

            Timber.d("Loaded ${sortedConstellations.size} constellations")
            return sortedConstellations
        } catch (e: Exception) {
            Timber.e(e, "Error loading constellation data")
            return emptyList()
        }
    }

    /**
     * Clear cache (useful when changing settings)
     */
    fun clearCache() {
        cachedConstellations = null
    }
}