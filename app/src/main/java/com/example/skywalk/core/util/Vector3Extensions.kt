package com.example.skywalk.core.util

import com.google.ar.sceneform.math.Vector3
import kotlin.math.sqrt

/**
 * Returns a normalized copy of this vector (a vector with the same direction but with length of 1)
 */
fun Vector3.normalized(): Vector3 {
    val magnitude = sqrt(x * x + y * y + z * z)
    return if (magnitude > 0) {
        Vector3(x / magnitude, y / magnitude, z / magnitude)
    } else {
        Vector3(0f, 0f, 0f)
    }
}