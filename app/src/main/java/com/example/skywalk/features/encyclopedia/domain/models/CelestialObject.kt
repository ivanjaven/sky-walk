package com.example.skywalk.features.encyclopedia.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CelestialObject(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val dateCreated: String,
    val type: CelestialObjectType,
    val keywords: List<String>,
    val source: String
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