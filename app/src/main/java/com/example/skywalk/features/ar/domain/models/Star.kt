package com.example.skywalk.features.ar.domain.models

import android.graphics.Color
import kotlin.math.pow

data class Star(
    val id: Int,
    val skyCoordinate: SkyCoordinate,  // RA and Dec
    val magnitude: Float,              // Brightness (lower is brighter)
    val colorIndex: Float?,            // B-V color index (indicates star color)
    val starName: StarName? = null     // Detailed name information
) {
    companion object {
        // Create star from GeoJSON feature
        fun fromGeoJsonFeature(
            id: Int,
            magnitude: Float,
            colorIndex: Float?,
            ra: Float,
            dec: Float,
            starName: StarName? = null
        ): Star {
            return Star(
                id = id,
                skyCoordinate = SkyCoordinate(ra, dec),
                magnitude = magnitude,
                colorIndex = colorIndex,
                starName = starName
            )
        }
    }

    // Get display name (use proper name information if available)
    fun getDisplayName(): String {
        return starName?.getBestName() ?: "HD-$id" // Return ID-based name if no name info exists
    }

    // Determine if this star has an official proper name
    fun hasProperName(): Boolean {
        return starName?.properName?.isNotEmpty() == true
    }

    // Determine if this is a Bayer-designated star (Greek letter)
    fun hasBayerDesignation(): Boolean {
        return starName?.bayerDesignation?.isNotEmpty() == true
    }

    // Determine if this is an important star (has proper name or Bayer designation)
    fun isNamedStar(): Boolean {
        return hasProperName() || hasBayerDesignation()
    }

    // Calculate star color based on B-V color index
    fun getStarColor(): Int {
        // If no color index, use magnitude to make an educated guess
        val index = colorIndex ?: when {
            magnitude < 0 -> 0.0f  // Very bright stars tend to be blue-white
            magnitude < 2 -> 0.5f  // Bright stars often yellowish
            else -> 1.0f          // Dimmer stars often orange-red
        }

        // Brightened color values
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