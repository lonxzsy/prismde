package com.prismde.core.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.prismde.BuildConfig
import com.prismde.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Direct Telegram Bot Reporter for PrismDE.
 * When a build or preview failure occurs, automatically aggregates complete device specs,
 * runtime environment, OS details, and compiler logs, then securely transmits a diagnostic
 * package to the developer's Telegram group via the Telegram Bot API.
 */
object TelegramBugReporter {

    private val BOT_TOKEN: String
        get() = BuildConfig.TELEGRAM_BOT_TOKEN.trim()

    private val CHAT_ID: String
        get() = BuildConfig.TELEGRAM_CHAT_ID.trim().ifBlank { "-5412558044" }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendReport(
        context: Context,
        reportType: String, // "BUILD_FAILURE", "PREVIEW_FAILURE", etc.
        project: Project?,
        errorSummary: String?,
        logs: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        val isRu = Locale.getDefault().language == "ru"
        if (BOT_TOKEN.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    if (isRu) "Telegram Bot Token не задан в конфигурации сборки."
                    else "Telegram Bot Token is not configured in build."
                )
            )
        }
        try {
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val currentTime = timeFormat.format(Date())
            val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

            val memInfo = ActivityManager.MemoryInfo()
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            actManager?.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            val availRamMb = memInfo.availMem / (1024 * 1024)

            val freeInternalDiskMb = try {
                val stat = StatFs(Environment.getDataDirectory().path)
                (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            } catch (_: Throwable) { -1L }

            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val androidVer = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val appVer = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

            val projectName = project?.name ?: "Unknown Project"
            val projectPath = project?.rootDir?.absolutePath ?: "N/A"

            // 1. Build complete diagnostic report document
            val reportContent = buildString {
                appendLine("=================================================================")
                appendLine("           PRISMDE AUTOMATED CRASH & DIAGNOSTIC REPORT          ")
                appendLine("=================================================================")
                appendLine("Report Type        : $reportType")
                appendLine("Timestamp          : $currentTime")
                appendLine("Application Version: PrismDE v$appVer")
                appendLine("Package            : ${context.packageName}")
                appendLine("-----------------------------------------------------------------")
                appendLine("DEVICE HARDWARE & OPERATING SYSTEM")
                appendLine("-----------------------------------------------------------------")
                appendLine("Device             : $manufacturer $model (${Build.PRODUCT})")
                appendLine("Android OS Version : Android $androidVer (API Level $sdkInt)")
                appendLine("Build ID           : ${Build.DISPLAY}")
                appendLine("CPU Architectures  : $abis")
                appendLine("RAM (Available)    : $availRamMb MB / $totalRamMb MB")
                appendLine("Internal Disk Free : ${if (freeInternalDiskMb >= 0) "$freeInternalDiskMb MB" else "Unknown"}")
                appendLine("-----------------------------------------------------------------")
                appendLine("PROJECT CONTEXT")
                appendLine("-----------------------------------------------------------------")
                appendLine("Project Name       : $projectName")
                appendLine("Project Path       : $projectPath")
                if (!errorSummary.isNullOrBlank()) {
                    appendLine("Reported Error     : $errorSummary")
                }
                appendLine("=================================================================")
                appendLine("                       FULL FAILURE LOG                          ")
                appendLine("=================================================================")
                if (logs.isEmpty()) {
                    appendLine("(No additional logs recorded)")
                } else {
                    logs.forEach { line -> appendLine(line) }
                }
                appendLine("=================================================================")
                appendLine("                         END OF REPORT                           ")
                appendLine("=================================================================")
            }

            // 2. Prepare compact Telegram caption (max 1024 characters)
            val captionHeader = when (reportType) {
                "PREVIEW_FAILURE" -> "🚨 Сбой ImGui Preview (GUI)"
                "BUILD_FAILURE" -> "🚨 Сбой сборщика PrismDE"
                else -> "🚨 Баг-репорт PrismDE"
            }

            val cleanError = errorSummary?.lines()?.firstOrNull { it.isNotBlank() }?.take(180)
                ?: logs.firstOrNull { it.contains("error", ignoreCase = true) || it.contains("fail", ignoreCase = true) }?.take(180)
                ?: "Сбой выполнения операции"

            val caption = buildString {
                appendLine("$captionHeader v${BuildConfig.VERSION_NAME}")
                appendLine("📱 $manufacturer $model (Android $androidVer, API $sdkInt)")
                appendLine("⚙️ $abis • RAM: $availRamMb/$totalRamMb MB")
                appendLine("📁 Проект: $projectName")
                appendLine("❌ Ошибка: $cleanError")
            }.trim().take(1000)

            val fileName = "prismde_${reportType.lowercase()}_${fileTimestamp}.log"

            // 3. Construct multipart request for Telegram sendDocument API
            val docPart = reportContent.toByteArray(Charsets.UTF_8)
                .toRequestBody("text/plain; charset=utf-8".toMediaType())

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", CHAT_ID)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", fileName, docPart)
                .build()

            val request = Request.Builder()
                .url("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = try {
                        JSONObject(bodyStr).optString("description", "HTTP ${response.code}")
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext Result.failure(Exception("Telegram API Error: $errMsg"))
                }

                Result.success(if (isRu) "Лог успешно отправлен в Telegram!" else "Report sent to Telegram successfully!")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
