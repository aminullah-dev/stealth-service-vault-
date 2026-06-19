package com.kabulsignal.news.data.model

/** A news category (WordPress taxonomy term). */
data class Category(
    val id: Int,
    val name: String,
)

/**
 * A single article, flattened from the WordPress REST `post` resource into the
 * shape the UI actually needs. HTML is kept verbatim for [contentHtml] (rendered
 * in a WebView) and plain-text-decoded for [title] / [excerpt].
 */
data class Article(
    val id: Int,
    val title: String,
    val excerpt: String,
    val contentHtml: String,
    val dateLabel: String,
    val link: String,
    val author: String?,
    val imageUrl: String?,
    val categories: List<Category>,
) {
    val primaryCategory: String? get() = categories.firstOrNull()?.name
}

/** One page of a paginated feed plus whether more pages remain. */
data class ArticlePage(
    val articles: List<Article>,
    val hasMore: Boolean,
)
