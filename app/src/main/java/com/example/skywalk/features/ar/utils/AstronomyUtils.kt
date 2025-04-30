package com.example.skywalk.features.ar.utils

import com.example.skywalk.features.ar.domain.models.SkyCoordinate
import com.example.skywalk.features.ar.domain.models.Vector3
import timber.log.Timber
import java.util.Date
import java.util.Calendar
import kotlin.math.*

object AstronomyUtils {
    private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
    private const val RADIANS_TO_DEGREES = 180f / PI.toFloat()
    private const val PI2 = 2 * PI.toFloat()

    // Obliquity of the ecliptic (angle between Earth's equator and the ecliptic plane)
    private const val OBLIQUITY = 23.439281f * DEGREES_TO_RADIANS

    // Convert RA and Dec to a 3D unit vector (geocentric coordinates)
    fun raDecToVector(ra: Float, dec: Float): Vector3 {
        val raRad = ra * DEGREES_TO_RADIANS
        val decRad = dec * DEGREES_TO_RADIANS

        return Vector3(
            x = cos(raRad) * cos(decRad),
            y = sin(raRad) * cos(decRad),
            z = sin(decRad)
        ).normalize()
    }

    // Convert Azimuth and Altitude to RA and Dec
    fun horizontalToEquatorial(azimuth: Float, altitude: Float, latitude: Float, localSiderealTime: Float): SkyCoordinate {
        val azRad = azimuth * DEGREES_TO_RADIANS
        val altRad = altitude * DEGREES_TO_RADIANS
        val latRad = latitude * DEGREES_TO_RADIANS
        val lstRad = localSiderealTime * DEGREES_TO_RADIANS

        // Convert horizontal coordinates to equatorial coordinates
        val sinDec = sin(altRad) * sin(latRad) + cos(altRad) * cos(latRad) * cos(azRad)
        val decRad = asin(sinDec)

        val cosH = (sin(altRad) - sin(latRad) * sinDec) / (cos(latRad) * cos(decRad))
        val sinH = -sin(azRad) * cos(altRad) / cos(decRad)
        val hourAngleRad = atan2(sinH, cosH)

        // Convert hour angle to right ascension
        var ra = (lstRad - hourAngleRad) * RADIANS_TO_DEGREES
        // Normalize RA to [0, 360)
        ra = (ra + 360f) % 360f

        val dec = decRad * RADIANS_TO_DEGREES

        return SkyCoordinate(ra, dec)
    }

    // Convert RA and Dec to Azimuth and Altitude
    fun equatorialToHorizontal(ra: Float, dec: Float, latitude: Float, localSiderealTime: Float): SkyCoordinate {
        val raRad = ra * DEGREES_TO_RADIANS
        val decRad = dec * DEGREES_TO_RADIANS
        val latRad = latitude * DEGREES_TO_RADIANS
        val lstRad = localSiderealTime * DEGREES_TO_RADIANS

        // Calculate hour angle
        val hourAngleRad = lstRad - raRad

        // Calculate altitude
        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(hourAngleRad)
        val altRad = asin(sinAlt)

        // Calculate azimuth
        val cosAz = (sin(decRad) - sin(altRad) * sin(latRad)) / (cos(altRad) * cos(latRad))
        val sinAz = -sin(hourAngleRad) * cos(decRad) / cos(altRad)
        var azRad = atan2(sinAz, cosAz)

        // Ensure azimuth is in [0, 2π)
        if (azRad < 0) azRad += PI2

        // Convert to degrees
        val azimuth = azRad * RADIANS_TO_DEGREES
        val altitude = altRad * RADIANS_TO_DEGREES

        return SkyCoordinate(azimuth, altitude)
    }

    // This is the key function for accurate panning
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
        val deviceRightVector = deviceLookVector.cross(deviceUpVector).normalize()

        // Calculate the dot product to determine if the object is in front
        val lookDot = deviceLookVector.dot(objectVector)

        // Only show objects in front of the camera
        if (lookDot > 0) {
            // Calculate projection onto the view plane
            val viewPlaneVector = objectVector.minus(deviceLookVector.times(lookDot))

            // Project this difference onto our right and up vectors
            val rightComponent = viewPlaneVector.dot(deviceRightVector)
            val upComponent = viewPlaneVector.dot(deviceUpVector)

            // Account for perspective projection
            val tanFov = tan(fieldOfView / 2f)
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

            // The scaling factor for projection
            val scaleFactor = 1f / tanFov

            // Calculate screen position
            val screenX = screenWidth / 2f + rightComponent * scaleFactor * screenWidth / 2f / aspectRatio
            val screenY = screenHeight / 2f - upComponent * scaleFactor * screenHeight / 2f

            return Pair(screenX, screenY)
        }

