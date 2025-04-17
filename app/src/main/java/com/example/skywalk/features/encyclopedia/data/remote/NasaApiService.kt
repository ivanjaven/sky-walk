package com.example.skywalk.features.encyclopedia.data.remote

import com.example.skywalk.features.encyclopedia.data.models.ApodResponse
import com.example.skywalk.features.encyclopedia.data.models.NasaImageSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NasaApiService {
    companion object {
        const val BASE_URL = "https://api.nasa.gov/"
        const val API_KEY = "43HslrB4VOxkD3m4vop7KGsqkGbAnlovaFbuj7lv" // Replace with your NASA API key
    }

    // Astronomy Picture of the Day
    @GET("planetary/apod")
    suspend fun getApod(
        @Query("api_key") apiKey: String = API_KEY,
        @Query("count") count: Int = 20  // Get multiple random APODs
    ): Response<List<ApodResponse>>

    // NASA Image Library Search
    @GET("https://images-api.nasa.gov/search")
    suspend fun searchImages(
        @Query("q") query: String,
        @Query("media_type") mediaType: String = "image",
        @Query("year_start") yearStart: String? = null,
        @Query("year_end") yearEnd: String? = null,
    ): Response<NasaImageSearchResponse>

    // Search for celestial object categories
    @GET("https://images-api.nasa.gov/search")
    suspend fun getCelestialCategory(
        @Query("keywords") category: String,
        @Query("media_type") mediaType: String = "image",
    ): Response<NasaImageSearchResponse>
}