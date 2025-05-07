package com.example.skywalk.features.ar.domain.models

data class StarName(
    val id: Int,                      // Star ID matching stars.json
    val properName: String,           // Common/traditional name (e.g., "Sirius")
    val bayerDesignation: String,     // Bayer designation (e.g., "α")
    val flamsteedDesignation: String, // Flamsteed number
    val variableDesignation: String,  // Variable star designation
    val hdCatalog: String,            // HD catalog number
    val hipparcosCatalog: String,     // Hipparcos catalog number
    val glieseCatalog: String,        // Gliese catalog number
    val constellation: String,        // Constellation abbreviation (e.g., "CMa" for Canis Major)
    val primaryDesignation: String    // Primary designation to use if properName is empty
) {
    /**
     * Get the best available name for display
     */
    fun getBestName(): String {
        // If a proper name exists, use it
        if (properName.isNotEmpty()) {
            return properName
        }

        // Next priority: Bayer with constellation
        if (bayerDesignation.isNotEmpty() && constellation.isNotEmpty()) {
            return "$bayerDesignation $constellation"
        }

        // Flamsteed with constellation
        if (flamsteedDesignation.isNotEmpty() && constellation.isNotEmpty()) {
            return "$flamsteedDesignation $constellation"
        }

        // Variable designation
        if (variableDesignation.isNotEmpty()) {
            return variableDesignation
        }

        // Any custom primary designation
        if (primaryDesignation.isNotEmpty()) {
            return primaryDesignation
        }

        // Fall back to HD or HIP catalog numbers
        if (hdCatalog.isNotEmpty()) {
            return hdCatalog
        }

        if (hipparcosCatalog.isNotEmpty()) {
            return hipparcosCatalog
        }

        // Last resort: just return ID-based name
        return "HD-$id"
    }
}