package com.kabulsignal.news.util

import androidx.core.text.HtmlCompat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Decode an HTML fragment (entities + tags) down to trimmed plain text. */
fun String?.htmlToPlainText(): String {
    if (this.isNullOrBlank()) return ""
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(' ', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private val displayDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/**
 * Format a WordPress timestamp (e.g. `2026-06-19T10:30:00` or with an offset)
 * into a short human label. Returns the raw string if it cannot be parsed.
 */
fun formatWpDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return try {
        // WordPress `date` is local-without-offset; `date_gmt` carries Z. Try both.
        val normalized = if (raw.endsWith("Z") || raw.contains('+')) raw else "${raw}Z"
        OffsetDateTime.parse(normalized).format(displayDateFormatter)
    } catch (_: DateTimeParseException) {
        raw
    }
}
