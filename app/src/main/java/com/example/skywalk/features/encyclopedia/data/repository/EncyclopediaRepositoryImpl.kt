package com.example.skywalk.features.encyclopedia.data.repository

import android.content.Context
import com.example.skywalk.features.encyclopedia.data.cache.EncyclopediaCacheManager
import com.example.skywalk.features.encyclopedia.data.models.NasaItem
import com.example.skywalk.features.encyclopedia.data.remote.NasaApiService
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObjectType
import com.example.skywalk.features.encyclopedia.domain.repository.EncyclopediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class EncyclopediaRepositoryImpl(private val context: Context) : EncyclopediaRepository {

    private val cacheManager = EncyclopediaCacheManager(context)

    private val retrofit = Retrofit.Builder()
        .baseUrl(NasaApiService.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val nasaApiService = retrofit.create(NasaApiService::class.java)

    private val categories = listOf(
        "planet", "star", "galaxy", "nebula", "moon",
        "asteroid", "comet", "black hole", "solar system", "constellation"
    )

    override suspend fun getFeaturedCelestialObjects(): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedApodData()?.let { cachedData ->
            val objects = cachedData.map { apod ->
                CelestialObject(
                    id = apod.date,
                    title = apod.title,
                    description = apod.explanation,
                    imageUrl = apod.url,
                    dateCreated = apod.date,
                    type = determineObjectType(apod.title),
                    keywords = listOf(),
                    source = "NASA APOD"
                )
            }
            emit(objects)
        }

        // Check if cache is expired
        if (cacheManager.isCacheExpired(EncyclopediaCacheManager.KEY_APOD)) {
            try {
                val response = nasaApiService.getApod()
                if (response.isSuccessful) {
                    response.body()?.let { apodList ->
                        // Cache the new data
                        cacheManager.cacheApodData(apodList)

                        // Convert and emit
                        val objects = apodList.map { apod ->
                            CelestialObject(
                                id = apod.date,
                                title = apod.title,
                                description = apod.explanation,
                                imageUrl = apod.url,
                                dateCreated = apod.date,
                                type = determineObjectType(apod.title),
                                keywords = listOf(),
                                source = "NASA APOD"
                            )
                        }
                        emit(objects)
                    }
                }
            } catch (e: Exception) {
                // Error handling - just use cache in this case
            }
        }
    }

    override suspend fun getCelestialObjectsByCategory(category: String): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedCategoryData(category)?.let { cachedData ->
            val objects = cachedData.collection.items.map { item ->
                nasaItemToCelestialObject(item, category)
            }
            emit(objects)
        }

        // Check if cache is expired
        if (cacheManager.isCacheExpired(EncyclopediaCacheManager.KEY_CATEGORY + category)) {
            try {
                val response = nasaApiService.getCelestialCategory(category)
                if (response.isSuccessful) {
                    response.body()?.let { data ->
                        // Cache the new data
                        cacheManager.cacheCategoryData(category, data)

                        // Convert and emit
                        val objects = data.collection.items.map { item ->
                            nasaItemToCelestialObject(item, category)
                        }
                        emit(objects)
                    }
                }
            } catch (e: Exception) {
                // Error handling - just use cache in this case
            }
        }
    }

    override suspend fun searchCelestialObjects(query: String): Flow<List<CelestialObject>> = flow {
        // First emit from cache if available
        cacheManager.getCachedSearchData(query)?.let { cachedData ->
            val objects = cachedData.collection.items.map { item ->
                nasaItemToCelestialObject(item)
            }
            emit(objects)
        }

        // Always fetch fresh data for searches
        try {
            val response = nasaApiService.searchImages(query)
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    // Cache the new data
                    cacheManager.cacheSearchData(query, data)

                    // Convert and emit
                    val objects = data.collection.items.map { item ->
                        nasaItemToCelestialObject(item)
                    }
                    emit(objects)
                }
            }
        } catch (e: Exception) {
            // Error handling - just use cache in this case
        }
    }

    override suspend fun getCategories(): List<String> {
        return categories
    }

    override suspend fun refreshData() {
        // Clear cache and reload everything
        cacheManager.clearAllCache()
    }

    private fun nasaItemToCelestialObject(item: NasaItem, categoryHint: String? = null): CelestialObject {
        val data = item.data.firstOrNull() ?: throw IllegalArgumentException("NASA item has no data")
        val imageUrl = item.links?.firstOrNull { it.rel == "preview" }?.href ?: ""

        return CelestialObject(
            id = data.nasaId,
            title = data.title,
            description = data.description ?: "No description available",
            imageUrl = imageUrl,
            dateCreated = data.dateCreated,
            type = determineObjectType(data.title, data.keywords, categoryHint),
            keywords = data.keywords ?: emptyList(),
            source = "NASA Image Library"
        )
    }

    private fun determineObjectType(
        title: String,
        keywords: List<String>? = null,
        categoryHint: String? = null
    ): CelestialObjectType {
        // First check category hint if provided
        if (categoryHint != null) {
            return CelestialObjectType.fromString(categoryHint)
        }

        // Then check keywords if available
        keywords?.forEach { keyword ->
            val type = CelestialObjectType.fromString(keyword)
            if (type != CelestialObjectType.OTHER) {
                return type
            }
        }

        // Finally check the title
        return CelestialObjectType.fromString(title)
    }
}