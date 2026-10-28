package com.prismde.feature_build.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class WebFetchResult(
    val url: String,
    val title: String,
    val markdown: String,
    val byteCount: Long,
    val isTruncated: Boolean
)

/**
 * Production-ready WebFetch Service for PrismDE AI Agent.
 * Fetches documentation, GitHub files, API references, or web guides,
 * converts HTML to clean readable Markdown, with SSRF protection and memory caps.
 */
class AiWebFetchService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    companion object {
        const val MAX_FETCH_BYTES = 100 * 1024L // 100 KB limit to prevent OOM
        private val UNWANTED_TAGS = listOf(
            "script", "style", "noscript", "svg", "nav",
            "footer", "header", "aside", "form", "iframe"
        )
    }

    suspend fun fetchUrl(targetUrl: String): Result<WebFetchResult> = withContext(Dispatchers.IO) {
        val trimmed = targetUrl.trim()
        val normalizedUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed

        val httpUrl = normalizedUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid URL format: $targetUrl"))

        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            return@withContext Result.failure(SecurityException("Scheme '${httpUrl.scheme}' is disallowed."))
        }

        // SSRF protection: block loopback and local/private networks
        try {
            val address = InetAddress.getByName(httpUrl.host)
            if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) {
                return@withContext Result.failure(SecurityException("Access to local/private network hosts is forbidden."))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("DNS resolution failed for ${httpUrl.host}: ${e.message}"))
        }

        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(IOException("HTTP Error ${response.code} ${response.message}"))
                }

                val contentType = response.header("Content-Type", "")?.lowercase() ?: ""
                val isHtml = contentType.contains("html")
                val isText = contentType.contains("text/plain") || contentType.contains("markdown")
                val isJson = contentType.contains("json")

                if (!isHtml && !isText && !isJson) {
                    return@use Result.failure(IOException("Unsupported Content-Type: $contentType (only text/html/json supported)"))
                }

                val source = response.body?.source() ?: return@use Result.failure(IOException("Empty response body"))
                val buffer = Buffer()
                var totalRead = 0L
                var isTruncated = false

                while (totalRead < MAX_FETCH_BYTES) {
                    val toRead = minOf(8192L, MAX_FETCH_BYTES - totalRead)
                    val readCount = source.read(buffer, toRead)
                    if (readCount == -1L) break
                    totalRead += readCount
                }

                if (!source.exhausted()) {
                    isTruncated = true
                }

                val rawContent = buffer.readUtf8()
                val (title, markdown) = if (isHtml) {
                    parseHtmlToMarkdown(rawContent, targetUrl)
                } else {
                    Pair(httpUrl.host, rawContent)
                }

                Result.success(
                    WebFetchResult(
                        url = targetUrl,
                        title = title,
                        markdown = markdown,
                        byteCount = totalRead,
                        isTruncated = isTruncated
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads a binary file (e.g. TTF font, asset, archive) directly to destination file.
     */
    suspend fun downloadBinaryFile(targetUrl: String, destinationFile: File, maxBytes: Long = 10 * 1024 * 1024L): Result<Long> = withContext(Dispatchers.IO) {
        val trimmed = targetUrl.trim()
        val normalizedUrl = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed

        val httpUrl = normalizedUrl.toHttpUrlOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid URL format: $targetUrl"))

        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            return@withContext Result.failure(SecurityException("Scheme '${httpUrl.scheme}' is disallowed."))
        }

        try {
            val address = InetAddress.getByName(httpUrl.host)
            if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) {
                return@withContext Result.failure(SecurityException("Access to local/private network hosts is forbidden."))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("DNS resolution failed for ${httpUrl.host}: ${e.message}"))
        }

        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure(IOException("HTTP Error ${response.code} ${response.message}"))
                }
                val body = response.body ?: return@use Result.failure(IOException("Empty response body"))
                destinationFile.parentFile?.mkdirs()

                destinationFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (bytesCopied > maxBytes) {
                                return@use Result.failure(IOException("File exceeded maximum download size ($maxBytes bytes)"))
                            }
                        }
                        Result.success(bytesCopied)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts HTML to clean readable Markdown.
     * Preserves headings, code blocks, links, lists, and strips layout noise.
     */
    private fun parseHtmlToMarkdown(html: String, fallbackUrl: String): Pair<String, String> {
        // 1. Extract title
        val titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE)
        val titleMatcher = titlePattern.matcher(html)
        val title = if (titleMatcher.find()) cleanEntities(titleMatcher.group(1) ?: "").trim() else fallbackUrl

        var content = html

        // 2. Extract main/article content if available
        val articlePattern = Pattern.compile("<(?:article|main)[^>]*>([\\s\\S]*?)</(?:article|main)>", Pattern.CASE_INSENSITIVE)
        val articleMatcher = articlePattern.matcher(content)
        if (articleMatcher.find()) {
            content = articleMatcher.group(1) ?: content
        }

        // 3. Strip unwanted tags and comments
        content = content.replace(Regex("<!--[\\s\\S]*?-->"), "")
        for (tag in UNWANTED_TAGS) {
            content = content.replace(Regex("<$tag[^>]*>[\\s\\S]*?</$tag>", RegexOption.IGNORE_CASE), "")
        }

        // 4. Preserve pre/code blocks with placeholders
        val codeBlocks = mutableListOf<String>()
        val prePattern = Pattern.compile("<pre[^>]*><code(?:\\s+class=[\"'](?:language-)?([a-zA-Z0-9_-]+)[\"'])?[^>]*>([\\s\\S]*?)</code></pre>", Pattern.CASE_INSENSITIVE)
        val preMatcher = prePattern.matcher(content)
        val preReplaced = StringBuffer()
        while (preMatcher.find()) {
            val lang = preMatcher.group(1) ?: ""
            val code = cleanEntities(preMatcher.group(2) ?: "")
            codeBlocks.add("```$lang\n$code\n```\n")
            preMatcher.appendReplacement(preReplaced, "%%%CODE_BLOCK_${codeBlocks.size - 1}%%%")
        }
        preMatcher.appendTail(preReplaced)
        content = preReplaced.toString()

        // 5. Structure conversion: headings, inline code, links, lists
        content = content.replace(Regex("<h1[^>]*>([\\s\\S]*?)</h1>", RegexOption.IGNORE_CASE), "\n\n# $1\n")
            .replace(Regex("<h2[^>]*>([\\s\\S]*?)</h2>", RegexOption.IGNORE_CASE), "\n\n## $1\n")
            .replace(Regex("<h3[^>]*>([\\s\\S]*?)</h3>", RegexOption.IGNORE_CASE), "\n\n### $1\n")
            .replace(Regex("<code[^>]*>([\\s\\S]*?)</code>", RegexOption.IGNORE_CASE), "`$1`")
            .replace(Regex("<li[^>]*>([\\s\\S]*?)</li>", RegexOption.IGNORE_CASE), "\n- $1")
            .replace(Regex("<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE), "[$2]($1)")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")

        // 6. Strip all remaining tags
        content = content.replace(Regex("<[^>]+>"), "")
        content = cleanEntities(content)

        // 7. Restore preserved code blocks
        codeBlocks.forEachIndexed { idx, code ->
            content = content.replace("%%%CODE_BLOCK_${idx}%%%", "\n$code\n")
        }

        // 8. Normalize spacing and blank lines
        val cleaned = content.lines()
            .map { it.trimEnd() }
            .filterIndexed { index, line ->
                !(index > 0 && line.isBlank() && content.lines().getOrNull(index - 1)?.isBlank() == true)
            }
            .joinToString("\n")
            .trim()

        return Pair(title, cleaned)
    }

    private fun cleanEntities(str: String): String {
        return str
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&apos;", "'")
    }
}
