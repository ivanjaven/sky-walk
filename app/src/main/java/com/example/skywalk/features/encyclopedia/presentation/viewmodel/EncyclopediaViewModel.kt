package com.example.skywalk.features.encyclopedia.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.encyclopedia.data.repository.EncyclopediaRepositoryImpl
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.domain.usecases.GetCategoriesUseCase
import com.example.skywalk.features.encyclopedia.domain.usecases.GetCelestialObjectsByCategoryUseCase
import com.example.skywalk.features.encyclopedia.domain.usecases.GetFeaturedCelestialObjectsUseCase
import com.example.skywalk.features.encyclopedia.domain.usecases.SearchCelestialObjectsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class EncyclopediaViewModel(application: Application) : AndroidViewModel(application) {

    // Repository and use cases (without DI for simplicity)
    private val repository = EncyclopediaRepositoryImpl(application)
    private val getFeaturedCelestialObjectsUseCase = GetFeaturedCelestialObjectsUseCase(repository)
    private val getCelestialObjectsByCategoryUseCase = GetCelestialObjectsByCategoryUseCase(repository)
    private val searchCelestialObjectsUseCase = SearchCelestialObjectsUseCase(repository)
    private val getCategoriesUseCase = GetCategoriesUseCase(repository)

    // UI state
    private val _uiState = MutableStateFlow<EncyclopediaUiState>(EncyclopediaUiState.Loading)
    val uiState: StateFlow<EncyclopediaUiState> = _uiState

    // Categories
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    // Selected category
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadCategories()
        loadFeaturedObjects()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = getCategoriesUseCase()
        }
    }

    fun loadFeaturedObjects() {
        viewModelScope.launch {
            _uiState.value = EncyclopediaUiState.Loading
            getFeaturedCelestialObjectsUseCase()
                .catch { e ->
                    _uiState.value = EncyclopediaUiState.Error(e.message ?: "Unknown error")
                }
                .collect { objects ->
                    _uiState.value = EncyclopediaUiState.Success(objects)
                }
        }
    }

    fun loadObjectsByCategory(category: String) {
        _selectedCategory.value = category
        viewModelScope.launch {
            _uiState.value = EncyclopediaUiState.Loading
            getCelestialObjectsByCategoryUseCase(category)
                .catch { e ->
                    _uiState.value = EncyclopediaUiState.Error(e.message ?: "Unknown error")
                }
                .collect { objects ->
                    _uiState.value = EncyclopediaUiState.Success(objects)
                }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query

        if (query.isEmpty()) {
            loadFeaturedObjects()
            return
        }

        viewModelScope.launch {
            _uiState.value = EncyclopediaUiState.Loading
            searchCelestialObjectsUseCase(query)
                .catch { e ->
                    _uiState.value = EncyclopediaUiState.Error(e.message ?: "Unknown error")
                }
                .collect { objects ->
                    _uiState.value = EncyclopediaUiState.Success(objects)
                }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshData()
            if (_selectedCategory.value != null) {
                loadObjectsByCategory(_selectedCategory.value!!)
            } else if (_searchQuery.value.isNotEmpty()) {
                search(_searchQuery.value)
            } else {
                loadFeaturedObjects()
            }
        }
    }
}

sealed class EncyclopediaUiState {
    object Loading : EncyclopediaUiState()
    data class Success(val celestialObjects: List<CelestialObject>) : EncyclopediaUiState()
    data class Error(val message: String) : EncyclopediaUiState()
}