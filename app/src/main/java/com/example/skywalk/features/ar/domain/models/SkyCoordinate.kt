// features/ar/domain/models/SkyCoordinate.kt

package com.example.skywalk.features.ar.domain.models

data class SkyCoordinate(
    val rightAscension: Float,  // In degrees (0-360)
    val declination: Float      // In degrees (-90 to +90)
)