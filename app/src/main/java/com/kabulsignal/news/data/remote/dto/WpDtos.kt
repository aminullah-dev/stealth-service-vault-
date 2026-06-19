package com.kabulsignal.news.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data-transfer objects mirroring the WordPress REST API (`/wp-json/wp/v2`).
 * Only the fields the app consumes are declared; Gson ignores the rest.
 */

data class RenderedDto(
    val rendered: String? = null,
)

data class PostDto(
    val id: Int = 0,
    val date: String? = null,
    val link: String? = null,
    val title: RenderedDto? = null,
    val excerpt: RenderedDto? = null,
    val content: RenderedDto? = null,
    val categories: List<Int>? = null,
    @SerializedName("_embedded") val embedded: EmbeddedDto? = null,
)

data class EmbeddedDto(
    val author: List<AuthorDto>? = null,
    @SerializedName("wp:featuredmedia") val featuredMedia: List<MediaDto>? = null,
    @SerializedName("wp:term") val terms: List<List<TermDto>>? = null,
)

data class AuthorDto(
    val name: String? = null,
)

data class MediaDto(
    @SerializedName("source_url") val sourceUrl: String? = null,
    @SerializedName("media_details") val mediaDetails: MediaDetailsDto? = null,
)

data class MediaDetailsDto(
    val sizes: Map<String, MediaSizeDto>? = null,
)

data class MediaSizeDto(
    @SerializedName("source_url") val sourceUrl: String? = null,
)

data class TermDto(
    val id: Int? = null,
    val name: String? = null,
    val taxonomy: String? = null,
)

data class CategoryDto(
    val id: Int = 0,
    val name: String? = null,
    val count: Int = 0,
)
