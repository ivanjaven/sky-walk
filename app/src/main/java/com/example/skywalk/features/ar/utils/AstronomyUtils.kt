package com.example.skywalk.features.ar.utils

import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Vector3
import timber.log.Timber
import java.util.Date
import kotlin.math.*

object AstronomyUtils {
    private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
    private const val RADIANS_TO_DEGREES = 180f / PI.toFloat()

    // Obliquity of the ecliptic (angle between Earth's equator and the ecliptic plane)
    private const val OBLIQUITY = 23.439281f * DEGREES_TO_RADIANS

    // Convert RA and Dec to a 3D unit vector (geocentric coordinates)
    // This follows Stardroid's approach in CoordinateManipulations.kt
    fun raDecToVector(ra: Float, dec: Float): Vector3 {
        val raRad = ra * DEGREES_TO_RADIANS
        val decRad = dec * DEGREES_TO_RADIANS

        return Vector3(
            x = cos(raRad) * cos(decRad),
            y = sin(raRad) * cos(decRad),
            z = sin(decRad)
        ).normalize()
    }

    // This is the key function for accurate panning
    // Based on Stardroid's approach, but simplified for our needs
    fun celestialToScreenCoordinates(
        skyCoordinate: SkyCoordinate,
        deviceLookVector: Vector3,
        deviceUpVector: Vector3,
        fieldOfView: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Float, Float>? {
        // Convert sky coordinate to a unit vector
        val objectVector = raDecToVector(
            skyCoordinate.rightAscension,
            skyCoordinate.declination
        )

        // We need the right vector to complete the device's coordinate system
        // This is calculated the same way as in Stardroid
        val deviceRightVector = deviceLookVector.cross(deviceUpVector).normalize()

        // Calculate the dot product to determine if the object is in front
        val lookDot = deviceLookVector.dot(objectVector)

        // Stardroid uses a more sophisticated approach, but for our purposes
        // we can simply check if the object is in front
        if (lookDot > 0) {
            // Calculate projection onto the view plane
            // This is a key insight from Stardroid - project the vectors properly

            // First, we need to calculate how much the object vector differs from look vector
            val viewPlaneVector = objectVector.minus(deviceLookVector.times(lookDot))

            // Then project this difference onto our right and up vectors
            val rightComponent = viewPlaneVector.dot(deviceRightVector)
            val upComponent = viewPlaneVector.dot(deviceUpVector)

            // Account for perspective projection
            val tanFov = tan(fieldOfView / 2f)
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

            // The more accurate scaling factors from the Stardroid approach
            val scaleFactor = 1f / tanFov

            // Calculate screen position
            val screenX = screenWidth / 2f + rightComponent * scaleFactor * screenWidth / 2f / aspectRatio
            val screenY = screenHeight / 2f - upComponent * scaleFactor * screenHeight / 2f

            return Pair(screenX, screenY)
        }

        // Object is behind us
        return null
    }

    // Calculate Julian Day
    private fun julianDay(date: Date): Float {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date

        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1 // Calendar months are 0-based
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val second = calendar.get(java.util.Calendar.SECOND)

        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3

        var jd = (day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045).toFloat()

        // Add time of day
        jd += (hour - 12) / 24.0f + minute / 1440.0f + second / 86400.0f

        return jd
    }

    // Calculate local sidereal time (in degrees)
    private fun calculateSiderealTime(date: Date, longitude: Float): Float {
        val jd = julianDay(date)
        val daysSinceJ2000 = jd - 2451545.0f

        // Greenwich Mean Sidereal Time in degrees
        val T = daysSinceJ2000 / 36525.0f // Julian centuries
        var gmst = 280.46061837f + 360.98564736629f * daysSinceJ2000 +
                0.000387933f * T * T - T * T * T / 38710000f

        // Normalize to 0-360
        gmst = (gmst % 360f + 360f) % 360f

        // Local Mean Sidereal Time
        val lmst = (gmst + longitude) % 360f
        return if (lmst < 0) lmst + 360f else lmst
    }

    // Calculate Sun position
    fun calculateSunPosition(date: Date, latitude: Float, longitude: Float): SkyCoordinate {
        val jd = julianDay(date)
        val T = (jd - 2451545.0f) / 36525.0f // Julian centuries since J2000.0

        // Mean longitude of the Sun
        val L0 = (280.46646f + 36000.76983f * T + 0.0003032f * T * T) % 360f

        // Mean anomaly of the Sun
        val M = (357.52911f + 35999.05029f * T - 0.0001537f * T * T) % 360f
        val MRad = M * DEGREES_TO_RADIANS

        // Equation of center
        val C = (1.914602f - 0.004817f * T - 0.000014f * T * T) * sin(MRad) +
                (0.019993f - 0.000101f * T) * sin(2f * MRad) +
                0.000289f * sin(3f * MRad)

        // True longitude of the Sun
        val trueLongitude = (L0 + C) % 360f
        val trueLongitudeRad = trueLongitude * DEGREES_TO_RADIANS

        // Convert ecliptic coordinates to equatorial (RA/Dec)
        val raRad = atan2(
            cos(OBLIQUITY) * sin(trueLongitudeRad),
            cos(trueLongitudeRad)
        )

        val decRad = asin(
            sin(OBLIQUITY) * sin(trueLongitudeRad)
        )

        // Convert to degrees
        var ra = raRad * RADIANS_TO_DEGREES
        if (ra < 0) ra += 360f
        val dec = decRad * RADIANS_TO_DEGREES

        // Calculate local sidereal time
        val lst = calculateSiderealTime(date, longitude)

        // Convert from RA to hour angle based on local sidereal time
        val hourAngle = (lst - ra + 360f) % 360f

        // Convert to observed altitude and azimuth
        return equatorialToHorizontal(hourAngle, dec, latitude)
    }

    // Calculate Moon position
    fun calculateMoonPosition(date: Date, latitude: Float, longitude: Float): SkyCoordinate {
        val jd = julianDay(date)
        val T = (jd - 2451545.0f) / 36525.0f // Julian centuries since J2000.0

        // Mean longitude of the Moon (L')
        val Lp = (218.3164477f + 481267.88123421f * T - 0.0015786f * T * T) % 360f

        // Mean elongation of the Moon (D)
        val D = (297.8501921f + 445267.1114034f * T - 0.0018819f * T * T) % 360f
        val DRad = D * DEGREES_TO_RADIANS

        // Mean anomaly of the Sun (M)
        val M = (357.5291092f + 35999.0502909f * T - 0.0001536f * T * T) % 360f
        val MRad = M * DEGREES_TO_RADIANS

        // Mean anomaly of the Moon (M')
        val Mp = (134.9633964f + 477198.8675055f * T + 0.0087414f * T * T) % 360f
        val MpRad = Mp * DEGREES_TO_RADIANS

        // Moon's argument of latitude (F)
        val F = (93.2720950f + 483202.0175233f * T - 0.0036539f * T * T) % 360f
        val FRad = F * DEGREES_TO_RADIANS

        // Corrections
        val E = 1.0f - 0.002516f * T - 0.0000074f * T * T // Earth's orbital eccentricity

        // Simplified corrections for longitude
        val dL = 6.288774f * sin(MpRad) +
                1.274027f * sin(2*DRad - MpRad) +
                0.658314f * sin(2*DRad) +
                0.213618f * sin(2*MpRad) -
                0.185116f * E * sin(MRad)

        // Simplified corrections for latitude
        val dB = 5.128122f * sin(FRad) +
                0.280602f * sin(MpRad + FRad) +
                0.277693f * sin(MpRad - FRad)

        // Ecliptic longitude and latitude
        val lambda = (Lp + dL) % 360f
        val beta = dB

        // Convert from ecliptic to equatorial coordinates
        val lambdaRad = lambda * DEGREES_TO_RADIANS
        val betaRad = beta * DEGREES_TO_RADIANS

        val raRad = atan2(
            sin(lambdaRad) * cos(OBLIQUITY) - tan(betaRad) * sin(OBLIQUITY),
            cos(lambdaRad)
        )

        val decRad = asin(
            sin(betaRad) * cos(OBLIQUITY) + cos(betaRad) * sin(OBLIQUITY) * sin(lambdaRad)
        )

        // Convert to degrees
        var ra = raRad * RADIANS_TO_DEGREES
        if (ra < 0) ra += 360f
        val dec = decRad * RADIANS_TO_DEGREES

        // Calculate local sidereal time
        val lst = calculateSiderealTime(date, longitude)

        // Convert from RA to hour angle based on local sidereal time
        val hourAngle = (lst - ra + 360f) % 360f

        // Convert to horizontal coordinates
        return equatorialToHorizontal(hourAngle, dec, latitude)
    }

    // Calculate planet position
    fun calculatePlanetPosition(date: Date, latitude: Float, longitude: Float, planetName: String): SkyCoordinate {
        // For simplicity, we'll distribute the planets in a visible arc across the sky
        // In a real implementation, we'd use proper orbital calculations

        // Create a distribution of planets that will be visible in the sky
        val baseAzimuth = when (planetName) {
            "Mercury" -> 160f
            "Venus" -> 200f
            "Mars" -> 240f
            "Jupiter" -> 280f
            "Saturn" -> 320f
            else -> 0f
        }

        // Put them at a good altitude for viewing
        val altitude = 25f

        // Return a SkyCoordinate with the azimuth and altitude
        return SkyCoordinate(baseAzimuth, altitude)
    }

    // Convert equatorial coordinates to horizontal (azimuth/altitude)
    private fun equatorialToHorizontal(hourAngle: Float, declination: Float, latitude: Float): SkyCoordinate {
        val hourAngleRad = hourAngle * DEGREES_TO_RADIANS
        val decRad = declination * DEGREES_TO_RADIANS
        val latRad = latitude * DEGREES_TO_RADIANS

        // Calculate altitude (elevation above horizon)
        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(hourAngleRad)
        val altRad = asin(sinAlt)

        // Calculate azimuth (angle from north, clockwise)
        val sinA = sin(hourAngleRad) * cos(decRad) / cos(altRad)
        val cosA = (sin(decRad) - sin(latRad) * sinAlt) / (cos(latRad) * cos(altRad))

        var azRad = atan2(sinA, cosA)
        if (azRad < 0) azRad += 2 * PI.toFloat()

        // Convert from astronomical azimuth (N=0, E=90) to SkyCoordinate
        // Use azimuth as rightAscension and altitude as declination for the AR view
        return SkyCoordinate(
            rightAscension = azRad * RADIANS_TO_DEGREES,
            declination = altRad * RADIANS_TO_DEGREES
        )
    }
}