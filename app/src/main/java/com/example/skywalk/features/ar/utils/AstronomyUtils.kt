// features/ar/utils/AstronomyUtils.kt

package com.example.skywalk.features.ar.utils

import android.location.Location
import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Vector3
import java.util.Calendar
import java.util.Date
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object AstronomyUtils {
    private const val DEGREES_TO_RADIANS = Math.PI.toFloat() / 180f
    private const val RADIANS_TO_DEGREES = 180f / Math.PI.toFloat()

    // Convert RA and Dec to a 3D unit vector (geocentric coordinates)
    fun raDecToVector(ra: Float, dec: Float): Vector3 {
        val raRad = ra * DEGREES_TO_RADIANS
        val decRad = dec * DEGREES_TO_RADIANS

        return Vector3(
            x = cos(raRad) * cos(decRad),
            y = sin(raRad) * cos(decRad),
            z = sin(decRad)
        )
    }

    // Convert a 3D unit vector to RA and Dec
    fun vectorToRaDec(vector: Vector3): SkyCoordinate {
        val ra = atan2(vector.y, vector.x) * RADIANS_TO_DEGREES
        val dec = asin(vector.z) * RADIANS_TO_DEGREES

        // Normalize RA to be between 0 and 360
        val normalizedRa = if (ra < 0) ra + 360f else ra

        return SkyCoordinate(normalizedRa, dec)
    }

    // Calculate the local sidereal time in degrees
    fun calculateSiderealTime(date: Date, longitude: Float): Float {
        val calendar = Calendar.getInstance()
        calendar.time = date

        // Days since J2000.0 epoch
        val jd = julianDay(date)
        val daysSinceJ2000 = jd - 2451545.0f

        // Greenwich Mean Sidereal Time in degrees
        val gmst = 280.46061837f + 360.98564736629f * daysSinceJ2000

        // Local Mean Sidereal Time
        val lmst = (gmst + longitude) % 360f
        return if (lmst < 0) lmst + 360f else lmst
    }

    // Calculate Julian day from date
    private fun julianDay(date: Date): Float {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)

        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3

        var jd = (day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045).toFloat()

        // Add time of day
        jd += (hour - 12) / 24.0f + minute / 1440.0f + second / 86400.0f

        return jd
    }

    // Transform celestial coordinates to screen coordinates based on device orientation
    fun celestialToScreenCoordinates(
        skyCoordinate: SkyCoordinate,
        deviceOrientation: Vector3,
        deviceUp: Vector3,
        fieldOfView: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        // Convert sky coordinate to a vector
        val objectVector = raDecToVector(
            skyCoordinate.rightAscension,
            skyCoordinate.declination
        )

        // Dot product to determine if object is in front of the device
        val dotProduct = objectVector.dot(deviceOrientation)

        // If object is behind the device, don't show it
        if (dotProduct <= 0) {
            return null
        }

        // Calculate the angle between the device orientation and the object
        val angle = Math.acos(dotProduct.toDouble()).toFloat()

        // If the object is outside the field of view, don't show it
        if (angle > fieldOfView / 2) {
            return null
        }

        // Project the object onto the plane perpendicular to the device orientation
        val distanceToPlane = 1.0f / dotProduct

        // Calculate the projected position
        val projectedVector = Vector3(
            x = objectVector.x * distanceToPlane,
            y = objectVector.y * distanceToPlane,
            z = objectVector.z * distanceToPlane
        )

        // Calculate the vector from the device to the projected position
        val toProjected = Vector3(
            x = projectedVector.x - deviceOrientation.x,
            y = projectedVector.y - deviceOrientation.y,
            z = projectedVector.z - deviceOrientation.z
        )

        // Calculate the right vector (cross product of device orientation and up)
        val rightVector = deviceOrientation.cross(deviceUp).normalize()

        // Calculate horizontal and vertical offsets
        val horizontalOffset = toProjected.dot(rightVector)
        val verticalOffset = toProjected.dot(deviceUp)

        // Convert to screen coordinates
        val screenX = (screenWidth / 2) + (horizontalOffset * screenWidth / (2 * Math.tan(fieldOfView / 2.0).toFloat()))
        val screenY = (screenHeight / 2) - (verticalOffset * screenWidth / (2 * Math.tan(fieldOfView / 2.0).toFloat()))

        return Pair(screenX, screenY)
    }

    // Calculate approximate positions of solar system objects
    // This is a simplified version - real calculations would be more complex
    fun calculatePlanetPosition(planetName: String, date: Date): SkyCoordinate {
        // These are very simplified approximations
        // In a real implementation, you'd use proper orbital elements and formulas

        // Number of days since J2000.0
        val jd = julianDay(date)
        val t = (jd - 2451545.0f) / 36525.0f // Julian centuries since J2000.0

        return when (planetName) {
            "Sun" -> {
                // The Sun moves about 1 degree per day along the ecliptic
                val eclipticLongitude = (280.46f + 0.9856474f * (jd - 2451545.0f)) % 360f
                val eclipticObliquity = 23.439f - 0.0000004f * (jd - 2451545.0f)

                // Convert ecliptic coordinates to equatorial (RA/Dec)
                val ra = atan2(
                    sin(eclipticLongitude * DEGREES_TO_RADIANS) * cos(eclipticObliquity * DEGREES_TO_RADIANS),
                    cos(eclipticLongitude * DEGREES_TO_RADIANS)
                ) * RADIANS_TO_DEGREES

                val dec = asin(
                    sin(eclipticLongitude * DEGREES_TO_RADIANS) * sin(eclipticObliquity * DEGREES_TO_RADIANS)
                ) * RADIANS_TO_DEGREES

                SkyCoordinate(ra, dec)
            }
            "Mercury" -> {
                // Very simplified Mercury position
                val meanLongitude = (252.25f + 4.0923344368f * (jd - 2451545.0f)) % 360f
                val ra = (meanLongitude + 20f * sin((meanLongitude + 40f) * DEGREES_TO_RADIANS)) % 360f
                val dec = 5f * sin(meanLongitude * DEGREES_TO_RADIANS)

                SkyCoordinate(ra, dec)
            }
            "Venus" -> {
                // Very simplified Venus position
                val meanLongitude = (181.98f + 1.6021302244f * (jd - 2451545.0f)) % 360f
                val ra = (meanLongitude + 10f * sin((meanLongitude + 20f) * DEGREES_TO_RADIANS)) % 360f
                val dec = 3f * sin(meanLongitude * DEGREES_TO_RADIANS)

                SkyCoordinate(ra, dec)
            }
            "Mars" -> {
                // Very simplified Mars position
                val meanLongitude = (355.45f + 0.5240207766f * (jd - 2451545.0f)) % 360f
                val ra = (meanLongitude + 15f * sin((meanLongitude + 30f) * DEGREES_TO_RADIANS)) % 360f
                val dec = 5f * sin(meanLongitude * DEGREES_TO_RADIANS)

                SkyCoordinate(ra, dec)
            }
            "Jupiter" -> {
                // Very simplified Jupiter position
                val meanLongitude = (34.40f + 0.0830853001f * (jd - 2451545.0f)) % 360f
                val ra = (meanLongitude + 5f * sin((meanLongitude + 10f) * DEGREES_TO_RADIANS)) % 360f
                val dec = 1.3f * sin(meanLongitude * DEGREES_TO_RADIANS)

                SkyCoordinate(ra, dec)
            }
            "Saturn" -> {
                // Very simplified Saturn position
                val meanLongitude = (50.58f + 0.0334442282f * (jd - 2451545.0f)) % 360f
                val ra = (meanLongitude + 6f * sin((meanLongitude + 15f) * DEGREES_TO_RADIANS)) % 360f
                val dec = 2.5f * sin(meanLongitude * DEGREES_TO_RADIANS)

                SkyCoordinate(ra, dec)
            }
            else -> SkyCoordinate(0f, 0f)
        }
    }
}