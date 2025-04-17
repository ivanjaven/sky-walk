package com.example.skywalk.features.encyclopedia.domain.repository

import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import kotlinx.coroutines.flow.Flow

interface EncyclopediaRepository {
    suspend fun getFeaturedCelestialObjects(): Flow<List<CelestialObject>>
    suspend fun getCelestialObjectsByCategory(category: String): Flow<List<CelestialObject>>
    suspend fun searchCelestialObjects(query: String): Flow<List<CelestialObject>>
    suspend fun getCategories(): List<String>
    suspend fun refreshData()
}