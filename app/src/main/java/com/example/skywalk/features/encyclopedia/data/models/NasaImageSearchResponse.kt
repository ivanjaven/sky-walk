package com.example.skywalk.features.encyclopedia.data.models

import com.google.gson.annotations.SerializedName

data class NasaImageSearchResponse(
    @SerializedName("collection") val collection: NasaCollection
)

data class NasaCollection(
    @SerializedName("items") val items: List<NasaItem>,
    @SerializedName("metadata") val metadata: NasaMetadata,
    @SerializedName("version") val version: String,
    @SerializedName("href") val href: String,
    @SerializedName("links") val links: List<NasaLink>?
)

data class NasaItem(
    @SerializedName("href") val href: String,
    @SerializedName("data") val data: List<NasaItemData>,
    @SerializedName("links") val links: List<NasaLink>?
)

data class NasaItemData(
    @SerializedName("center") val center: String,
    @SerializedName("title") val title: String,
    @SerializedName("nasa_id") val nasaId: String,
    @SerializedName("date_created") val dateCreated: String,
    @SerializedName("keywords") val keywords: List<String>?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("description") val description: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("secondary_creator") val secondaryCreator: String?
)

data class NasaLink(
    @SerializedName("href") val href: String,
    @SerializedName("rel") val rel: String,
    @SerializedName("render") val render: String?
)

data class NasaMetadata(
    @SerializedName("total_hits") val totalHits: Int
)