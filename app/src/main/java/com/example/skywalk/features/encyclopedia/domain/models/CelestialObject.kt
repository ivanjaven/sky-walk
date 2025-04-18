package com.example.skywalk.features.encyclopedia.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CelestialObject(
    val id: String,
    val name: String,
    val description: String,
    val summary: String = "",
    val type: CelestialObjectType,
    val imageUrl: String = "",
    val thumbnailUrl: String = "",
    val coordinates: CelestialCoordinates? = null,
    val visibility: CelestialVisibility? = null,
    val observing: ObservingDetails? = null,
    val properties: CelestialProperties? = null,
    val facts: List<String> = emptyList(),
    val arVisualization: ArVisualization? = null,
    val keywords: List<String> = emptyList(),
    val relatedObjects: List<String> = emptyList(),
    val dateCreated: String = ""
) : Parcelable

@Parcelize
data class CelestialCoordinates(
    val rightAscension: String = "",
    val declination: String = "",
    val properMotion: String? = null,
    val epoch: String = "J2000"
) : Parcelable

@Parcelize
data class CelestialVisibility(
    val magnitude: Double? = null,
    val color: String = "#FFFFFF",
    val angularSize: String = "",
    val isVisibleToNakedEye: Boolean = false
) : Parcelable

@Parcelize
data class ObservingDetails(
    val bestTimeToView: String = "",
    val findingTips: String = "",
    val equipment: String = "",
    val features: String = ""
) : Parcelable

@Parcelize
data class CelestialProperties(
    val mass: String = "",
    val diameter: String = "",
    val distance: String = ""
) : Parcelable

@Parcelize
data class ArVisualization(
    val visualType: String = "POINT",
    val pointColor: String = "#FFFFFF",
    val pointSize: Double = 1.0,
    val labelOffset: LabelOffset = LabelOffset(),
    val labelColor: String = "#FFFFFF",
    val showLabel: Boolean = true
) : Parcelable

@Parcelize
data class LabelOffset(
    val x: Int = 0,
    val y: Int = 10
) : Parcelable

@Parcelize
enum class CelestialObjectType : Parcelable {
    PLANET,
    STAR,
    GALAXY,
    NEBULA,
    MOON,
    ASTEROID,
    COMET,
    BLACKHOLE,
    SOLAR_SYSTEM,
    CONSTELLATION,
    OTHER;

    companion object {
        fun fromString(value: String): CelestialObjectType {
            return when {
                value.contains("planet", ignoreCase = true) -> PLANET
                value.contains("star", ignoreCase = true) -> STAR
                value.contains("galaxy", ignoreCase = true) -> GALAXY
                value.contains("nebula", ignoreCase = true) -> NEBULA
                value.contains("moon", ignoreCase = true) -> MOON
                value.contains("asteroid", ignoreCase = true) -> ASTEROID
                value.contains("comet", ignoreCase = true) -> COMET
                value.contains("black hole", ignoreCase = true) -> BLACKHOLE
                value.contains("solar system", ignoreCase = true) -> SOLAR_SYSTEM
                value.contains("constellation", ignoreCase = true) -> CONSTELLATION
                else -> OTHER
            }
        }
    }
}