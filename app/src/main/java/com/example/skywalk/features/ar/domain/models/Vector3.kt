package com.example.skywalk.features.ar.domain.models

import kotlin.math.sqrt
import kotlin.math.acos
import kotlin.math.PI

/**
 * A 3D vector class optimized for astronomical and AR calculations.
 * Designed to be fast and memory-efficient for frequent calculations.
 */
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    /**
     * Calculate the length (magnitude) of the vector.
     */
    fun length(): Float = sqrt(lengthSquared())

    /**
     * Calculate the squared length (squared magnitude) of the vector.
     * Use this instead of length() when possible for better performance.
     */
    fun lengthSquared(): Float = x * x + y * y + z * z

    /**
     * Create a normalized version of this vector (unit vector with same direction).
     * Returns the original vector if length is zero to avoid NaN values.
     */
    fun normalize(): Vector3 {
        val len = length()
        return if (len > 1e-6f) {
            val invLen = 1.0f / len
            Vector3(x * invLen, y * invLen, z * invLen)
        } else {
            // Return original vector if length is essentially zero
            this
        }
    }

    /**
     * Modify this vector to be normalized.
     * Returns this vector for chaining.
     */
    fun normalizeInPlace(): Vector3 {
        val len = length()
        if (len > 1e-6f) {
            val invLen = 1.0f / len
            return Vector3(x * invLen, y * invLen, z * invLen)
        }
        return this
    }

    /**
     * Calculate the dot product with another vector.
     */
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    /**
     * Calculate the cross product with another vector.
     */
    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    /**
     * Calculate the angle between this vector and another vector in radians.
     */
    fun angleBetween(other: Vector3): Float {
        val dot = this.dot(other)
        val lenProduct = this.length() * other.length()

        // Handle potential floating point errors
        if (lenProduct < 1e-6f) return 0f

        val cosAngle = (dot / lenProduct).coerceIn(-1f, 1f)
        return acos(cosAngle)
    }

    /**
     * Calculate the distance to another vector.
     */
    fun distanceTo(other: Vector3): Float = (this - other).length()

    /**
     * Calculate the squared distance to another vector.
     * Use this instead of distanceTo() when possible for better performance.
     */
    fun distanceToSquared(other: Vector3): Float =
        (x - other.x).let { it * it } +
                (y - other.y).let { it * it } +
                (z - other.z).let { it * it }

    /**
     * Linear interpolation between this vector and another.
     * @param other The vector to interpolate towards
     * @param t Interpolation factor (0-1)
     * @return Interpolated vector
     */
    fun lerp(other: Vector3, t: Float): Vector3 = Vector3(
        x + (other.x - x) * t,
        y + (other.y - y) * t,
        z + (other.z - z) * t
    )

    /**
     * Calculate spherical linear interpolation (slerp) between this vector and another.
     * This is useful for interpolating between directions on a sphere.
     */
    fun slerp(other: Vector3, t: Float): Vector3 {
        val omega = angleBetween(other)

        // If vectors are nearly identical, use linear interpolation instead
        if (omega < 1e-6f) {
            return lerp(other, t)
        }

        val sinOmega = kotlin.math.sin(omega)
        val invSinOmega = 1.0f / sinOmega

        val scale0 = kotlin.math.sin((1 - t) * omega) * invSinOmega
        val scale1 = kotlin.math.sin(t * omega) * invSinOmega

        return Vector3(
            x * scale0 + other.x * scale1,
            y * scale0 + other.y * scale1,
            z * scale0 + other.z * scale1
        )
    }

    /**
     * Check if this vector is nearly equal to another vector.
     */
    fun isNearlyEqual(other: Vector3, tolerance: Float = 1e-4f): Boolean {
        return abs(x - other.x) < tolerance &&
                abs(y - other.y) < tolerance &&
                abs(z - other.z) < tolerance
    }

    // Operator overloads for vector arithmetic
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    companion object {
        // Useful constant vectors
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UNIT_X = Vector3(1f, 0f, 0f)
        val UNIT_Y = Vector3(0f, 1f, 0f)
        val UNIT_Z = Vector3(0f, 0f, 1f)

        /**
         * Create a vector from spherical coordinates (useful for astronomical calculations).
         * @param azimuth Angle in the XY plane from X axis in radians
         * @param elevation Angle from the XY plane in radians
         * @param radius Distance from origin (default = 1)
         */
        fun fromSpherical(azimuth: Float, elevation: Float, radius: Float = 1f): Vector3 {
            val cosElevation = kotlin.math.cos(elevation)
            return Vector3(
                radius * cosElevation * kotlin.math.cos(azimuth),
                radius * cosElevation * kotlin.math.sin(azimuth),
                radius * kotlin.math.sin(elevation)
            )
        }

        /**
         * Calculate the absolute value of a float.
         */
        private fun abs(value: Float): Float = if (value < 0) -value else value
    }
}