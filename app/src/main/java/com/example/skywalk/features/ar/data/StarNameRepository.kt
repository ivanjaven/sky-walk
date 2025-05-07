package com.example.skywalk.features.ar.data

import android.content.Context
import com.example.skywalk.features.ar.domain.models.StarName
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import timber.log.Timber
import java.io.InputStreamReader

class StarNameRepository(private val context: Context) {
    // Cache loaded star names to avoid repeated processing
    private var cachedStarNames: Map<Int, StarName>? = null

    /**
     * Load star name data from JSON file
     * @return Map of star IDs to StarName objects
     */
    fun loadStarNames(): Map<Int, StarName> {
        // Return cached data if available
        cachedStarNames?.let { return it }

        try {
            // Load the JSON from assets folder
            val inputStream = context.assets.open("starnames.json")
            val reader = InputStreamReader(inputStream)

            // Parse the JSON
            val jsonObject = JsonParser.parseReader(reader).asJsonObject
            val starNames = mutableMapOf<Int, StarName>()

            // Process each star entry
            for (entry in jsonObject.entrySet()) {
                val id = entry.key.toIntOrNull() ?: continue
                val starData = entry.value.asJsonObject

                starNames[id] = StarName(
                    id = id,
                    properName = starData.get("name")?.asString ?: "",
                    bayerDesignation = starData.get("bayer")?.asString ?: "",
                    flamsteedDesignation = starData.get("flam")?.asString ?: "",
                    variableDesignation = starData.get("var")?.asString ?: "",
                    hdCatalog = starData.get("hd")?.asString ?: "",
                    glieseCatalog = starData.get("gl")?.asString ?: "",
                    hipparcosCatalog = starData.get("hip")?.asString ?: "",
                    constellation = starData.get("c")?.asString ?: "",
                    primaryDesignation = starData.get("desig")?.asString ?: ""
                )
            }

            reader.close()
            inputStream.close()

            // Cache and return
            cachedStarNames = starNames
            Timber.d("Loaded ${starNames.size} star names")
            return starNames
        } catch (e: Exception) {
            Timber.e(e, "Error loading star name data")
            return emptyMap()
        }
    }

    /**
     * Clear cache (useful when changing settings)
     */
    fun clearCache() {
        cachedStarNames = null
    }
}