package com.example.skywalk.features.encyclopedia.data.repository

import android.content.Context
import com.example.skywalk.features.encyclopedia.data.cache.EncyclopediaCacheManager
import com.example.skywalk.features.encyclopedia.data.remote.FirestoreService
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.domain.repository.EncyclopediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class EncyclopediaRepositoryImpl(private val context: Context) : EncyclopediaRepository {

    private val cacheManager = EncyclopediaCacheManager(context)
    private val firestoreService = FirestoreService()

    override suspend fun getAllCelestialObjects(): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedAllObjects()?.let { cachedData ->
            emit(cachedData)
        }

        // Check if cache is expired
        if (cacheManager.isCacheExpired(EncyclopediaCacheManager.KEY_ALL)) {
            try {
                val objects = firestoreService.getAllCelestialObjects()
                    .sortedBy { it.name }

                // Cache the new data
                cacheManager.cacheAllObjects(objects)

                // Emit fresh data
                emit(objects)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching all objects")
                // Error handling - just use cache in this case
            }
        }
    }

    override suspend fun getFeaturedCelestialObjects(): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedFeaturedObjects()?.let { cachedData ->
            emit(cachedData)
        }

        // Check if cache is expired
        if (cacheManager.isCacheExpired(EncyclopediaCacheManager.KEY_FEATURED)) {
            try {
                val objects = firestoreService.getAllCelestialObjects()
                    .sortedByDescending { it.visibility?.magnitude ?: 0.0 }
                    .take(20) // Get top 20 objects as featured

                // Cache the new data
                cacheManager.cacheFeaturedObjects(objects)

                // Emit fresh data
                emit(objects)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching featured objects")
                // Error handling - just use cache in this case
            }
        }
    }

    override suspend fun getCelestialObjectsByCategory(category: String): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedCategoryData(category)?.let { cachedData ->
            emit(cachedData)
        }

        // Check if cache is expired
        if (cacheManager.isCacheExpired(EncyclopediaCacheManager.KEY_CATEGORY + category)) {
            try {
                val objects = firestoreService.getCelestialObjectsByCategory(category)

                // Cache the new data
                cacheManager.cacheCategoryData(category, objects)

                // Emit fresh data
                emit(objects)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching objects by category: $category")
                // Error handling - just use cache in this case
            }
        }
    }

    override suspend fun searchCelestialObjects(query: String): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedSearchResults(query)?.let { cachedData ->
            emit(cachedData)
        }

        // Always fetch fresh data for searches
        try {
            val objects = firestoreService.searchCelestialObjects(query)

            // Cache the new data
            cacheManager.cacheSearchResults(query, objects)

            // Emit fresh data
            emit(objects)
        } catch (e: Exception) {
            Timber.e(e, "Error searching objects: $query")
            // Error handling - just use cache in this case
        }
    }

    override suspend fun getCategories(): List<String> {
        return firestoreService.getCategories()
    }

    override suspend fun refreshData() {
        // Clear cache and reload everything
        cacheManager.clearAllCache()
    }
}