package com.example.skywalk.features.encyclopedia.data.cache

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.skywalk.features.encyclopedia.data.models.ApodResponse
import com.example.skywalk.features.encyclopedia.data.models.NasaImageSearchResponse
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit

class EncyclopediaCacheManager(private val context: Context) {
    private val gson = Gson()
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    // Cache APOD data
    fun cacheApodData(data: List<ApodResponse>) {
        preferences.edit {
            putString(KEY_APOD_DATA, gson.toJson(data))
            putLong(KEY_APOD_TIMESTAMP, System.currentTimeMillis())
        }
    }

    // Get cached APOD data
    fun getCachedApodData(): List<ApodResponse>? {
        val json = preferences.getString(KEY_APOD_DATA, null) ?: return null
        return try {
            gson.fromJson(json, Array<ApodResponse>::class.java).toList()
        } catch (e: Exception) {
            null
        }
    }

    // Cache NASA Image Search data
    fun cacheSearchData(query: String, data: NasaImageSearchResponse) {
        preferences.edit {
            putString(KEY_SEARCH_PREFIX + query.lowercase(), gson.toJson(data))
            putLong(KEY_SEARCH_TIMESTAMP_PREFIX + query.lowercase(), System.currentTimeMillis())
        }
    }

    // Get cached search data
    fun getCachedSearchData(query: String): NasaImageSearchResponse? {
        val json = preferences.getString(KEY_SEARCH_PREFIX + query.lowercase(), null) ?: return null
        return try {
            gson.fromJson(json, NasaImageSearchResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Cache category data
    fun cacheCategoryData(category: String, data: NasaImageSearchResponse) {
        preferences.edit {
            putString(KEY_CATEGORY_PREFIX + category.lowercase(), gson.toJson(data))
            putLong(KEY_CATEGORY_TIMESTAMP_PREFIX + category.lowercase(), System.currentTimeMillis())
        }
    }

    // Get cached category data
    fun getCachedCategoryData(category: String): NasaImageSearchResponse? {
        val json = preferences.getString(KEY_CATEGORY_PREFIX + category.lowercase(), null) ?: return null
        return try {
            gson.fromJson(json, NasaImageSearchResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Check if cache is expired
    fun isCacheExpired(key: String, expirationMs: Long = CACHE_EXPIRATION): Boolean {
        val timestamp = when {
            key == KEY_APOD -> preferences.getLong(KEY_APOD_TIMESTAMP, 0)
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
        context.cacheDir.deleteRecursively()
    }

    companion object {
        private const val PREF_NAME = "encyclopedia_cache"
        private const val KEY_APOD_DATA = "apod_data"
        private const val KEY_APOD_TIMESTAMP = "apod_timestamp"
        private const val KEY_SEARCH_PREFIX = "search_"
        private const val KEY_SEARCH_TIMESTAMP_PREFIX = "search_timestamp_"
        private const val KEY_CATEGORY_PREFIX = "category_"
        private const val KEY_CATEGORY_TIMESTAMP_PREFIX = "category_timestamp_"

        const val KEY_APOD = "apod"
        const val KEY_SEARCH = "search_"
        const val KEY_CATEGORY = "category_"

        // Cache expiration time (1 hour)
        val CACHE_EXPIRATION = TimeUnit.HOURS.toMillis(1)
    }
}