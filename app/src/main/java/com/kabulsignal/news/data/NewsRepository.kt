package com.kabulsignal.news.data

import com.kabulsignal.news.data.model.Article
import com.kabulsignal.news.data.model.ArticlePage
import com.kabulsignal.news.data.model.Category
import com.kabulsignal.news.data.remote.WordPressApi
import com.kabulsignal.news.data.remote.dto.MediaDto
import com.kabulsignal.news.data.remote.dto.PostDto
import com.kabulsignal.news.util.formatWpDate
import com.kabulsignal.news.util.htmlToPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for news data. Talks to the WordPress REST API, maps
 * the wire format to UI-friendly [Article]s, and keeps a small in-memory cache
 * so opening an article from the feed needs no extra network round-trip.
 */
class NewsRepository(private val api: WordPressApi) {

    private val articleCache = mutableMapOf<Int, Article>()

    suspend fun getCategories(): Result<List<Category>> = runCatching {
        withContext(Dispatchers.IO) {
            api.getCategories()
                .filter { it.count > 0 }
                .map { Category(id = it.id, name = it.name.htmlToPlainText()) }
        }
    }

    suspend fun getArticles(
        page: Int,
        categoryId: Int? = null,
        search: String? = null,
    ): Result<ArticlePage> = runCatching {
        withContext(Dispatchers.IO) {
            val response = api.getPosts(
                page = page,
                categoryId = categoryId,
                search = search?.takeIf { it.isNotBlank() },
            )
            if (!response.isSuccessful) {
                // WordPress answers a page past the end with 400 — treat as "no more".
                if (response.code() == 400) return@withContext ArticlePage(emptyList(), hasMore = false)
                error("HTTP ${response.code()}")
            }
            val totalPages = response.headers()["X-WP-TotalPages"]?.toIntOrNull() ?: page
            val articles = (response.body() ?: emptyList()).map { it.toArticle() }
            articles.forEach { articleCache[it.id] = it }
            ArticlePage(articles = articles, hasMore = page < totalPages)
        }
    }

    suspend fun getArticle(id: Int): Result<Article> = runCatching {
        articleCache[id] ?: withContext(Dispatchers.IO) {
            api.getPost(id).toArticle().also { articleCache[id] = it }
        }
    }

    // --- mapping ----------------------------------------------------------

    private fun PostDto.toArticle(): Article {
        val embeddedTerms = embedded?.terms.orEmpty()
            .flatten()
            .filter { it.taxonomy == "category" && it.id != null && !it.name.isNullOrBlank() }
            .map { Category(id = it.id!!, name = it.name.htmlToPlainText()) }

        return Article(
            id = id,
            title = title?.rendered.htmlToPlainText(),
            excerpt = excerpt?.rendered.htmlToPlainText(),
            contentHtml = content?.rendered.orEmpty(),
            dateLabel = formatWpDate(date),
            link = link.orEmpty(),
            author = embedded?.author?.firstOrNull()?.name?.htmlToPlainText()?.takeIf { it.isNotBlank() },
            imageUrl = embedded?.featuredMedia?.firstOrNull()?.bestImageUrl(),
            categories = embeddedTerms,
        )
    }

    /** Prefer a mid-size rendition to save bandwidth, falling back to the full image. */
    private fun MediaDto.bestImageUrl(): String? {
        val sizes = mediaDetails?.sizes
        return sizes?.get("medium_large")?.sourceUrl
            ?: sizes?.get("large")?.sourceUrl
            ?: sizes?.get("medium")?.sourceUrl
            ?: sourceUrl
    }
}
