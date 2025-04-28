// features/ar/domain/models/CelestialObject.kt

package com.example.skywalk.features.ar.domain.models

import androidx.annotation.DrawableRes

data class CelestialObject(
    val name: String,
    val skyCoordinate: SkyCoordinate,
    val magnitude: Float,
    @DrawableRes val imageResourceId: Int,
    val type: String
)