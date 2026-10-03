package com.prismde.feature_build.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.prismde.feature_ndk.engine.NdkDownloader
import com.prismde.feature_ndk.engine.NdkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

data class BuildToolInfo(
    val id: String,
    val name: String,
    val version: String,
    val isInstalled: Boolean,
    val installedPath: String?,
    val description: String,
    val sizeLabel: String
)

data class JavaEnvironmentInfo(
    val isAvailable: Boolean,
    val javaHome: File? = null,
    val javaBin: File? = null,
    val sourceDescription: String = ""
)

object BuildToolInstaller {

    const val MAVEN_VERSION = "3.9.6"
    private const val MAVEN_TAR_GZ_URL = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz"
    private const val MAVEN_ZIP_URL = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
    private const val MAVEN_MIRROR_URL = "https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz"

    const val TERMUX_INSTALL_CMD = "pkg update -y && pkg install -y openjdk-17 maven"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloader = NdkDownloader(httpClient)
    private val extractor = NdkExtractor()

    fun getToolsDir(context: Context): File {
        return File(context.filesDir, "tools").also { it.mkdirs() }
    }

    fun getMavenDir(context: Context): File {
        return File(getToolsDir(context), "maven")
    }

    fun isMavenInstalled(context: Context): Boolean {
        return getMavenExecutable(context) != null
    }

    fun getMavenExecutable(context: Context): File? {
        val mavenDir = getMavenDir(context)
        if (!mavenDir.exists()) return null

        val mvnExecutable = mavenDir.walkTopDown().firstOrNull { file ->
            file.isFile && (file.name == "mvn" || file.name == "mvn.cmd") && file.parentFile?.name == "bin"
        }

        mvnExecutable?.let {
            try { it.setExecutable(true, false) } catch (_: Throwable) {}
        }
        return mvnExecutable
    }

