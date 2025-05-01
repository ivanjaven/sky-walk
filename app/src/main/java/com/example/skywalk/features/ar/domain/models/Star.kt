package com.example.skywalk.features.ar.domain.models

import android.graphics.Color
import kotlin.math.pow

data class Star(
    val id: Int,
    val skyCoordinate: SkyCoordinate,  // RA and Dec
    val magnitude: Float,              // Brightness (lower is brighter)
    val colorIndex: Float?,            // B-V color index (indicates star color)
    val name: String? = null           // Optional name for bright stars
) {
    companion object {
        // Common bright star names
        private val BRIGHT_STAR_NAMES = mapOf(
            // Brightest stars
            32349 to "Sirius",
            27989 to "Canopus",
            91262 to "Vega",
            113368 to "Arcturus",
            87937 to "Barnard's Star",
            24608 to "Capella",
            37279 to "Rigel",
            32349 to "Sirius",
            30438 to "Betelgeuse",
            37826 to "Procyon",
            69673 to "Altair",
            7001 to "Achernar",
            58001 to "Antares",
            97649 to "Deneb",
            78401 to "Fomalhaut"
            // Could add more as needed
        )

        // Create star from GeoJSON feature
        fun fromGeoJsonFeature(id: Int, magnitude: Float, colorIndex: Float?, ra: Float, dec: Float): Star {
            return Star(
                id = id,
                skyCoordinate = SkyCoordinate(ra, dec),
                magnitude = magnitude,
                colorIndex = colorIndex,
                name = BRIGHT_STAR_NAMES[id]
            )
        }
    }

    // Get display name (use known name or empty)
    fun getDisplayName(): String {
        return name ?: ""
    }

    // Calculate star color based on B-V color index
    fun getStarColor(): Int {
        // If no color index, use magnitude to make an educated guess
        val index = colorIndex ?: when {
            magnitude < 0 -> 0.0f  // Very bright stars tend to be blue-white
            magnitude < 2 -> 0.5f  // Bright stars often yellowish
            else -> 1.0f          // Dimmer stars often orange-red
        }

        return when {
            index < -0.1f -> Color.rgb(155, 176, 255)  // Blue (O type)
            index < 0.0f -> Color.rgb(170, 191, 255)   // Blue-white (B type)
            index < 0.3f -> Color.rgb(202, 215, 255)   // White (A type)
            index < 0.5f -> Color.rgb(248, 247, 255)   // Yellow-white (F type)
            index < 0.8f -> Color.rgb(255, 244, 234)   // Yellow (G type)
            index < 1.4f -> Color.rgb(255, 210, 161)   // Orange (K type)
            else -> Color.rgb(255, 204, 111)           // Red (M type)
        }
    }

    // Get the appropriate size scale based on magnitude
    fun getSizeScale(): Float {
        // For very bright stars, make them much larger
        return when {
            magnitude < 0f -> 3.5f
            magnitude < 1f -> 3.0f
            magnitude < 2f -> 2.5f
            magnitude < 3f -> 2.0f
            magnitude < 4f -> 1.5f
            else -> 1.0f
        }
    }

    // For dimming stars that are far from zenith or setting
    fun getAlpha(altitude: Float): Int {
        // Stars near horizon appear dimmer due to atmospheric extinction
        if (altitude < 0) return 0  // Below horizon

        return when {
            altitude < 10 -> (155 * (altitude / 10f)).toInt() + 100
            else -> 255
        }
    }

    // Is this star visible to naked eye?
    fun isVisibleToNakedEye(): Boolean {
        return magnitude < 6.0f
    }
}