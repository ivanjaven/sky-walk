package com.example.skywalk.features.encyclopedia.data.cache

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

class EncyclopediaCacheManager(private val context: Context) {
    private val gson = Gson()
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    // Cache all objects
    fun cacheAllObjects(data: List<CelestialObject>) {
        preferences.edit {
            putString(KEY_ALL_DATA, gson.toJson(data))
            putLong(KEY_ALL_TIMESTAMP, System.currentTimeMillis())
        }
    }

    // Get cached all objects
    fun getCachedAllObjects(): List<CelestialObject>? {
        val json = preferences.getString(KEY_ALL_DATA, null) ?: return null
        return try {
            val type = object : TypeToken<List<CelestialObject>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Cache featured objects
    fun cacheFeaturedObjects(data: List<CelestialObject>) {
        preferences.edit {
            putString(KEY_FEATURED_DATA, gson.toJson(data))
            putLong(KEY_FEATURED_TIMESTAMP, System.currentTimeMillis())
        }
    }

    // Get cached featured objects
    fun getCachedFeaturedObjects(): List<CelestialObject>? {
        val json = preferences.getString(KEY_FEATURED_DATA, null) ?: return null
        return try {
            val type = object : TypeToken<List<CelestialObject>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Cache category data
    fun cacheCategoryData(category: String, data: List<CelestialObject>) {
        preferences.edit {
            putString(KEY_CATEGORY_PREFIX + category.lowercase(), gson.toJson(data))
            putLong(KEY_CATEGORY_TIMESTAMP_PREFIX + category.lowercase(), System.currentTimeMillis())
        }
    }

    // Get cached category data
    fun getCachedCategoryData(category: String): List<CelestialObject>? {
        val json = preferences.getString(KEY_CATEGORY_PREFIX + category.lowercase(), null) ?: return null
        return try {
            val type = object : TypeToken<List<CelestialObject>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Cache search results
    fun cacheSearchResults(query: String, data: List<CelestialObject>) {
        preferences.edit {
            putString(KEY_SEARCH_PREFIX + query.lowercase(), gson.toJson(data))
            putLong(KEY_SEARCH_TIMESTAMP_PREFIX + query.lowercase(), System.currentTimeMillis())
        }
    }

    // Get cached search results
    fun getCachedSearchResults(query: String): List<CelestialObject>? {
        val json = preferences.getString(KEY_SEARCH_PREFIX + query.lowercase(), null) ?: return null
        return try {
            val type = object : TypeToken<List<CelestialObject>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // Check if cache is expired
    fun isCacheExpired(key: String, expirationMs: Long = CACHE_EXPIRATION): Boolean {
        val timestamp = when {
            key == KEY_ALL -> preferences.getLong(KEY_ALL_TIMESTAMP, 0)
            key == KEY_FEATURED -> preferences.getLong(KEY_FEATURED_TIMESTAMP, 0)
            key.startsWith(KEY_SEARCH) -> {
                val query = key.removePrefix(KEY_SEARCH)
                preferences.getLong(KEY_SEARCH_TIMESTAMP_PREFIX + query.lowercase(), 0)
            }
            key.startsWith(KEY_CATEGORY) -> {
                val category = key.removePrefix(KEY_CATEGORY)
                preferences.getLong(KEY_CATEGORY_TIMESTAMP_PREFIX + category.lowercase(), 0)
            }
            else -> 0L
        }
        return System.currentTimeMillis() - timestamp > expirationMs
    }

    // Clear all cached data
    fun clearAllCache() {
        preferences.edit { clear() }
    }

    companion object {
        private const val PREF_NAME = "encyclopedia_cache"
        private const val KEY_ALL_DATA = "all_data"
        private const val KEY_ALL_TIMESTAMP = "all_timestamp"
        private const val KEY_FEATURED_DATA = "featured_data"
        private const val KEY_FEATURED_TIMESTAMP = "featured_timestamp"
        private const val KEY_SEARCH_PREFIX = "search_"
        private const val KEY_SEARCH_TIMESTAMP_PREFIX = "search_timestamp_"
        private const val KEY_CATEGORY_PREFIX = "category_"
        private const val KEY_CATEGORY_TIMESTAMP_PREFIX = "category_timestamp_"

        const val KEY_ALL = "all"
        const val KEY_FEATURED = "featured"
        const val KEY_SEARCH = "search_"
        const val KEY_CATEGORY = "category_"

        // Cache expiration time (1 hour)
        val CACHE_EXPIRATION = TimeUnit.HOURS.toMillis(1)
    }
}