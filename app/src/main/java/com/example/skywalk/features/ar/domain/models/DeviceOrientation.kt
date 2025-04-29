package com.example.skywalk.features.ar.domain.models

import kotlin.math.PI

/**
 * Represents the device's orientation in 3D space using Euler angles.
 */
data class DeviceOrientation(
    val azimuth: Float,  // Rotation around the Z axis (0 = North, 90 = East, etc.) in degrees
    val pitch: Float,    // Rotation around the X axis (tilt up/down) in degrees
    val roll: Float      // Rotation around the Y axis (tilt left/right) in degrees
) {
    /**
     * Convert this device orientation to a look direction vector.
     * The vector points in the direction the device is facing.
     */
    fun toLookVector(): Vector3 {
        val azimuthRad = azimuth * DEGREES_TO_RADIANS
        val pitchRad = pitch * DEGREES_TO_RADIANS

        // Calculate the direction vector components
        return Vector3(
            x = kotlin.math.cos(pitchRad) * kotlin.math.sin(azimuthRad),
            y = kotlin.math.cos(pitchRad) * kotlin.math.cos(azimuthRad),
            z = -kotlin.math.sin(pitchRad)
        ).normalize()
    }

    /**
     * Convert this device orientation to an up vector.
     * This represents the direction that points to the top of the device's screen.
     */
    fun toUpVector(): Vector3 {
        val azimuthRad = azimuth * DEGREES_TO_RADIANS
        val pitchRad = pitch * DEGREES_TO_RADIANS
        val rollRad = roll * DEGREES_TO_RADIANS

        // Start with the standard up vector (0, 0, 1) and apply rotations
        // First rotate around X by pitch
        val x = -kotlin.math.sin(rollRad)
        val y = kotlin.math.cos(rollRad) * kotlin.math.cos(pitchRad)
        val z = kotlin.math.cos(rollRad) * kotlin.math.sin(pitchRad)

        // Then rotate around Z by azimuth
        return Vector3(
            x = x * kotlin.math.cos(azimuthRad) - y * kotlin.math.sin(azimuthRad),
            y = x * kotlin.math.sin(azimuthRad) + y * kotlin.math.cos(azimuthRad),
            z = z
        ).normalize()
    }

    /**
     * Calculate the sky coordinate (RA/Dec) that this orientation is pointing at.
     * This is the inverse of the transformation in AstronomyUtils.
     */
    fun toSkyCoordinate(): SkyCoordinate {
        // Convert to horizontal coordinates first (azimuth/altitude)
        val altitude = -pitch // Altitude is negative pitch in this context

        // Then convert horizontal to equatorial (RA/Dec)
        // This is simplified - in a real app we would need location and time
        return SkyCoordinate(
            rightAscension = azimuth,
            declination = altitude
        )
    }

    /**
     * Format the orientation angles as a readable string.
     */
    fun formatAngles(): String {
        return String.format(
            "Azimuth: %.1f°, Pitch: %.1f°, Roll: %.1f°",
            azimuth, pitch, roll
        )
    }

    companion object {
        private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f

        /**
         * Create a DeviceOrientation from a rotation matrix.
         * @param rotationMatrix 3x3 rotation matrix
         */
        fun fromRotationMatrix(rotationMatrix: FloatArray): DeviceOrientation {
            // Extract Euler angles from rotation matrix
            val orientationAngles = FloatArray(3)
            android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles)

            return DeviceOrientation(
                azimuth = (orientationAngles[0] * 180f / PI.toFloat() + 360f) % 360f,
                pitch = orientationAngles[1] * 180f / PI.toFloat(),
                roll = orientationAngles[2] * 180f / PI.toFloat()
            )
        }
    }
}