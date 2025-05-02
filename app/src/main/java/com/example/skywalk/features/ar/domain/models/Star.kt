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
            // First magnitude stars (brightest)
            32349 to "Sirius",
            27989 to "Canopus",
            91262 to "Vega",
            113368 to "Arcturus",
            24608 to "Capella",
            37279 to "Rigel",
            30438 to "Betelgeuse",
            37826 to "Procyon",
            69673 to "Altair",
            7001 to "Achernar",
            58001 to "Antares",
            97649 to "Deneb",
            78401 to "Fomalhaut",

            // Second magnitude stars
            67301 to "Pollux",
            21421 to "Aldebaran",
            80763 to "Spica",
            65474 to "Regulus",
            80816 to "Castor",
            71683 to "Shaula",
            2081 to "Bellatrix",
            90185 to "Elnath",
            109268 to "Alnilam",
            92855 to "Miaplacidus",
            30324 to "Alnair",
            15863 to "Alnitak",

            // Third magnitude stars
            85927 to "Polaris",
            46390 to "Mimosa",
            5447 to "Albireo",
            68702 to "Alioth",
            72607 to "Dubhe",
            75097 to "Merak",
            67301 to "Phecda",
            54061 to "Megrez",
            53910 to "Alkaid",
            85822 to "Mizar",
            86032 to "Alcor",
            83895 to "Izar",
            67927 to "Mirach",
            21421 to "Alphecca",
            110893 to "Kochab",
            105199 to "Alderamin",
            102098 to "Scheat",
            677 to "Alpheratz"
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
        return name ?: "HD-$id" // Return ID-based name if no official name exists
    }

    // Calculate star color based on B-V color index
    // In the Star.kt file - update the getStarColor() method to make stars brighter

    fun getStarColor(): Int {
        // If no color index, use magnitude to make an educated guess
        val index = colorIndex ?: when {
            magnitude < 0 -> 0.0f  // Very bright stars tend to be blue-white
            magnitude < 2 -> 0.5f  // Bright stars often yellowish
            else -> 1.0f          // Dimmer stars often orange-red
        }

        // MODIFIED: Brightened all color values
        return when {
            index < -0.1f -> Color.rgb(175, 196, 255)  // Brightened from (155, 176, 255)
            index < 0.0f -> Color.rgb(190, 211, 255)   // Brightened from (170, 191, 255)
            index < 0.3f -> Color.rgb(232, 235, 255)   // Brightened from (202, 215, 255)
            index < 0.5f -> Color.rgb(255, 252, 235)   // Brightened from (248, 247, 235)
            index < 0.8f -> Color.rgb(255, 250, 234)   // Brightened from (255, 244, 214)
            index < 1.4f -> Color.rgb(255, 230, 181)   // Brightened from (255, 210, 161)
            else -> Color.rgb(255, 224, 131)           // Brightened from (255, 204, 111)
        }
    }

    // Update the getSizeScale() method to make stars appear larger
    fun getSizeScale(): Float {
        // For very bright stars, make them much larger
        return when {
            magnitude < 0f -> 4.5f  // Increased from 3.5f
            magnitude < 1f -> 4.0f  // Increased from 3.0f
            magnitude < 2f -> 3.5f  // Increased from 2.5f
            magnitude < 3f -> 3.0f  // Increased from 2.0f
            magnitude < 4f -> 2.5f  // Increased from 1.5f
            else -> 1.5f            // Increased from 1.0f
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