package com.example.skywalk.features.ar.utils

import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Vector3
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import kotlin.math.*

object AstronomyUtils {
    private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
    private const val RADIANS_TO_DEGREES = 180f / PI.toFloat()

    // Solar obliquity - the tilt of Earth's axis relative to the ecliptic plane
    private const val OBLIQUITY = 23.439281f * DEGREES_TO_RADIANS

    // Convert RA and Dec to a 3D unit vector (geocentric coordinates)
    fun raDecToVector(ra: Float, dec: Float): Vector3 {
        val raRad = ra * DEGREES_TO_RADIANS
        val decRad = dec * DEGREES_TO_RADIANS

        return Vector3(
            x = cos(raRad) * cos(decRad),
            y = sin(raRad) * cos(decRad),
            z = sin(decRad)
        ).normalize()  // Ensure it's normalized
    }

    // Convert a 3D unit vector to RA and Dec
    fun vectorToRaDec(vector: Vector3): SkyCoordinate {
        val normalizedVector = vector.normalize()
        val ra = atan2(normalizedVector.y, normalizedVector.x) * RADIANS_TO_DEGREES
        val dec = asin(normalizedVector.z) * RADIANS_TO_DEGREES

        // Normalize RA to be between 0 and 360
        val normalizedRa = if (ra < 0) ra + 360f else ra

        return SkyCoordinate(normalizedRa, dec)
    }

    // Calculate the local sidereal time in degrees
    fun calculateSiderealTime(date: Date, longitude: Float): Float {
        // Days since J2000.0 epoch
        val jd = julianDay(date)
        val daysSinceJ2000 = jd - 2451545.0f

        // Greenwich Mean Sidereal Time in degrees
        // More accurate formula for GMST
        val T = daysSinceJ2000 / 36525.0f  // Julian centuries
        var gmst = 280.46061837f + 360.98564736629f * daysSinceJ2000 +
                0.000387933f * T * T - T * T * T / 38710000f

        // Normalize to 0-360
        gmst = (gmst % 360f + 360f) % 360f

        // Local Mean Sidereal Time
        val lmst = (gmst + longitude) % 360f
        return if (lmst < 0) lmst + 360f else lmst
    }

    // Calculate Julian day from date
    fun julianDay(date: Date): Float {
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
    // This is the critical function for creating the stable AR effect
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

        // Get the right vector to complete our basis
        val deviceRightVector = Vector3(
            deviceLookVector.y * deviceUpVector.z - deviceLookVector.z * deviceUpVector.y,
            deviceLookVector.z * deviceUpVector.x - deviceLookVector.x * deviceUpVector.z,
            deviceLookVector.x * deviceUpVector.y - deviceLookVector.y * deviceUpVector.x
        ).normalize()

        // IMPORTANT: Use dot product to determine if the object is "in front" of the device
        val dotProduct = objectVector.x * deviceLookVector.x +
                objectVector.y * deviceLookVector.y +
                objectVector.z * deviceLookVector.z

        // A very liberal check for testing - allow objects even slightly in front
        // Normally we'd check if dotProduct > 0 for objects in front
        // For debugging, we're much more permissive
        if (dotProduct > -0.5f) {
            Timber.d("Object is potentially visible with dot product: $dotProduct")

            // Project the object vector onto the device's view plane
            val rightComponent = objectVector.x * deviceRightVector.x +
                    objectVector.y * deviceRightVector.y +
                    objectVector.z * deviceRightVector.z

            val upComponent = objectVector.x * deviceUpVector.x +
                    objectVector.y * deviceUpVector.y +
                    objectVector.z * deviceUpVector.z

            // Convert to normalized device coordinates (-1 to 1)
            val tanHalfFov = kotlin.math.tan(fieldOfView / 2)
            val aspectRatio = screenWidth.toFloat() / screenHeight

            // Scale and account for perspective projection
            val scaleFactor = 0.5f / tanHalfFov  // Simplified projection for testing

            // Calculate screen position
            val screenX = screenWidth / 2f + rightComponent * screenWidth * scaleFactor
            val screenY = screenHeight / 2f - upComponent * screenHeight * scaleFactor

            Timber.d("Converted to screen position: ($screenX, $screenY)")

            return Pair(screenX, screenY)
        }

        Timber.d("Object is behind camera with dot product: $dotProduct")
        return null
    }

    // Calculate positions of solar system objects
    fun calculateCelestialPosition(
        objectName: String,
        date: Date,
        latitude: Float,
        longitude: Float
    ): SkyCoordinate {
        val jd = julianDay(date)
        val T = (jd - 2451545.0f) / 36525.0f // Julian centuries since J2000.0

        // Calculate local sidereal time for coordinate transformations
        val lst = calculateSiderealTime(date, longitude)

        return when (objectName) {
            "Sun" -> calculateSunPosition(T, lst, latitude)
            "Moon" -> calculateMoonPosition(T, lst, latitude)
            "Mercury", "Venus", "Mars", "Jupiter", "Saturn" ->
                calculatePlanetPosition(T, lst, latitude, objectName)
            else -> SkyCoordinate(0f, 0f)
        }
    }

