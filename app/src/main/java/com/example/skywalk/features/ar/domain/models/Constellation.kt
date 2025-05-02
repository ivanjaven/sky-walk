package com.example.skywalk.features.ar.domain.models

/**
 * Represents a complete constellation with metadata and line segments
 */
data class Constellation(
    val id: String,                   // 3-letter IAU designator (e.g., "And" for Andromeda)
    val name: String,                 // Full name (e.g., "Andromeda")
    val rank: Int,                    // Importance rank (1-3, 1 being most prominent)
    val lines: List<ConstellationLine> // List of line segments forming the constellation pattern
) {
    /**
     * Get a calculated center point of the constellation
     * (average of all line endpoints)
     */
    fun getCenter(): SkyCoordinate {
        if (lines.isEmpty()) return SkyCoordinate(0f, 0f)

        var sumRa = 0f
        var sumDec = 0f
        var count = 0

        // Sum up all unique endpoints
        val uniquePoints = mutableSetOf<Pair<Float, Float>>()

        for (line in lines) {
            // Add start point if not already counted
            val startPoint = Pair(line.startRa, line.startDec)
            if (uniquePoints.add(startPoint)) {
                sumRa += line.startRa
                sumDec += line.startDec
                count++
            }

            // Add end point if not already counted
            val endPoint = Pair(line.endRa, line.endDec)
            if (uniquePoints.add(endPoint)) {
                sumRa += line.endRa
                sumDec += line.endDec
                count++
            }
        }

        // Calculate average
        return if (count > 0) {
            SkyCoordinate(sumRa / count, sumDec / count)
        } else {
            SkyCoordinate(0f, 0f)
        }
    }

    /**
     * Determines if this constellation is considered major/prominent
     */
    fun isMajor(): Boolean {
        return rank <= 2
    }
}

/**
 * Represents a single line segment in a constellation pattern
 */
data class ConstellationLine(
    val startRa: Float,  // Right Ascension of line start (degrees)
    val startDec: Float, // Declination of line start (degrees)
    val endRa: Float,    // Right Ascension of line end (degrees)
    val endDec: Float    // Declination of line end (degrees)
) {
    /**
     * Get the starting point as a SkyCoordinate
     */
    fun getStart(): SkyCoordinate = SkyCoordinate(startRa, startDec)

    /**
     * Get the ending point as a SkyCoordinate
     */
    fun getEnd(): SkyCoordinate = SkyCoordinate(endRa, endDec)
}