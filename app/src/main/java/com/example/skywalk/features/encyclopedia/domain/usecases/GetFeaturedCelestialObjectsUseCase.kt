package com.example.skywalk.features.encyclopedia.domain.usecases

import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.domain.repository.EncyclopediaRepository
import kotlinx.coroutines.flow.Flow

class GetFeaturedCelestialObjectsUseCase(
    private val repository: EncyclopediaRepository
) {
    suspend operator fun invoke(): Flow<List<CelestialObject>> {
        return repository.getFeaturedCelestialObjects()
    }
}