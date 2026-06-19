package com.kabulsignal.news.data.remote

import com.kabulsignal.news.data.remote.dto.CategoryDto
import com.kabulsignal.news.data.remote.dto.PostDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * WordPress REST API surface. `_embed` asks WordPress to inline the author,
 * featured image and taxonomy terms so the feed renders from a single request.
 */
interface WordPressApi {

    @GET("wp/v2/posts?_embed=author,wp:featuredmedia,wp:term")
    suspend fun getPosts(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 12,
        @Query("categories") categoryId: Int? = null,
        @Query("search") search: String? = null,
    ): Response<List<PostDto>>

    @GET("wp/v2/posts/{id}?_embed=author,wp:featuredmedia,wp:term")
    suspend fun getPost(
        @Path("id") id: Int,
    ): PostDto

    @GET("wp/v2/categories?per_page=50&orderby=count&order=desc&hide_empty=true")
    suspend fun getCategories(): List<CategoryDto>
}
