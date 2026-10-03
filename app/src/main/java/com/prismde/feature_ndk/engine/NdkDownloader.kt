package com.prismde.feature_ndk.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class NdkDownloader(private val client: OkHttpClient) {

    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Float, speedBytesPerSec: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            downloadInternal(url, destinationFile, allowResume = true, onProgress = onProgress)
        } catch (e: Exception) {
            // If Range was not satisfiable (HTTP 416) or corrupted resume, delete and retry fresh
            if (e.message?.contains("416") == true || destinationFile.exists()) {
                destinationFile.delete()
                downloadInternal(url, destinationFile, allowResume = false, onProgress = onProgress)
            } else {
                throw e
            }
        }
    }

    private fun downloadInternal(
        url: String,
        destinationFile: File,
        allowResume: Boolean,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percent: Float, speedBytesPerSec: Long) -> Unit
    ) {
        val existingLength = if (allowResume && destinationFile.exists()) destinationFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            .apply {
                if (existingLength > 0) {
                    header("Range", "bytes=$existingLength-")
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                throw IOException("HTTP 416 Range Not Satisfiable")
            }

            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Download failed with HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body from $url")
            val isPartial = response.code == 206

            // If server returned 200 instead of 206, it ignored Range, so start from 0
            val startOffset = if (isPartial) existingLength else 0L
            val totalBytes = startOffset + body.contentLength()
            var currentBytes = startOffset

            var lastTime = System.currentTimeMillis()
            var lastProgressTime = 0L
            var bytesSinceLastTime = 0L
            var currentSpeed = 0L

            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile, isPartial).use { output ->
                val buffer = ByteArray(64 * 1024)
                val source = body.byteStream()
                var read: Int

                while (source.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    currentBytes += read
                    bytesSinceLastTime += read

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 500) {
                        currentSpeed = (bytesSinceLastTime * 1000) / (now - lastTime)
                        lastTime = now
                        bytesSinceLastTime = 0
                    }

                    val percent = if (totalBytes > 0) (currentBytes.toFloat() / totalBytes) * 100f else 0f
                    if (now - lastProgressTime >= 250 || currentBytes >= totalBytes || percent >= 100f) {
                        lastProgressTime = now
                        onProgress(currentBytes, totalBytes, percent, currentSpeed)
                    }
                }
                val finalPercent = if (totalBytes > 0) (currentBytes.toFloat() / totalBytes) * 100f else 100f
                onProgress(currentBytes, totalBytes, finalPercent, currentSpeed)
            }
        }
    }
}