    // Calculate Sun position with better accuracy
    private fun calculateSunPosition(T: Float, lst: Float, latitude: Float): SkyCoordinate {
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

        // Convert from RA to hour angle based on local sidereal time
        val hourAngle = (lst - ra + 360f) % 360f

        // Convert to observed altitude and azimuth
        return equatorialToHorizontal(hourAngle, dec, latitude)
    }

    private fun calculateMoonPosition(T: Float, lst: Float, latitude: Float): SkyCoordinate {
        // Mean longitude of the Moon (L')
        val Lp = (218.3164477f + 481267.88123421f * T - 0.0015786f * T * T + T * T * T / 538841f - T * T * T * T / 65194000f) % 360f

        // Mean elongation of the Moon (D)
        val D = (297.8501921f + 445267.1114034f * T - 0.0018819f * T * T + T * T * T / 545868f - T * T * T * T / 113065000f) % 360f
        val DRad = D * DEGREES_TO_RADIANS

        // Mean anomaly of the Sun (M)
        val M = (357.5291092f + 35999.0502909f * T - 0.0001536f * T * T + T * T * T / 24490000f) % 360f
        val MRad = M * DEGREES_TO_RADIANS

        // Mean anomaly of the Moon (M')
        val Mp = (134.9633964f + 477198.8675055f * T + 0.0087414f * T * T + T * T * T / 69699f - T * T * T * T / 14712000f) % 360f
        val MpRad = Mp * DEGREES_TO_RADIANS

        // Moon's argument of latitude (F)
        val F = (93.2720950f + 483202.0175233f * T - 0.0036539f * T * T - T * T * T / 3526000f + T * T * T * T / 863310000f) % 360f
        val FRad = F * DEGREES_TO_RADIANS

        // Corrections for the Moon's longitude
        val E = 1.0f - 0.002516f * T - 0.0000074f * T * T // correction for Earth's orbital eccentricity

        // Longitude corrections (simplified, using the largest terms)
        val dL = 6.288774f * sin(MpRad) +
                1.274027f * sin(2*DRad - MpRad) +
                0.658314f * sin(2*DRad) +
                0.213618f * sin(2*MpRad) -
                0.185116f * E * sin(MRad) -
                0.114332f * sin(2*FRad) +
                0.058793f * sin(2*DRad - 2*MpRad) +
                0.057066f * E * sin(2*DRad - MRad - MpRad)

        // Latitude corrections (simplified)
        val dB = 5.128122f * sin(FRad) +
                0.280602f * sin(MpRad + FRad) +
                0.277693f * sin(MpRad - FRad) +
                0.173237f * sin(2*DRad - FRad)

        // Ecliptic longitude and latitude
        val lambda = (Lp + dL) % 360f
        val beta = dB // Already in degrees

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

        // Convert from RA to hour angle based on local sidereal time
        val hourAngle = (lst - ra + 360f) % 360f

        // Convert to horizontal coordinates
        return equatorialToHorizontal(hourAngle, dec, latitude)
    }

    private fun calculatePlanetPosition(T: Float, lst: Float, latitude: Float, planetName: String): SkyCoordinate {
        // Get orbital elements for the planet
        val orbitalElements = getPlanetOrbitalElements(T, planetName)
        val a = orbitalElements[0]  // semi-major axis
        val e = orbitalElements[1]  // eccentricity
        val i = orbitalElements[2]  // inclination
        val O = orbitalElements[3]  // longitude of ascending node
        val w = orbitalElements[4]  // argument of perihelion
        val L = orbitalElements[5]  // mean longitude

        // Calculate mean anomaly
        val M = (L - w + 360f) % 360f
        val MRad = M * DEGREES_TO_RADIANS

        // Solve Kepler's equation iteratively
        var E = MRad // Initial approximation
        var dE = 1f
        var iterations = 0
        while (abs(dE) > 1e-6f && iterations < 10) {
            dE = (E - e * sin(E) - MRad) / (1f - e * cos(E))
            E -= dE
            iterations++
        }

        // Calculate true anomaly
        val v = 2f * atan2(sqrt((1f + e) / (1f - e)) * sin(E / 2f), cos(E / 2f))

        // Calculate heliocentric distance
        val r = a * (1f - e * cos(E))

        // Calculate heliocentric rectangular coordinates
        val xh = r * (cos(O) * cos(v + w - O) - sin(O) * sin(v + w - O) * cos(i))
        val yh = r * (sin(O) * cos(v + w - O) + cos(O) * sin(v + w - O) * cos(i))
        val zh = r * sin(v + w - O) * sin(i)

        // Get Earth's position (simplified)
        val earthElements = getPlanetOrbitalElements(T, "Earth")
        val earthL = earthElements[5]
        val earthW = earthElements[4]
        val earthMRad = ((earthL - earthW + 360f) % 360f) * DEGREES_TO_RADIANS

        // Solve Kepler's equation for Earth
        var earthE = earthMRad
        for (j in 0..5) {
            earthE = earthMRad + earthElements[1] * sin(earthE)
        }

        val earthV = 2f * atan2(
            sqrt((1f + earthElements[1]) / (1f - earthElements[1])) * sin(earthE / 2f),
            cos(earthE / 2f)
        )

        val earthR = earthElements[0] * (1f - earthElements[1] * cos(earthE))

        // Earth's heliocentric coordinates
        val xe = earthR * cos(earthV + earthW)
        val ye = earthR * sin(earthV + earthW)
        val ze = 0f // Earth is our reference plane

        // Calculate geocentric coordinates
        val x = xh - xe
        val y = yh - ye
        val z = zh - ze

        // Convert to geocentric ecliptic coordinates
        val geocentricLon = atan2(y, x)
        val geocentricLat = atan2(z, sqrt(x*x + y*y))

        // Convert to equatorial coordinates
        val raRad = atan2(
            sin(geocentricLon) * cos(OBLIQUITY) - tan(geocentricLat) * sin(OBLIQUITY),
            cos(geocentricLon)
        )

        val decRad = asin(
            sin(geocentricLat) * cos(OBLIQUITY) +
                    cos(geocentricLat) * sin(OBLIQUITY) * sin(geocentricLon)
        )

        // Convert to degrees
        var ra = raRad * RADIANS_TO_DEGREES
        if (ra < 0) ra += 360f
        val dec = decRad * RADIANS_TO_DEGREES

        // Convert from RA to hour angle based on local sidereal time
        val hourAngle = (lst - ra + 360f) % 360f

        // Convert to horizontal coordinates
        return equatorialToHorizontal(hourAngle, dec, latitude)
    }

