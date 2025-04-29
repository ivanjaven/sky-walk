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