        // Object is behind us
        return null
    }

    // Calculate Julian Day - more accurate formula from Stardroid
    fun julianDay(date: Date): Double {
        val cal = Calendar.getInstance()
        cal.time = date

        val year = cal[Calendar.YEAR]
        val month = cal[Calendar.MONTH] + 1
        val day = cal[Calendar.DAY_OF_MONTH]
        val hour = cal[Calendar.HOUR_OF_DAY]
        val minute = cal[Calendar.MINUTE]
        val second = cal[Calendar.SECOND]

        val hourDecimal = hour + minute / 60.0 + second / 3600.0

        // Julian date calculation using the formula from the Astronomical Almanac
        var jd = 367 * year -
                floor(7 * (year + floor((month + 9) / 12.0)) / 4.0).toInt() +
                floor(275 * month / 9.0).toInt() +
                day + 1721013.5

        // Add time of day
        jd += hourDecimal / 24.0

        return jd
    }

    // Calculate time in Julian centuries since J2000
    fun julianCenturies(date: Date): Double {
        val jd = julianDay(date)
        return (jd - 2451545.0) / 36525.0
    }

    // Calculate local mean sidereal time in degrees
    fun calculateSiderealTime(date: Date, longitude: Float): Float {
        val jd = julianDay(date)
        val t = (jd - 2451545.0) / 36525.0  // Julian centuries since J2000.0

        // Greenwich mean sidereal time at 0h UT
        var gmst = 280.46061837 +
                360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t -
                t * t * t / 38710000.0

        // Normalize to [0, 360)
        gmst = (gmst % 360.0 + 360.0) % 360.0

        // Local mean sidereal time
        var lmst = (gmst + longitude)
        lmst = (lmst % 360.0 + 360.0) % 360.0

        return lmst.toFloat()
    }

    // Calculate the Sun's position using a more accurate algorithm from Stardroid
    fun calculateSunPosition(date: Date, latitude: Float, longitude: Float): SkyCoordinate {
        val t = julianCenturies(date)

        // Mean longitude of the Sun, corrected for aberration
        val l0 = (280.46646 + 36000.76983 * t + 0.0003032 * t * t) % 360.0

        // Mean anomaly of the Sun
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) % 360.0
        val mRad = m * Math.PI / 180.0

        // Sun's equation of center
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2 * mRad) +
                0.000289 * sin(3 * mRad)

        // True longitude of the Sun
        val trueL = (l0 + c) % 360.0
        val trueL_rad = trueL * Math.PI / 180.0

        // The Sun's ecliptic coordinates
        val x = cos(trueL_rad)
        val y = cos(OBLIQUITY) * sin(trueL_rad)
        val z = sin(OBLIQUITY) * sin(trueL_rad)

        // Convert to equatorial coordinates (RA, Dec)
        var ra = atan2(y, x) * 180.0 / Math.PI
        if (ra < 0) ra += 360.0
        val dec = asin(z) * 180.0 / Math.PI

        // Get local sidereal time
        val lst = calculateSiderealTime(date, longitude)

        // Convert to horizontal coordinates
        return equatorialToHorizontal(ra.toFloat(), dec.toFloat(), latitude, lst)
    }

    // Calculate the Moon's position using a more accurate algorithm
    fun calculateMoonPosition(date: Date, latitude: Float, longitude: Float): SkyCoordinate {
        val T = julianCenturies(date)

        // Lunar mean longitude
        val L = (218.3164477 + 481267.88123421 * T - 0.0015786 * T * T) % 360.0
        val L_rad = L * Math.PI / 180.0

        // Lunar mean elongation
        val D = (297.8501921 + 445267.1114034 * T - 0.0018819 * T * T) % 360.0
        val D_rad = D * Math.PI / 180.0

        // Sun's mean anomaly
        val M = (357.5291092 + 35999.0502909 * T - 0.0001536 * T * T) % 360.0
        val M_rad = M * Math.PI / 180.0

        // Moon's mean anomaly
        val M_moon = (134.9633964 + 477198.8675055 * T + 0.0087414 * T * T) % 360.0
        val M_moon_rad = M_moon * Math.PI / 180.0

        // Moon's argument of latitude
        val F = (93.2720950 + 483202.0175233 * T - 0.0036539 * T * T) % 360.0
        val F_rad = F * Math.PI / 180.0

        // Correction for the eccentricity of Earth's orbit
        val E = 1.0 - 0.002516 * T - 0.0000074 * T * T

        // Lunar longitude perturbations (simplified)
        val longitude_perturbation =
            6.288774 * sin(M_moon_rad) +
                    1.274027 * sin(2 * D_rad - M_moon_rad) +
                    0.658314 * sin(2 * D_rad) +
                    0.213618 * sin(2 * M_moon_rad) -
                    0.185116 * E * sin(M_rad)

        // Lunar latitude perturbations (simplified)
        val latitude_perturbation =
            5.128122 * sin(F_rad) +
                    0.280602 * sin(M_moon_rad + F_rad) +
                    0.277693 * sin(M_moon_rad - F_rad)

        // Corrected longitude and latitude
        val longitude_corrected = (L + longitude_perturbation) % 360.0
        val latitude_corrected = latitude_perturbation

        // Convert to RA and Dec
        val longitude_rad = longitude_corrected * Math.PI / 180.0
        val latitude_rad = latitude_corrected * Math.PI / 180.0

        val x = cos(longitude_rad) * cos(latitude_rad)
        val y = sin(longitude_rad) * cos(latitude_rad)
        val z = sin(latitude_rad)

        // Account for ecliptic-to-equatorial coordinate transformation
        val xEq = x
        val yEq = y * cos(OBLIQUITY) - z * sin(OBLIQUITY)
        val zEq = y * sin(OBLIQUITY) + z * cos(OBLIQUITY)

        // Convert to RA and Dec
        var ra = atan2(yEq, xEq) * 180.0 / Math.PI
        if (ra < 0) ra += 360.0
        val dec = asin(zEq) * 180.0 / Math.PI

        // Get local sidereal time
        val lst = calculateSiderealTime(date, longitude)

        // Convert to horizontal coordinates
        return equatorialToHorizontal(ra.toFloat(), dec.toFloat(), latitude, lst)
    }

    // Calculate Planet positions using simplified orbital elements
    fun calculatePlanetPosition(date: Date, latitude: Float, longitude: Float, planetName: String): SkyCoordinate {
        // Get orbital elements based on planet name
        val orbitalElements = getPlanetOrbitalElements(date, planetName)
        if (orbitalElements == null) {
            // If we don't have elements, return a dummy position (will be improved in future version)
            return dummyPlanetPosition(planetName)
        }

        // Calculate planet's heliocentric position
        val helioCoords = calculateHeliocentricCoordinates(orbitalElements)

        // Get Earth's position to calculate geocentric position
        val earthElements = getPlanetOrbitalElements(date, "Earth")!!
        val earthCoords = calculateHeliocentricCoordinates(earthElements)

        // Calculate geocentric position (relative to Earth)
        val xGeo = helioCoords.x - earthCoords.x
        val yGeo = helioCoords.y - earthCoords.y
        val zGeo = helioCoords.z - earthCoords.z

        // Convert to RA and Dec
        val geocentricDistance = sqrt(xGeo*xGeo + yGeo*yGeo + zGeo*zGeo)
        var ra = atan2(yGeo, xGeo) * RADIANS_TO_DEGREES
        if (ra < 0) ra += 360f
        val dec = asin(zGeo / geocentricDistance) * RADIANS_TO_DEGREES

        // Get local sidereal time
        val lst = calculateSiderealTime(date, longitude)

        // Convert to horizontal coordinates
        return equatorialToHorizontal(ra, dec, latitude, lst)
    }

    // Temporary function for planet positions while we implement full orbital calculations
    private fun dummyPlanetPosition(planetName: String): SkyCoordinate {
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

        return SkyCoordinate(baseAzimuth, altitude)
    }

    // Data class to hold orbital elements
    private data class OrbitalElements(
        val a: Float,      // Semi-major axis (AU)
        val e: Float,      // Eccentricity
        val i: Float,      // Inclination (radians)
        val omega: Float,  // Longitude of ascending node (radians)
        val pi: Float,     // Longitude of perihelion (radians)
        val l: Float       // Mean longitude (radians)
    )

    // Get planetary orbital elements based on time
    private fun getPlanetOrbitalElements(date: Date, planetName: String): OrbitalElements? {
        val T = julianCenturies(date).toFloat()

        return when (planetName) {
            "Mercury" -> OrbitalElements(
                a = 0.38709927f + 0.00000037f * T,
                e = 0.20563593f + 0.00001906f * T,
                i = (7.00497902f - 0.00594749f * T) * DEGREES_TO_RADIANS,
                omega = (48.33076593f - 0.12534081f * T) * DEGREES_TO_RADIANS,
                pi = (77.45779628f + 0.16047689f * T) * DEGREES_TO_RADIANS,
                l = ((252.25032350f + 149472.67411175f * T) % 360f) * DEGREES_TO_RADIANS
            )
            "Venus" -> OrbitalElements(
                a = 0.72333566f + 0.00000390f * T,
                e = 0.00677672f - 0.00004107f * T,
                i = (3.39467605f - 0.00078890f * T) * DEGREES_TO_RADIANS,
                omega = (76.67984255f - 0.27769418f * T) * DEGREES_TO_RADIANS,
                pi = (131.60246718f + 0.00268329f * T) * DEGREES_TO_RADIANS,
                l = ((181.97909950f + 58517.81538729f * T) % 360f) * DEGREES_TO_RADIANS
            )
            "Earth" -> OrbitalElements(
                a = 1.00000261f + 0.00000562f * T,
                e = 0.01671123f - 0.00004392f * T,
                i = (-0.00001531f - 0.01294668f * T) * DEGREES_TO_RADIANS,
                omega = 0f,  // For Earth, this is 0
                pi = (102.93768193f + 0.32327364f * T) * DEGREES_TO_RADIANS,
                l = ((100.46457166f + 35999.37244981f * T) % 360f) * DEGREES_TO_RADIANS
            )
            "Mars" -> OrbitalElements(
                a = 1.52371034f + 0.00001847f * T,
                e = 0.09339410f + 0.00007882f * T,
                i = (1.84969142f - 0.00813131f * T) * DEGREES_TO_RADIANS,
                omega = (49.55953891f - 0.29257343f * T) * DEGREES_TO_RADIANS,
                pi = (-23.94362959f + 0.44441088f * T) * DEGREES_TO_RADIANS,
                l = ((-4.55343205f + 19140.30268499f * T) % 360f) * DEGREES_TO_RADIANS
            )
            "Jupiter" -> OrbitalElements(
                a = 5.20288700f - 0.00011607f * T,
                e = 0.04838624f - 0.00013253f * T,
                i = (1.30439695f - 0.00183714f * T) * DEGREES_TO_RADIANS,
                omega = (100.47390909f + 0.20469106f * T) * DEGREES_TO_RADIANS,
                pi = (14.72847983f + 0.21252668f * T) * DEGREES_TO_RADIANS,
                l = ((34.39644051f + 3034.74612775f * T) % 360f) * DEGREES_TO_RADIANS
            )
            "Saturn" -> OrbitalElements(
                a = 9.53667594f - 0.00125060f * T,
                e = 0.05386179f - 0.00050991f * T,
                i = (2.48599187f + 0.00193609f * T) * DEGREES_TO_RADIANS,
                omega = (113.66242448f - 0.28867794f * T) * DEGREES_TO_RADIANS,
                pi = (92.59887831f - 0.41897216f * T) * DEGREES_TO_RADIANS,
                l = ((49.95424423f + 1222.49362201f * T) % 360f) * DEGREES_TO_RADIANS
            )
            else -> null
        }
    }

    // Calculate heliocentric coordinates from orbital elements
    private fun calculateHeliocentricCoordinates(elements: OrbitalElements): Vector3 {
        // Calculate the planet's mean anomaly
        val m = (elements.l - elements.pi) % PI2
        if (m < 0) m + PI2

        // Solve Kepler's equation for eccentric anomaly
        var e0 = m
        var e1: Float
        val maxIterations = 10  // Usually converges in 3-4 iterations

        // Iteratively solve Kepler's equation
        for (i in 0 until maxIterations) {
            e1 = e0
            e0 = m + elements.e * sin(e1)
            if (abs(e0 - e1) < 1e-6) break
        }

        // Calculate true anomaly
        val v = 2f * atan(sqrt((1f + elements.e) / (1f - elements.e)) * tan(e0 / 2f))

        // Calculate heliocentric distance
        val r = elements.a * (1f - elements.e * cos(e0))

        // Calculate heliocentric rectangular coordinates
        val xh = r * (cos(elements.omega) * cos(v + elements.pi - elements.omega) -
                sin(elements.omega) * sin(v + elements.pi - elements.omega) * cos(elements.i))
        val yh = r * (sin(elements.omega) * cos(v + elements.pi - elements.omega) +
                cos(elements.omega) * sin(v + elements.pi - elements.omega) * cos(elements.i))
        val zh = r * sin(v + elements.pi - elements.omega) * sin(elements.i)

        return Vector3(xh, yh, zh)
    }
}