    // More accurate orbital elements for planets
    private fun getPlanetOrbitalElements(T: Float, planetName: String): FloatArray {
        return when (planetName) {
            "Mercury" -> {
                val a = 0.38709927f + 0.00000037f * T
                val e = 0.20563593f + 0.00001906f * T
                val i = (7.00497902f - 0.00594749f * T) * DEGREES_TO_RADIANS
                val O = (48.33076593f - 0.12534081f * T) * DEGREES_TO_RADIANS
                val w = (77.45779628f + 0.16047689f * T) * DEGREES_TO_RADIANS
                val L = ((252.25032350f + 149472.67411175f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            "Venus" -> {
                val a = 0.72333566f + 0.00000390f * T
                val e = 0.00677672f - 0.00004107f * T
                val i = (3.39467605f - 0.00078890f * T) * DEGREES_TO_RADIANS
                val O = (76.67984255f - 0.27769418f * T) * DEGREES_TO_RADIANS
                val w = (131.60246718f + 0.00268329f * T) * DEGREES_TO_RADIANS
                val L = ((181.97909950f + 58517.81538729f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            "Earth" -> {
                val a = 1.00000261f + 0.00000562f * T
                val e = 0.01671123f - 0.00004392f * T
                val i = (-0.00001531f - 0.01294668f * T) * DEGREES_TO_RADIANS
                val O = 0f // Reference plane
                val w = (102.93768193f + 0.32327364f * T) * DEGREES_TO_RADIANS
                val L = ((100.46457166f + 35999.37244981f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            "Mars" -> {
                val a = 1.52371034f + 0.00001847f * T
                val e = 0.09339410f + 0.00007882f * T
                val i = (1.84969142f - 0.00813131f * T) * DEGREES_TO_RADIANS
                val O = (49.55953891f - 0.29257343f * T) * DEGREES_TO_RADIANS
                val w = (336.04084f + 0.44441088f * T) * DEGREES_TO_RADIANS
                val L = ((355.45332f + 19140.30268499f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            "Jupiter" -> {
                val a = 5.20288700f - 0.00011607f * T
                val e = 0.04838624f - 0.00013253f * T
                val i = (1.30439695f - 0.00183714f * T) * DEGREES_TO_RADIANS
                val O = (100.47390909f + 0.20469106f * T) * DEGREES_TO_RADIANS
                val w = (14.72847983f + 0.21252668f * T) * DEGREES_TO_RADIANS
                val L = ((34.39644051f + 3034.74612775f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            "Saturn" -> {
                val a = 9.53667594f - 0.00125060f * T
                val e = 0.05386179f - 0.00050991f * T
                val i = (2.48599187f + 0.00193609f * T) * DEGREES_TO_RADIANS
                val O = (113.66242448f - 0.28867794f * T) * DEGREES_TO_RADIANS
                val w = (92.59887831f - 0.41897216f * T) * DEGREES_TO_RADIANS
                val L = ((49.95424423f + 1222.49362201f * T) % 360f) * DEGREES_TO_RADIANS
                floatArrayOf(a, e, i, O, w, L)
            }
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        }
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

        // Convert from astronomical azimuth (N=0, E=90) to RA/Dec-like coordinates
        // In Sky Map's AR view, we treat azimuth like right ascension and altitude like declination
        return SkyCoordinate(
            rightAscension = azRad * RADIANS_TO_DEGREES,
            declination = altRad * RADIANS_TO_DEGREES
        )
    }
}