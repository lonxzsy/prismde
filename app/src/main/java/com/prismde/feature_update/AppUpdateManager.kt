package com.prismde.feature_update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String
)

class AppUpdateManager(
    private val context: Context,
    private val client: OkHttpClient
) {

    suspend fun checkForUpdates(
        owner: String = "lonxzsy",
        repo: String = "prismde"
    ): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext Result.success(null) // No releases yet
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("GitHub API error: ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.success(null)
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "")
                val remoteVersion = tagName.removePrefix("v").trim()

                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    val assets = json.optJSONArray("assets") ?: return@withContext Result.success(null)
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            return@withContext Result.success(
                                AppReleaseInfo(
                                    tagName = tagName,
                                    versionName = remoteVersion,
                                    releaseNotes = json.optString("body", if (java.util.Locale.getDefault().language == "ru") "Новая версия доступна для установки." else "New version is available for installation."),
                                    apkDownloadUrl = asset.getString("browser_download_url"),
                                    apkSizeBytes = asset.optLong("size", 0L),
                                    publishedAt = json.optString("published_at", "")
                                )
                            )
                        }
                    }
                }

                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(
        url: String,
        targetFile: File,
        onProgress: (percent: Float, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val total = body.contentLength()
                var current = 0L

                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    val input = body.byteStream()
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        current += read
                        val percent = if (total > 0) (current.toFloat() / total) * 100f else 0f
                        onProgress(percent, current, total)
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun launchApkInstaller(apkFile: File) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)

        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
