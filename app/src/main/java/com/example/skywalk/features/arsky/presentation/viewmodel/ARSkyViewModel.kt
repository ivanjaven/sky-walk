// features/arsky/presentation/viewmodel/ARSkyViewModel.kt
package com.example.skywalk.features.arsky.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObjectType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class ARSkyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()

    private val _celestialObjects = MutableStateFlow<List<CelestialObject>>(emptyList())
    val celestialObjects: StateFlow<List<CelestialObject>> = _celestialObjects

    private val _selectedCelestialObject = MutableStateFlow<CelestialObject?>(null)
    val selectedCelestialObject: StateFlow<CelestialObject?> = _selectedCelestialObject

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadCelestialObjects() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Timber.d("Fetching ALL celestial objects from Firestore")

                // Get ALL celestial objects at once without any limit
                val results = mutableListOf<CelestialObject>()

                try {
                    val snapshot = db.collection("celestial_objects")
                        .get()
                        .await()

                    Timber.d("Received ${snapshot.documents.size} documents from Firestore")

                    snapshot.documents.forEach { doc ->
                        parseCelestialObject(doc)?.let {
                            results.add(it)
                            Timber.d("Added object: ${it.name}, type: ${it.type}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error fetching all celestial objects")
                }

                // If we couldn't get any objects, add a fallback
                if (results.isEmpty()) {
                    Timber.d("No objects found in database, adding fallback objects")
                    results.add(createDefaultCelestialObject("Mars"))
                    results.add(createDefaultCelestialObject("Jupiter"))
                    results.add(createDefaultCelestialObject("Saturn"))
                }

                Timber.d("Loaded ${results.size} celestial objects")
                _celestialObjects.value = results

                // Select the first object by default
                if (results.isNotEmpty()) {
                    _selectedCelestialObject.value = results.first()
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading celestial objects")
                // Use defaults if we can't load from Firestore
                val defaultObjects = listOf(
                    createDefaultCelestialObject("Mars"),
                    createDefaultCelestialObject("Jupiter"),
                    createDefaultCelestialObject("Saturn")
                )
                _celestialObjects.value = defaultObjects
                _selectedCelestialObject.value = defaultObjects.first()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCelestialObject(celestialObject: CelestialObject) {
        _selectedCelestialObject.value = celestialObject
    }

    private fun parseCelestialObject(doc: com.google.firebase.firestore.DocumentSnapshot): CelestialObject? {
        return try {
            val id = doc.id
            val name = doc.getString("name") ?: return null
            val description = doc.getString("description") ?: ""
            val summary = doc.getString("summary") ?: ""
            val typeString = doc.getString("type") ?: "STAR"
            val type = try {
                CelestialObjectType.valueOf(typeString)
            } catch (e: Exception) {
                CelestialObjectType.STAR
            }

            val imageUrl = doc.getString("imageUrl") ?: ""
            val thumbnailUrl = doc.getString("thumbnailUrl") ?: ""

            CelestialObject(
                id = id,
                name = name,
                description = description,
                summary = summary,
                type = type,
                imageUrl = imageUrl,
                thumbnailUrl = thumbnailUrl
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing celestial object")
            null
        }
    }

    private fun createDefaultCelestialObject(name: String): CelestialObject {
        val (type, description, imageUrl) = when (name) {
            "Mars" -> Triple(
                CelestialObjectType.PLANET,
                "Mars is the fourth planet from the Sun and the second-smallest planet in the Solar System, being larger than only Mercury.",
                "https://firebasestorage.googleapis.com/v0/b/skywalk-app-demo.appspot.com/o/celestial_images%2Fmars.jpg?alt=media"
            )
            "Jupiter" -> Triple(
                CelestialObjectType.PLANET,
                "Jupiter is the fifth planet from the Sun and the largest in the Solar System. It is a gas giant with a mass more than two and a half times that of all the other planets combined.",
                "https://firebasestorage.googleapis.com/v0/b/skywalk-app-demo.appspot.com/o/celestial_images%2Fjupiter.jpg?alt=media"
            )
            "Saturn" -> Triple(
                CelestialObjectType.PLANET,
                "Saturn is the sixth planet from the Sun and the second-largest in the Solar System, after Jupiter. It is a gas giant with an average radius of about nine and a half times that of Earth.",
                "https://firebasestorage.googleapis.com/v0/b/skywalk-app-demo.appspot.com/o/celestial_images%2Fsaturn.jpg?alt=media"
            )
            else -> Triple(
                CelestialObjectType.STAR,
                "A celestial body in our universe.",
                "https://firebasestorage.googleapis.com/v0/b/skywalk-app-demo.appspot.com/o/celestial_images%2Fstar.jpg?alt=media"
            )
        }

        return CelestialObject(
            id = "default_$name",
            name = name,
            description = description,
            summary = "The $name",
            type = type,
            imageUrl = imageUrl
        )
    }
}