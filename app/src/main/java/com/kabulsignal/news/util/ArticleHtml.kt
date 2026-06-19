package com.kabulsignal.news.util

/**
 * Wraps a WordPress post's rendered HTML in a self-contained, RTL-first document
 * styled for comfortable mobile reading. Colours adapt to the system dark mode
 * via `prefers-color-scheme` so it matches the surrounding Compose theme.
 */
object ArticleHtml {

    /** Full article document: hero image, headline, byline, then the post body. */
    fun document(title: String, meta: String, imageUrl: String?, contentHtml: String): String {
        val hero = imageUrl?.let { "<img class=\"hero\" src=\"${it.escapeHtml()}\" />" }.orEmpty()
        val metaBlock = if (meta.isBlank()) "" else "<div class=\"post-meta\">${meta.escapeHtml()}</div>"
        val header = """
            $hero
            <h1 class="post-title">${title.escapeHtml()}</h1>
            $metaBlock
            <hr class="rule" />
        """.trimIndent()
        return wrap(header + contentHtml)
    }

    private fun wrap(bodyHtml: String): String = """
        <!DOCTYPE html>
        <html lang="fa" dir="rtl">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>
            :root {
              --bg: #FBF8F6;
              --fg: #1A1C1E;
              --muted: #5F6368;
              --accent: #8E1B1B;
              --rule: #E2E2E6;
            }
            @media (prefers-color-scheme: dark) {
              :root {
                --bg: #121316;
                --fg: #E3E2E6;
                --muted: #A9A9AD;
                --accent: #F2B8B5;
                --rule: #2A2B2E;
              }
            }
            html, body {
              margin: 0;
              padding: 0;
              background: var(--bg);
              color: var(--fg);
            }
            body {
              font-family: -apple-system, "Roboto", "Noto Naskh Arabic", "Vazirmatn", sans-serif;
              font-size: 18px;
              line-height: 1.9;
              padding: 0 18px 32px;
              word-wrap: break-word;
              overflow-wrap: break-word;
              text-align: justify;
            }
            .hero { width: 100%; height: auto; border-radius: 12px; margin: 12px 0; display: block; }
            .post-title { font-size: 26px; line-height: 1.4; margin: 8px 0 6px; text-align: right; }
            .post-meta { color: var(--muted); font-size: 14px; margin-bottom: 8px; text-align: right; }
            .rule { border: none; border-top: 1px solid var(--rule); margin: 12px 0 20px; }
            p { margin: 0 0 1.1em; }
            a { color: var(--accent); text-decoration: none; }
            img, figure, video, iframe {
              max-width: 100% !important;
              height: auto;
              border-radius: 10px;
              margin: 12px 0;
              display: block;
            }
            figcaption { color: var(--muted); font-size: 14px; text-align: center; }
            h1, h2, h3, h4 { line-height: 1.4; }
            blockquote {
              margin: 1em 0;
              padding: 4px 14px;
              border-right: 4px solid var(--accent);
              color: var(--muted);
            }
            ul, ol { padding-right: 1.2em; padding-left: 0; }
            table { max-width: 100%; border-collapse: collapse; }
            td, th { border: 1px solid var(--rule); padding: 6px; }
            pre { overflow-x: auto; background: rgba(127,127,127,0.12); padding: 12px; border-radius: 8px; }
          </style>
        </head>
        <body>$bodyHtml</body>
        </html>
    """.trimIndent()

    private fun String.escapeHtml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
