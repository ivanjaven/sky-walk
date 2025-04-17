package com.example.skywalk.features.encyclopedia.domain.usecases

import com.example.skywalk.features.encyclopedia.domain.repository.EncyclopediaRepository

class GetCategoriesUseCase(
    private val repository: EncyclopediaRepository
) {
    suspend operator fun invoke(): List<String> {
        return repository.getCategories()
    }
}