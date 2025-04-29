package com.example.skywalk.features.ar.domain.models

import kotlin.math.PI

/**
 * Represents celestial coordinates (Right Ascension and Declination),
 * or horizontal coordinates (Azimuth and Altitude) depending on context.
 */
data class SkyCoordinate(
    val rightAscension: Float,  // In degrees (0-360)
    val declination: Float      // In degrees (-90 to +90)
) {
    /**
     * Converts this SkyCoordinate to a 3D unit vector in the geocentric coordinate system.
     * @return a 3D unit vector representation
     */
    fun toVector3(): Vector3 {
        val raRad = rightAscension * DEGREES_TO_RADIANS
        val decRad = declination * DEGREES_TO_RADIANS

        return Vector3(
            x = kotlin.math.cos(raRad) * kotlin.math.cos(decRad),
            y = kotlin.math.sin(raRad) * kotlin.math.cos(decRad),
            z = kotlin.math.sin(decRad)
        )
    }

    /**
     * Calculates the angular distance to another SkyCoordinate in degrees.
     * This uses the haversine formula for better accuracy with small angles.
     */
    fun angularDistanceTo(other: SkyCoordinate): Float {
        val ra1 = rightAscension * DEGREES_TO_RADIANS
        val dec1 = declination * DEGREES_TO_RADIANS
        val ra2 = other.rightAscension * DEGREES_TO_RADIANS
        val dec2 = other.declination * DEGREES_TO_RADIANS

        // Using the haversine formula
        val haversine = kotlin.math.sin((dec2 - dec1) / 2).let { it * it } +
                kotlin.math.cos(dec1) * kotlin.math.cos(dec2) *
                kotlin.math.sin((ra2 - ra1) / 2).let { it * it }

        return 2 * kotlin.math.asin(kotlin.math.sqrt(haversine)) * RADIANS_TO_DEGREES
    }

    /**
     * Creates a SkyCoordinate with normalized values (RA between 0-360, Dec between -90-90).
     */
    fun normalized(): SkyCoordinate {
        var normalizedRa = rightAscension % 360f
        if (normalizedRa < 0) normalizedRa += 360f

        var normalizedDec = declination
        if (normalizedDec > 90f) {
            normalizedDec = 180f - normalizedDec
            normalizedRa = (normalizedRa + 180f) % 360f
        } else if (normalizedDec < -90f) {
            normalizedDec = -180f - normalizedDec
            normalizedRa = (normalizedRa + 180f) % 360f
        }

        return SkyCoordinate(normalizedRa, normalizedDec)
    }

    /**
     * Formats the SkyCoordinate for display in astronomical notation.
     * @param useHms If true, format RA as hours:minutes:seconds, otherwise as degrees
     * @return Formatted string
     */
    fun format(useHms: Boolean = true): String {
        return if (useHms) {
            // Convert RA from degrees to hours (24 hours = 360 degrees)
            val raHours = rightAscension / 15f
            val raH = raHours.toInt()
            val raM = ((raHours - raH) * 60).toInt()
            val raS = ((raHours - raH) * 60 - raM) * 60

            val decSign = if (declination < 0) "-" else "+"
            val decD = kotlin.math.abs(declination.toInt())
            val decM = (kotlin.math.abs(declination) - decD) * 60
            val decS = ((kotlin.math.abs(declination) - decD) * 60 - decM) * 60

            String.format(
                "RA: %02dh %02dm %04.1fs, Dec: %s%02d° %02d' %04.1f\"",
                raH, raM, raS, decSign, decD, decM.toInt(), decS
            )
        } else {
            String.format(
                "RA: %.2f°, Dec: %.2f°",
                rightAscension, declination
            )
        }
    }

    companion object {
        private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
        private const val RADIANS_TO_DEGREES = 180f / PI.toFloat()

        /**
         * Creates a SkyCoordinate from a 3D unit vector in the geocentric coordinate system.
         */
        fun fromVector3(vector: Vector3): SkyCoordinate {
            val normalizedVector = vector.normalize()

            val ra = kotlin.math.atan2(normalizedVector.y, normalizedVector.x) * RADIANS_TO_DEGREES
            val dec = kotlin.math.asin(normalizedVector.z) * RADIANS_TO_DEGREES

            // Normalize RA to be between 0 and 360
            val normalizedRa = if (ra < 0) ra + 360f else ra

            return SkyCoordinate(normalizedRa, dec)
        }

        /**
         * Creates a SkyCoordinate from hour angle and declination.
         * @param ra Right ascension in hours (0-24)
         * @param dec Declination in degrees (-90 to 90)
         */
        fun fromHourAngle(ra: Float, dec: Float): SkyCoordinate {
            return SkyCoordinate(ra * 15f, dec)  // 1 hour = 15 degrees
        }
    }
}