// features/ar/domain/models/DeviceOrientation.kt

package com.example.skywalk.features.ar.domain.models

data class DeviceOrientation(
    val azimuth: Float,  // Rotation around the Z axis (0 = North, 90 = East, etc.)
    val pitch: Float,    // Rotation around the X axis (tilt up/down)
    val roll: Float      // Rotation around the Y axis (tilt left/right)
)