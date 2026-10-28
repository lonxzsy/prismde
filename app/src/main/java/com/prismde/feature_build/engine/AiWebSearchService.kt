package com.prismde.feature_build.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Lightweight mobile Web Search Service for PrismDE.
 * Uses DuckDuckGo HTML zero-config API to fetch real-time documentation,
 * API references, and code solutions without requiring paid API keys or heavy browser runtimes.
 */
class AiWebSearchService(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun searchWeb(query: String, maxResults: Int = 4): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val formBody = FormBody.Builder()
                .add("q", trimmed)
                .add("b", "")
                .build()

            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val html = response.body?.string() ?: return@withContext emptyList()
                parseDuckDuckGoHtml(html, maxResults)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDuckDuckGoHtml(html: String, maxResults: Int): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()

        val resultPattern = Pattern.compile(
            """<a[^>]*class=["'][^"']*result__url[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>[\s\S]*?<a[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>([\s\S]*?)</a>""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = resultPattern.matcher(html)
        while (matcher.find() && results.size < maxResults) {
            val rawUrl = matcher.group(1)?.trim() ?: ""
            val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
            val snippet = cleanHtml(matcher.group(3) ?: "")
            val title = cleanHtml(matcher.group(2) ?: "")

            if (cleanUrl.isNotBlank() && snippet.isNotBlank()) {
                results.add(WebSearchResult(title = title.ifBlank { cleanUrl }, url = cleanUrl, snippet = snippet))
            }
        }

        if (results.isEmpty()) {
            val snippetPattern = Pattern.compile("""<a class="result__snippet[^"]*"[^>]*>([\s\S]*?)</a>""")
            val sMatcher = snippetPattern.matcher(html)
            var count = 0
            while (sMatcher.find() && count < maxResults) {
                val snippetText = cleanHtml(sMatcher.group(1) ?: "")
                if (snippetText.isNotBlank()) {
                    results.add(
                        WebSearchResult(
                            title = "Result ${count + 1}",
                            url = "https://duckduckgo.com/?q=${URLEncoder.encode(html.take(20), "UTF-8")}",
                            snippet = snippetText
                        )
                    )
                    count++
                }
            }
        }

        return results
    }

    private fun cleanDuckDuckGoUrl(raw: String): String {
        val uddgIdx = raw.indexOf("uddg=")
        if (uddgIdx != -1) {
            val sub = raw.substring(uddgIdx + 5)
            val ampIdx = sub.indexOf('&')
            val encoded = if (ampIdx != -1) sub.substring(0, ampIdx) else sub
            return try { URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { raw }
        }
        return raw
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
