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

    private val _selectedCelestialObject = MutableStateFlow<CelestialObject?>(null)
    val selectedCelestialObject: StateFlow<CelestialObject?> = _selectedCelestialObject

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadFirstCelestialObject() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Timber.d("Fetching celestial object from Firestore")

                // First try to fetch a visible planet (they work best in AR)
                val snapshot = db.collection("celestial_objects")
                    .whereEqualTo("type", CelestialObjectType.PLANET.name)
                    .limit(1)
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    parseCelestialObject(doc)?.let { celestialObject ->
                        _selectedCelestialObject.value = celestialObject
                        Timber.d("Loaded celestial object: ${celestialObject.name}")
                    }
                } else {
                    // Try stars if no planets found
                    val starSnapshot = db.collection("celestial_objects")
                        .whereEqualTo("type", CelestialObjectType.STAR.name)
                        .limit(1)
                        .get()
                        .await()

                    if (!starSnapshot.isEmpty) {
                        val doc = starSnapshot.documents[0]
                        parseCelestialObject(doc)?.let { celestialObject ->
                            _selectedCelestialObject.value = celestialObject
                            Timber.d("Loaded star object: ${celestialObject.name}")
                        }
                    } else {
                        // Try any celestial object if no planets or stars found
                        val fallbackSnapshot = db.collection("celestial_objects")
                            .limit(1)
                            .get()
                            .await()

                        if (!fallbackSnapshot.isEmpty) {
                            val doc = fallbackSnapshot.documents[0]
                            parseCelestialObject(doc)?.let { celestialObject ->
                                _selectedCelestialObject.value = celestialObject
                                Timber.d("Loaded fallback celestial object: ${celestialObject.name}")
                            }
                        } else {
                            // Create a default object if nothing found
                            _selectedCelestialObject.value = createDefaultCelestialObject()
                            Timber.d("Using default celestial object")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading celestial object")
                _selectedCelestialObject.value = createDefaultCelestialObject()
            } finally {
                _isLoading.value = false
            }
        }
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

    private fun createDefaultCelestialObject(): CelestialObject {
        return CelestialObject(
            id = "default_planet",
            name = "Mars",
            description = "Mars is the fourth planet from the Sun and the second-smallest planet in the Solar System, being larger than only Mercury.",
            summary = "The Red Planet",
            type = CelestialObjectType.PLANET,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/skywalk-app-demo.appspot.com/o/celestial_images%2Fmars.jpg?alt=media"
        )
    }
}