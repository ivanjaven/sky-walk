// features/ar/domain/models/Vector3.kt

package com.example.skywalk.features.ar.domain.models

import kotlin.math.sqrt

data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0) {
            Vector3(x / len, y / len, z / len)
        } else {
            this
        }
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
}