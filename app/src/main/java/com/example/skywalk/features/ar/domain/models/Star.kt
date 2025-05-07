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
        private val BRIGHT_STAR_NAMES = mapOf(
            65474 to "Sirius",
            30438 to "Canopus",
            91262 to "Vega",
            69673 to "Altair",
            113368 to "Arcturus",
            24608 to "Capella",
            24436 to "Rigel",
            27989 to "Betelgeuse",
            37279 to "Procyon",
            7588 to "Achernar",
            80763 to "Spica",
            87833 to "Antares",
            102098 to "Deneb",
            113368 to "Fomalhaut",
            37826 to "Pollux",
            21421 to "Aldebaran",
            71683 to "Shaula",
            21421 to "Alphecca",
            90185 to "Elnath",
            30324 to "Alnair",
            15863 to "Alnitak",
            11767 to "Bellatrix",
            84012 to "Regulus",
            95947 to "Castor"
        )

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

    fun getDisplayName(): String {
        return name ?: "HD-$id"
    }

    fun getStarColor(): Int {
        val index = colorIndex ?: when {
            magnitude < 0 -> 0.0f
            magnitude < 2 -> 0.5f
            else -> 1.0f
        }

        return when {
            index < -0.1f -> Color.rgb(175, 196, 255)
            index < 0.0f -> Color.rgb(190, 211, 255)
            index < 0.3f -> Color.rgb(232, 235, 255)
            index < 0.5f -> Color.rgb(255, 252, 235)
            index < 0.8f -> Color.rgb(255, 250, 234)
            index < 1.4f -> Color.rgb(255, 230, 181)
            else -> Color.rgb(255, 224, 131)
        }
    }

    fun getSizeScale(): Float {
        return when {
            magnitude < 0f -> 4.5f
            magnitude < 1f -> 4.0f
            magnitude < 2f -> 3.5f
            magnitude < 3f -> 3.0f
            magnitude < 4f -> 2.5f
            else -> 1.5f
        }
    }

    fun getAlpha(altitude: Float): Int {
        if (altitude < 0) return 0
        return if (altitude < 10) (155 * (altitude / 10f)).toInt() + 100 else 255
    }

    fun isVisibleToNakedEye(): Boolean {
        return magnitude < 6.0f
    }
}
