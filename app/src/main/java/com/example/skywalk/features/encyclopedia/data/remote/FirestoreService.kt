package com.example.skywalk.features.encyclopedia.data.remote

import com.example.skywalk.core.firebase.FirebaseConfig
import com.example.skywalk.features.encyclopedia.domain.models.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirestoreService {
    private val db = FirebaseConfig.firestore
    private val celestialCollection = db.collection("celestial_objects")

    suspend fun getAllCelestialObjects(): List<CelestialObject> {
        return try {
            val snapshot = celestialCollection.get().await()
            parseCelestialObjects(snapshot)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching celestial objects")
            emptyList()
        }
    }

    suspend fun getCelestialObjectsByCategory(category: String): List<CelestialObject> {
        return try {
            val snapshot = celestialCollection
                .whereEqualTo("type", category.uppercase())
                .get().await()
            parseCelestialObjects(snapshot)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching objects by category: $category")
            emptyList()
        }
    }

    suspend fun searchCelestialObjects(query: String): List<CelestialObject> {
        return try {
            // Due to Firestore limitations with text search, we'll fetch all and filter
            // In a production app, consider using Algolia or similar search solutions
            val snapshot = celestialCollection.get().await()
            val objects = parseCelestialObjects(snapshot)
            objects.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.keywords.any { keyword -> keyword.contains(query, ignoreCase = true) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error searching objects: $query")
            emptyList()
        }
    }

    suspend fun getCategories(): List<String> {
        return CelestialObjectType.values()
            .filter { it != CelestialObjectType.OTHER }
            .map { it.name.lowercase() }
    }

    private fun parseCelestialObjects(snapshot: QuerySnapshot): List<CelestialObject> {
        return snapshot.documents.mapNotNull { documentSnapshot ->
            parseCelestialObject(documentSnapshot)
        }
    }

    private fun parseCelestialObject(document: DocumentSnapshot): CelestialObject? {
        return try {
            val id = document.id
            val name = document.getString("name") ?: return null
            val description = document.getString("description") ?: ""
            val summary = document.getString("summary") ?: ""
            val typeString = document.getString("type") ?: "OTHER"
            val type = try {
                CelestialObjectType.valueOf(typeString)
            } catch (e: Exception) {
                CelestialObjectType.OTHER
            }

            val imageUrl = document.getString("imageUrl") ?: ""
            val thumbnailUrl = document.getString("thumbnailUrl") ?: imageUrl

            // Parse coordinates
            val coordinatesMap = document.get("coordinates") as? Map<*, *>
            val coordinates = if (coordinatesMap != null) {
                CelestialCoordinates(
                    rightAscension = (coordinatesMap["rightAscension"] as? String) ?: "",
                    declination = (coordinatesMap["declination"] as? String) ?: "",
                    properMotion = coordinatesMap["properMotion"] as? String,
                    epoch = (coordinatesMap["epoch"] as? String) ?: "J2000"
                )
            } else null

            // Parse visibility
            val visibilityMap = document.get("visibility") as? Map<*, *>
            val visibility = if (visibilityMap != null) {
                CelestialVisibility(
                    magnitude = (visibilityMap["magnitude"] as? Number)?.toDouble(),
                    color = (visibilityMap["color"] as? String) ?: "#FFFFFF",
                    angularSize = (visibilityMap["angularSize"] as? String) ?: "",
                    isVisibleToNakedEye = (visibilityMap["isVisibleToNakedEye"] as? Boolean) ?: false
                )
            } else null

            // Parse observing details
            val observingMap = document.get("observing") as? Map<*, *>
            val observing = if (observingMap != null) {
                ObservingDetails(
                    bestTimeToView = (observingMap["bestTimeToView"] as? String) ?: "",
                    findingTips = (observingMap["findingTips"] as? String) ?: "",
                    equipment = (observingMap["equipment"] as? String) ?: "",
                    features = (observingMap["features"] as? String) ?: ""
                )
            } else null

            // Parse properties
            val propertiesMap = document.get("properties") as? Map<*, *>
            val properties = if (propertiesMap != null) {
                CelestialProperties(
                    mass = (propertiesMap["mass"] as? String) ?: "",
                    diameter = (propertiesMap["diameter"] as? String) ?: "",
                    distance = (propertiesMap["distance"] as? String) ?: ""
                )
            } else null

            // Parse facts
            val factsArray = document.get("facts") as? List<*>
            val facts = factsArray?.mapNotNull { it as? String } ?: emptyList()

            // Parse AR visualization
            val arMap = document.get("arVisualization") as? Map<*, *>
            val arVisualization = if (arMap != null) {
                val labelOffsetMap = arMap["labelOffset"] as? Map<*, *>
                val labelOffset = if (labelOffsetMap != null) {
                    LabelOffset(
                        x = (labelOffsetMap["x"] as? Number)?.toInt() ?: 0,
                        y = (labelOffsetMap["y"] as? Number)?.toInt() ?: 10
                    )
                } else LabelOffset()

                ArVisualization(
                    visualType = (arMap["visualType"] as? String) ?: "POINT",
                    pointColor = (arMap["pointColor"] as? String) ?: "#FFFFFF",
                    pointSize = (arMap["pointSize"] as? Number)?.toDouble() ?: 1.0,
                    labelOffset = labelOffset,
                    labelColor = (arMap["labelColor"] as? String) ?: "#FFFFFF",
                    showLabel = (arMap["showLabel"] as? Boolean) ?: true
                )
            } else null

            // Parse keywords and related objects
            val keywordsArray = document.get("keywords") as? List<*>
            val keywords = keywordsArray?.mapNotNull { it as? String } ?: emptyList()

            val relatedArray = document.get("relatedObjects") as? List<*>
            val relatedObjects = relatedArray?.mapNotNull { it as? String } ?: emptyList()

            CelestialObject(
                id = id,
                name = name,
                description = description,
                summary = summary,
                type = type,
                imageUrl = imageUrl,
                thumbnailUrl = thumbnailUrl,
                coordinates = coordinates,
                visibility = visibility,
                observing = observing,
                properties = properties,
                facts = facts,
                arVisualization = arVisualization,
                keywords = keywords,
                relatedObjects = relatedObjects
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing celestial object: ${document.id}")
            null
        }
    }
}