    suspend fun installMaven(
        context: Context,
        onProgress: (statusMessage: String, percent: Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val mavenTargetDir = getMavenDir(context)
        mavenTargetDir.mkdirs()

        val tempArchive = File(context.cacheDir, "apache-maven-$MAVEN_VERSION-bin.tar.gz")

        try {
            onProgress(
                if (isRu) "Загрузка Apache Maven $MAVEN_VERSION (~9 МБ)..."
                else "Downloading Apache Maven $MAVEN_VERSION (~9 MB)...",
                10f
            )

            var downloadSuccess = false
            val urls = listOf(MAVEN_TAR_GZ_URL, MAVEN_MIRROR_URL, MAVEN_ZIP_URL)

            for (url in urls) {
                try {
                    downloader.download(url, tempArchive) { current, total, percent, _ ->
                        val scaled = 10f + (percent * 0.6f) // 10% to 70%
                        onProgress(
                            if (isRu) "Загрузка Apache Maven: ${(current / (1024 * 1024))} МБ / ${(total / (1024 * 1024))} МБ (${percent.toInt()}%)"
                            else "Downloading Apache Maven: ${(current / (1024 * 1024))} MB / ${(total / (1024 * 1024))} MB (${percent.toInt()}%)",
                            scaled
                        )
                    }
                    downloadSuccess = true
                    break
                } catch (e: Exception) {
                    tempArchive.delete()
                }
            }

            if (!downloadSuccess) {
                onProgress(
                    if (isRu) "✖ Ошибка: Не удалось загрузить архив Maven"
                    else "✖ Error: Failed to download Maven archive",
                    0f
                )
                return@withContext false
            }

            onProgress(
                if (isRu) "Распаковка Apache Maven..."
                else "Extracting Apache Maven...",
                75f
            )

            val extractSuccess = extractor.extract(tempArchive, mavenTargetDir) { msg ->
                onProgress(msg, 85f)
            }

            tempArchive.delete()

            if (!extractSuccess) {
                return@withContext false
            }

            // Ensure all files in bin/ are executable
            mavenTargetDir.walkTopDown().filter { it.parentFile?.name == "bin" }.forEach {
                try { it.setExecutable(true, false) } catch (_: Throwable) {}
            }

            val installedMvn = getMavenExecutable(context)
            val success = installedMvn != null && installedMvn.exists()
            if (success) {
                onProgress(
                    if (isRu) "✔ Apache Maven $MAVEN_VERSION успешно установлен!"
                    else "✔ Apache Maven $MAVEN_VERSION installed successfully!",
                    100f
                )
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            tempArchive.delete()
            false
        }
    }

    fun detectJavaEnvironment(context: Context? = null, workingDir: File? = null): JavaEnvironmentInfo {
        val isRu = java.util.Locale.getDefault().language == "ru"

        // 1. Check local.properties in project
        if (workingDir != null) {
            val lp = File(workingDir, "local.properties")
            if (lp.exists()) {
                try {
                    for (line in lp.readLines()) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("org.gradle.java.home=") || trimmed.startsWith("java.home=")) {
                            val path = trimmed.substringAfter("=").replace("\\:", ":").replace("\\\\", "/")
                            val dir = File(path)
                            val bin = File(dir, "bin/java")
                            if (dir.exists()) {
                                return JavaEnvironmentInfo(
                                    isAvailable = true,
                                    javaHome = dir,
                                    javaBin = if (bin.exists()) bin else null,
                                    sourceDescription = "local.properties ($path)"
                                )
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Check internal tools/jdk if present
        if (context != null) {
            val internalJdk = File(getToolsDir(context), "jdk")
            if (internalJdk.exists()) {
                val bin = File(internalJdk, "bin/java")
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = internalJdk,
                    javaBin = if (bin.exists()) bin else null,
                    sourceDescription = if (isRu) "Внутренний JDK (${internalJdk.absolutePath})" else "Internal JDK (${internalJdk.absolutePath})"
                )
            }
        }

        // 3. System environment JAVA_HOME
        val envJava = System.getenv("JAVA_HOME")
        if (!envJava.isNullOrBlank()) {
            val f = File(envJava)
            if (f.exists()) {
                val bin = File(f, "bin/java")
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = f,
                    javaBin = if (bin.exists()) bin else null,
                    sourceDescription = "ENV JAVA_HOME ($envJava)"
                )
            }
        }

        // 4. Termux / AndroidIDE JVMs
        val candidateJvms = listOf(
            File("/data/data/com.termux/files/usr/lib/jvm/openjdk-17") to "Termux openjdk-17",
            File("/data/data/com.termux/files/usr/lib/jvm/openjdk-21") to "Termux openjdk-21",
            File("/data/data/com.termux/files/usr/lib/jvm/default-jvm") to "Termux default-jvm",
            File("/data/user/0/com.termux/files/usr/lib/jvm/openjdk-17") to "Termux openjdk-17",
            File("/data/data/com.itsaky.androidide/files/usr/lib/jvm/openjdk-17") to "AndroidIDE openjdk-17"
        )
        for ((dir, desc) in candidateJvms) {
            if (dir.exists()) {
                val bin = File(dir, "bin/java")
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = dir,
                    javaBin = if (bin.exists()) bin else null,
                    sourceDescription = desc
                )
            }
        }

        // 5. Binary paths in Termux / system PATH
        val binCandidates = listOf(
            File("/data/data/com.termux/files/usr/bin/java") to "Termux bin (/usr/bin/java)",
            File("/data/user/0/com.termux/files/usr/bin/java") to "Termux bin",
            File("/data/data/com.itsaky.androidide/files/usr/bin/java") to "AndroidIDE bin",
            File("/system/bin/java") to "Android System bin",
            File("/system/xbin/java") to "Android System xbin"
        )
        for ((bin, desc) in binCandidates) {
            if (bin.exists()) {
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = bin.parentFile?.parentFile,
                    javaBin = bin,
                    sourceDescription = desc
                )
            }
        }

        // 6. Search PATH
        val path = System.getenv("PATH") ?: ""
        for (dir in path.split(File.pathSeparator)) {
            val exeName = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) "java.exe" else "java"
            val exe = File(dir, exeName)
            if (exe.exists()) {
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = exe.parentFile?.parentFile,
                    javaBin = exe,
                    sourceDescription = "PATH ($dir)"
                )
            }
        }

        return JavaEnvironmentInfo(
            isAvailable = false,
            sourceDescription = if (isRu) "Не найден на устройстве" else "Not found on device"
        )
    }

    fun getToolsBinDirs(context: Context): List<File> {
        val toolsDir = getToolsDir(context)
        if (!toolsDir.exists()) return emptyList()

        val binDirs = mutableListOf<File>()
        toolsDir.walkTopDown().maxDepth(4).forEach { file ->
            if (file.isDirectory && file.name.equals("bin", ignoreCase = true)) {
                binDirs.add(file)
            }
        }
        return binDirs
    }

    fun getAvailableBuildTools(context: Context): List<BuildToolInfo> {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val mavenExe = getMavenExecutable(context)
        val javaInfo = detectJavaEnvironment(context)

        return listOf(
            BuildToolInfo(
                id = "maven",
                name = "Apache Maven",
                version = MAVEN_VERSION,
                isInstalled = mavenExe != null,
                installedPath = mavenExe?.absolutePath,
                description = if (isRu)
                    "Система автоматизации сборки Java/Kotlin и Android библиотек"
                else
                    "Build automation tool for Java/Kotlin and Android libraries",
                sizeLabel = "~9 MB"
            ),
            BuildToolInfo(
                id = "jdk",
                name = "Java Development Kit (JDK)",
                version = "17+",
                isInstalled = javaInfo.isAvailable,
                installedPath = javaInfo.javaHome?.absolutePath ?: javaInfo.javaBin?.absolutePath,
                description = if (isRu)
                    "Среда выполнения и компилятор Java, необходимый для Maven и Gradle"
                else
                    "Java Runtime and Compiler required for Maven and Gradle builds",
                sizeLabel = javaInfo.sourceDescription
            )
        )
    }
}
