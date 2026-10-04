package com.prismde.feature_build.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.prismde.core.model.NdkVersion
import com.prismde.feature_ndk.engine.NdkDownloader
import com.prismde.feature_ndk.engine.NdkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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

    const val GRADLE_VERSION = "8.5"
    private const val GRADLE_ZIP_URL_PRIMARY = "https://mirrors.cloud.tencent.com/gradle/gradle-8.5-bin.zip"
    private const val GRADLE_ZIP_URL_OFFICIAL = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
    private const val GRADLE_ZIP_URL_ALIYUN = "https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.5-bin.zip"
    private const val GRADLE_ZIP_URL_FALLBACK = "https://downloads.gradle-dn.com/distributions/gradle-8.5-bin.zip"

    const val JDK_VERSION = "17.0.20"
    const val JDK_BUILD_TAG = "17.0.20-termux-deb-v3"

    // Official Termux OpenJDK 17 LTS packages with built-in Android 12+ tagged pointers fix (patch 0021)
    const val JDK_DEB_URL_PRIMARY = "https://packages.termux.dev/apt/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb"
    const val JDK_DEB_URL_TSINGHUA = "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb"
    const val JDK_DEB_URL_BFSU = "https://mirrors.bfsu.edu.cn/termux/apt/termux-main/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb"
    const val JDK_DEB_URL_GRIMLER = "https://grimler.se/termux-packages-24/pool/main/o/openjdk-17/openjdk-17_17.0.20_aarch64.deb"

    // Fallback mirrors
    const val JDK_TAR_XZ_FALLBACK = "https://gh-proxy.com/https://github.com/zryyoung/openjdk-Termux/releases/download/openjdk-17/openjdk-17-aarch64.tar.xz"
    const val JDK_TAR_XZ_CDN = "https://ghfast.top/https://github.com/zryyoung/openjdk-Termux/releases/download/openjdk-17/openjdk-17-aarch64.tar.xz"

    const val TERMUX_INSTALL_CMD = "pkg update -y && pkg install -y openjdk-17 maven"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val downloader = NdkDownloader(httpClient)
    private val extractor = NdkExtractor()

    fun getToolsDir(context: Context): File {
        return File(context.filesDir, "tools").also { it.mkdirs() }
    }

    // ==================== Maven Management ====================

    fun getMavenDir(context: Context): File {
        return File(getToolsDir(context), "maven")
    }

    fun isMavenInstalled(context: Context): Boolean {
        return getMavenExecutable(context) != null
    }

    fun getMavenExecutable(context: Context): File? {
        val mavenDir = getMavenDir(context)
        if (!mavenDir.exists()) return null

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

        // On Android / Linux, NEVER execute mvn.cmd (it is a Windows batch file and causes syntax error in /system/bin/sh)
        val mvnExecutable = if (isWindows) {
            mavenDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name == "mvn.cmd" && file.parentFile?.name == "bin"
            } ?: mavenDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name == "mvn" && file.parentFile?.name == "bin"
            }
        } else {
            mavenDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name == "mvn" && file.parentFile?.name == "bin"
            }
        }

        mvnExecutable?.let {
            try { it.setExecutable(true, false) } catch (_: Throwable) {}
        }
        return mvnExecutable
    }

    fun getMavenHomeDir(context: Context): File? {
        val mvn = getMavenExecutable(context) ?: return null
        return mvn.parentFile?.parentFile ?: getMavenDir(context)
    }

    fun getMavenLauncherJar(context: Context): File? {
        val home = getMavenHomeDir(context) ?: return null
        val bootDir = File(home, "boot")
        if (!bootDir.exists()) return null
        return bootDir.listFiles { _, name -> name.startsWith("plexus-classworlds") && name.endsWith(".jar") }?.firstOrNull()
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
                if (!msg.startsWith("Распаковка:") && !msg.startsWith("Unpacking:")) {
                    onProgress(msg, 85f)
                }
            }

            tempArchive.delete()

            if (!extractSuccess) {
                return@withContext false
            }

            // 1. Remove glibc jansi-native libraries which crash on Android Bionic (libc.so.6 not found)
            val jansiNativeDir = File(mavenTargetDir, "lib/jansi-native")
            if (jansiNativeDir.exists()) {
                try { jansiNativeDir.deleteRecursively() } catch (_: Throwable) {}
            }

            // 2. Patch bin/mvn script to eliminate -Dlibrary.jansi.path loading
            val mvnExecutable = getMavenExecutable(context)
            if (mvnExecutable != null && mvnExecutable.exists() && !mvnExecutable.name.endsWith(".cmd")) {
                try {
                    val scriptText = mvnExecutable.readText()
                    if (scriptText.contains("jansi-native")) {
                        val patched = scriptText.replace("\"\${MAVEN_HOME}/lib/jansi-native\"", "\"\"")
                                                .replace("\${MAVEN_HOME}/lib/jansi-native", "")
                        mvnExecutable.writeText(patched)
                    }
                } catch (_: Throwable) {}
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

    // ==================== Standalone Gradle Management ====================

    fun getGradleDir(context: Context): File {
        return File(getToolsDir(context), "gradle")
    }

    fun isGradleInstalled(context: Context): Boolean {
        return getGradleExecutable(context) != null
    }

    fun getGradleExecutable(context: Context): File? {
        val gradleDir = getGradleDir(context)
        if (!gradleDir.exists()) return null

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

        val exe = if (isWindows) {
            gradleDir.walkTopDown().firstOrNull { file ->
                file.isFile && (file.name == "gradle.bat" || file.name == "gradle.cmd") && file.parentFile?.name == "bin"
            } ?: gradleDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name == "gradle" && file.parentFile?.name == "bin"
            }
        } else {
            gradleDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name == "gradle" && file.parentFile?.name == "bin"
            }
        }

        exe?.let {
            try { it.setExecutable(true, false) } catch (_: Throwable) {}
        }
        return exe
    }

    fun getGradleHomeDir(context: Context): File? {
        val exe = getGradleExecutable(context) ?: return null
        return exe.parentFile?.parentFile ?: getGradleDir(context)
    }

    suspend fun installGradle(
        context: Context,
        onProgress: (statusMessage: String, percent: Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val gradleTargetDir = getGradleDir(context)
        gradleTargetDir.mkdirs()

        val tempArchive = File(context.cacheDir, "gradle-$GRADLE_VERSION-bin.zip")

        try {
            onProgress(
                if (isRu) "Загрузка Gradle $GRADLE_VERSION (~125 МБ)..."
                else "Downloading Gradle $GRADLE_VERSION (~125 MB)...",
                5f
            )

            var downloadSuccess = false
            val urls = listOf(
                GRADLE_ZIP_URL_PRIMARY,
                GRADLE_ZIP_URL_OFFICIAL,
                GRADLE_ZIP_URL_ALIYUN,
                GRADLE_ZIP_URL_FALLBACK
            )

            for (url in urls) {
                try {
                    downloader.download(url, tempArchive) { current, total, percent, _ ->
                        val scaled = 5f + (percent * 0.7f) // 5% to 75%
                        onProgress(
                            if (isRu) "Загрузка Gradle: ${(current / (1024 * 1024))} МБ / ${(total / (1024 * 1024))} МБ (${percent.toInt()}%)"
                            else "Downloading Gradle: ${(current / (1024 * 1024))} MB / ${(total / (1024 * 1024))} MB (${percent.toInt()}%)",
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
                    if (isRu) "✖ Ошибка: Не удалось загрузить архив Gradle"
                    else "✖ Error: Failed to download Gradle archive",
                    0f
                )
                return@withContext false
            }

            onProgress(
                if (isRu) "Распаковка Gradle..."
                else "Extracting Gradle...",
                80f
            )

            val extractSuccess = extractor.extract(tempArchive, gradleTargetDir) { msg ->
                if (!msg.startsWith("Распаковка:") && !msg.startsWith("Unpacking:")) {
                    onProgress(msg, 88f)
                }
            }

            tempArchive.delete()

            if (!extractSuccess) {
                return@withContext false
            }

            // Ensure all binaries in bin/ are executable and have Unix LF line endings
            gradleTargetDir.walkTopDown().filter { it.parentFile?.name == "bin" }.forEach {
                try { it.setExecutable(true, false) } catch (_: Throwable) {}
                try { it.setReadable(true, false) } catch (_: Throwable) {}
                if (!it.name.endsWith(".bat") && !it.name.endsWith(".cmd")) {
                    try {
                        val txt = it.readText()
                        if (txt.contains("\r\n")) {
                            it.writeText(txt.replace("\r\n", "\n"))
                        }
                    } catch (_: Throwable) {}
                }
            }

            val installedExe = getGradleExecutable(context)
            val success = installedExe != null && installedExe.exists()
            if (success) {
                onProgress(
                    if (isRu) "✔ Gradle $GRADLE_VERSION успешно установлен!"
                    else "✔ Gradle $GRADLE_VERSION installed successfully!",
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

    /**
     * Ensures the project has gradle wrapper files (gradlew, gradlew.bat, gradle/wrapper/...)
     * and a properly configured gradle.properties for optimal execution on Android.
     */
    fun ensureGradleWrapper(context: Context, projectRootDir: File, force: Boolean = false): Boolean {
        return try {
            val gradlew = File(projectRootDir, "gradlew")
            val gradlewBat = File(projectRootDir, "gradlew.bat")
            val wrapperJar = File(projectRootDir, "gradle/wrapper/gradle-wrapper.jar")
            val wrapperProps = File(projectRootDir, "gradle/wrapper/gradle-wrapper.properties")

            val assetManager = context.assets

            fun copyAssetFile(assetPath: String, destFile: File) {
                destFile.parentFile?.mkdirs()
                assetManager.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val needsUpdateGradlew = force || !gradlew.exists() || gradlew.length() == 0L ||
                    try {
                        val txt = gradlew.readText()
                        txt.contains("\"-Xmx64m\"") || txt.contains("xargs -n1")
                    } catch (_: Throwable) { false }

            if (needsUpdateGradlew) {
                copyAssetFile("gradle_wrapper/gradlew", gradlew)
            }
            if (!gradlewBat.exists() || gradlewBat.length() == 0L) {
                copyAssetFile("gradle_wrapper/gradlew.bat", gradlewBat)
            }
            if (!wrapperJar.exists() || wrapperJar.length() == 0L) {
                copyAssetFile("gradle_wrapper/gradle/wrapper/gradle-wrapper.jar", wrapperJar)
            }
            if (!wrapperProps.exists() || wrapperProps.length() == 0L) {
                copyAssetFile("gradle_wrapper/gradle/wrapper/gradle-wrapper.properties", wrapperProps)
            }

            // Ensure executable permissions and unix line endings on gradlew
            if (gradlew.exists()) {
                try { gradlew.setExecutable(true, false) } catch (_: Throwable) {}
                try { gradlew.setReadable(true, false) } catch (_: Throwable) {}
                val text = gradlew.readText()
                if (text.contains("\r\n")) {
                    gradlew.writeText(text.replace("\r\n", "\n"))
                }
            }

            // Ensure gradle.properties exists with Android-optimized settings
            val gradleProps = File(projectRootDir, "gradle.properties")
            val javaHome = getJdkHomeDir(context)?.absolutePath
            val defaultJvmArgs = "-XX:-UseCompressedOops -XX:-UseCompressedClassPointers -Xmx1024m"
            if (!gradleProps.exists()) {
                val sb = java.lang.StringBuilder()
                sb.appendLine("# PrismDE Android Build Optimizations")
                sb.appendLine("org.gradle.jvmargs=$defaultJvmArgs")
                sb.appendLine("org.gradle.daemon=false")
                sb.appendLine("org.gradle.parallel=false")
                sb.appendLine("org.gradle.vfs.watch=false")
                sb.appendLine("org.gradle.console=plain")
                sb.appendLine("android.suppressUnsupportedCompileSdk=34,35")
                if (!javaHome.isNullOrBlank()) {
                    sb.appendLine("org.gradle.java.home=${javaHome.replace("\\", "/")}")
                }
                gradleProps.writeText(sb.toString())
            } else {
                var propsText = gradleProps.readText()
                var modified = false
                if (!propsText.contains("org.gradle.jvmargs")) {
                    propsText += "\norg.gradle.jvmargs=$defaultJvmArgs\n"
                    modified = true
                }
                if (!propsText.contains("org.gradle.daemon")) {
                    propsText += "\norg.gradle.daemon=false\n"
                    modified = true
                }
                if (!propsText.contains("org.gradle.vfs.watch")) {
                    propsText += "\norg.gradle.vfs.watch=false\n"
                    modified = true
                }
                if (!propsText.contains("org.gradle.console")) {
                    propsText += "\norg.gradle.console=plain\n"
                    modified = true
                }
                if (!propsText.contains("android.suppressUnsupportedCompileSdk")) {
                    propsText += "\nandroid.suppressUnsupportedCompileSdk=34,35\n"
                    modified = true
                }
                val withoutSdkDownloadOverride = propsText
                    .lineSequence()
                    .filterNot { it.trim().matches(Regex("android\\.builder\\.sdkDownload\\s*=.*")) }
                    .joinToString("\n")
                if (withoutSdkDownloadOverride != propsText) {
                    propsText = withoutSdkDownloadOverride
                    modified = true
                }
                if (!propsText.contains("org.gradle.java.home") && !javaHome.isNullOrBlank()) {
                    propsText += "\norg.gradle.java.home=${javaHome.replace("\\", "/")}\n"
                    modified = true
                }
                if (modified) {
                    gradleProps.writeText(propsText)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==================== Android SDK & Tools Management ====================

    const val ANDROID_PLATFORM_API_DEFAULT = 34
    const val ANDROID_BUILD_TOOLS_VERSION_DEFAULT = "34.0.0"

    fun getAndroidSdkDir(context: Context): File {
        return File(getToolsDir(context), "android-sdk")
    }

    fun findExistingSdk(context: Context? = null): File {
        val candidates = listOf(
            context?.let { getAndroidSdkDir(it) },
            File("/data/data/com.termux/files/home/android-sdk"),
            File("/data/user/0/com.termux/files/home/android-sdk"),
            File("/data/data/com.itsaky.androidide/files/usr/lib/android-sdk"),
            File("/sdcard/Android/sdk"),
            File("/sdcard/android-sdk"),
            File("/data/local/android-sdk")
        ).filterNotNull()

        return candidates.firstOrNull { dir ->
            dir.exists() && (File(dir, "platforms").exists() || File(dir, "build-tools").exists())
        } ?: (if (context != null) getAndroidSdkDir(context) else File("/sdcard/Android/sdk"))
    }

    fun ensureAndroidSdk(context: Context): File {
        val sdkDir = getAndroidSdkDir(context)
        sdkDir.mkdirs()
        File(sdkDir, "platforms").mkdirs()
        File(sdkDir, "build-tools").mkdirs()
        val licensesDir = File(sdkDir, "licenses").also { it.mkdirs() }

        // Accept official Google SDK licenses so AGP and Gradle don't complain
        val sdkLicenseFile = File(licensesDir, "android-sdk-license")
        if (!sdkLicenseFile.exists() || sdkLicenseFile.length() == 0L) {
            try {
                sdkLicenseFile.writeText(
                    "24333f8a63b6825ea9c5514f83c2829b004d1fee\n" +
                    "d56f5187479451eabf01fb78af6dfcb131a6481e\n" +
                    "84831b9409646a918e30573bab4c9c91346d8abd\n"
                )
            } catch (_: Throwable) {}
        }

        val previewLicenseFile = File(licensesDir, "android-sdk-preview-license")
        if (!previewLicenseFile.exists() || previewLicenseFile.length() == 0L) {
            try { previewLicenseFile.writeText("84831b9409646a918e30573bab4c9c91346d8abd\n") } catch (_: Throwable) {}
        }

        val googletvLicenseFile = File(licensesDir, "android-googletv-license")
        if (!googletvLicenseFile.exists() || googletvLicenseFile.length() == 0L) {
            try { googletvLicenseFile.writeText("601085b94cd77f0b54ff864069554494414c4d6d\n") } catch (_: Throwable) {}
        }

        return sdkDir
    }

    fun ensureLocalProperties(
        projectRootDir: File,
        sdkDir: File,
        ndkDir: File? = null,
        context: Context? = null
    ) {
        val localProps = File(projectRootDir, "local.properties")
        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val formattedSdkPath = if (isWindows) {
            sdkDir.absolutePath.replace("\\", "/").replace(":", "\\:")
        } else {
            sdkDir.absolutePath
        }

        // Detect if project explicitly specifies ndkVersion in build.gradle
        val ndkVersionRegex = Regex("""ndkVersion\s*=?\s*['"]([^'"]+)['"]""")
        var projectRequestedNdkVersion: String? = null
        val buildGradleFiles = listOf(
            File(projectRootDir, "app/build.gradle"),
            File(projectRootDir, "build.gradle"),
            File(projectRootDir, "app/build.gradle.kts"),
            File(projectRootDir, "build.gradle.kts")
        )
        for (bg in buildGradleFiles) {
            if (bg.exists()) {
                try {
                    val text = bg.readText()
                    val match = ndkVersionRegex.find(text)
                    if (match != null) {
                        projectRequestedNdkVersion = match.groupValues[1]
                        break
                    }
                } catch (_: Throwable) {}
            }
        }

        val effectiveRevision = projectRequestedNdkVersion ?: "26.2.11394342"

        var hasSdkNdk = false
        // Ensure source.properties, meta/abis.json, and sysroot STL exist in ndkDir and parent folders
        if (ndkDir != null && ndkDir.exists()) {
            NdkVersion.ensureNdkMetadata(ndkDir, effectiveRevision, context)
            NdkVersion.ensureNdkPermissions(ndkDir, context)
            NdkVersion.ensureNdkStlLibraries(ndkDir, context)

            // Also provision $sdkDir/ndk/$effectiveRevision and fallback symlinks for AGP NDK resolution
            val sdkNdkRevisions = listOf(effectiveRevision, "25.1.8937393")
            for (rev in sdkNdkRevisions) {
                try {
                    val sdkNdkDir = File(sdkDir, "ndk/$rev")
                    if (sdkNdkDir.exists()) {
                        try {
                            val canonSdk = sdkNdkDir.canonicalFile
                            val canonNdk = ndkDir.canonicalFile
                            if (canonSdk.absolutePath != canonNdk.absolutePath) {
                                try { sdkNdkDir.delete() } catch (_: Throwable) {}
                                try { android.system.Os.remove(sdkNdkDir.absolutePath) } catch (_: Throwable) {}
                                try { android.system.Os.symlink(ndkDir.absolutePath, sdkNdkDir.absolutePath) } catch (_: Throwable) {}
                            }
                        } catch (_: Throwable) {}
                    } else {
                        sdkNdkDir.parentFile?.mkdirs()
                        try {
                            android.system.Os.symlink(ndkDir.absolutePath, sdkNdkDir.absolutePath)
                        } catch (_: Throwable) {}
                    }
                    if (sdkNdkDir.exists()) {
                        NdkVersion.ensureNdkMetadata(sdkNdkDir, rev, context)
                        NdkVersion.ensureNdkPermissions(sdkNdkDir, context)
                        NdkVersion.ensureNdkStlLibraries(sdkNdkDir, context)
                        if (rev == effectiveRevision) {
                            hasSdkNdk = true
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        val formattedNdkPath = if (ndkDir != null && ndkDir.exists()) {
            if (isWindows) {
                ndkDir.absolutePath.replace("\\", "/").replace(":", "\\:")
            } else {
                ndkDir.absolutePath
            }
        } else null

        if (!localProps.exists()) {
            val sb = java.lang.StringBuilder()
            sb.appendLine("# Location of the SDK. This is only used by Gradle.")
            sb.appendLine("sdk.dir=$formattedSdkPath")
            if (formattedNdkPath != null) {
                if (hasSdkNdk) {
                    // Comment out ndk.dir so AGP uses sdk.dir/ndk/$effectiveRevision and suppresses CXX5106
                    sb.appendLine("# ndk.dir=$formattedNdkPath")
                } else {
                    sb.appendLine("ndk.dir=$formattedNdkPath")
                }
            }
            try { localProps.writeText(sb.toString()) } catch (_: Throwable) {}
        } else {
            try {
                val lines = localProps.readLines().toMutableList()
                var hasSdk = false
                var hasNdk = false
                for (i in lines.indices) {
                    val trimmed = lines[i].trim()
                    if (trimmed.startsWith("sdk.dir=")) {
                        lines[i] = "sdk.dir=$formattedSdkPath"
                        hasSdk = true
                    }
                    if (trimmed.startsWith("ndk.dir=") || trimmed.startsWith("# ndk.dir=")) {
                        if (formattedNdkPath != null) {
                            lines[i] = if (hasSdkNdk) "# ndk.dir=$formattedNdkPath" else "ndk.dir=$formattedNdkPath"
                            hasNdk = true
                        }
                    }
                }
                if (!hasSdk) {
                    lines.add("sdk.dir=$formattedSdkPath")
                }
                if (!hasNdk && formattedNdkPath != null) {
                    if (hasSdkNdk) {
                        lines.add("# ndk.dir=$formattedNdkPath")
                    } else {
                        lines.add("ndk.dir=$formattedNdkPath")
                    }
                }
                localProps.writeText(lines.joinToString("\n"))
            } catch (_: Throwable) {}
        }
    }

    /**
     * Ensures project build scripts and Application.mk files do not attempt to build
     * deprecated/removed ABIs like 'armeabi', enforces ndkVersion and the target ABI filter.
     */
    fun ensureProjectAbiFilters(
        projectRootDir: File,
        selectedAbi: String = "arm64-v8a",
        ndkRevision: String = "26.2.11394342"
    ) {
        // 1. Sanitize app/build.gradle and build.gradle
        val buildGradleFiles = listOf(
            File(projectRootDir, "app/build.gradle"),
            File(projectRootDir, "build.gradle"),
            File(projectRootDir, "app/build.gradle.kts"),
            File(projectRootDir, "build.gradle.kts")
        )
        for (bg in buildGradleFiles) {
            if (bg.exists() && bg.isFile) {
                try {
                    var txt = bg.readText()
                    var modified = false

                    // Ensure ndkVersion is explicitly defined in android { } to eliminate CXX5106 warning
                    if (txt.contains("android {") && !txt.contains("ndkVersion")) {
                        txt = txt.replaceFirst("android {", "android {\n    ndkVersion '$ndkRevision'")
                        modified = true
                    }

                    // Remove unsupported legacy 'armeabi' ABI if present
                    if (txt.contains("'armeabi'") && !txt.contains("'armeabi-v7a'")) {
                        txt = txt.replace("'armeabi'", "'$selectedAbi'")
                        modified = true
                    } else if (txt.contains("\"armeabi\"") && !txt.contains("\"armeabi-v7a\"")) {
                        txt = txt.replace("\"armeabi\"", "\"$selectedAbi\"")
                        modified = true
                    } else if (txt.contains("'armeabi',") || txt.contains(", 'armeabi'")) {
                        txt = txt.replace("'armeabi',", "").replace(", 'armeabi'", "")
                        modified = true
                    }

                    // If externalNativeBuild is used, ensure defaultConfig has ndk { abiFilters ... }
                    if (txt.contains("externalNativeBuild") && !txt.contains("ndk {") && txt.contains("defaultConfig {")) {
                        txt = txt.replace(
                            "defaultConfig {",
                            "defaultConfig {\n        ndk {\n            abiFilters '$selectedAbi'\n        }"
                        )
                        modified = true
                    }

                    if (modified) {
                        bg.writeText(txt)
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Sanitize Application.mk
        val appMkCandidates = listOf(
            File(projectRootDir, "app/src/main/jni/Application.mk"),
            File(projectRootDir, "src/main/jni/Application.mk"),
            File(projectRootDir, "jni/Application.mk")
        )
        for (appMk in appMkCandidates) {
            if (appMk.exists() && appMk.isFile) {
                try {
                    var txt = appMk.readText()
                    var modified = false
                    if (txt.contains("APP_ABI := all")) {
                        txt = txt.replace("APP_ABI := all", "APP_ABI := $selectedAbi")
                        modified = true
                    }
                    if (txt.contains("armeabi ") || txt.endsWith("armeabi")) {
                        txt = txt.replace(Regex("""\barmeabi\b(?!\-v7a)"""), selectedAbi)
                        modified = true
                    }
                    if (modified) {
                        appMk.writeText(txt)
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Detects the compileSdk / compileSdkVersion requested by the user's Gradle project.
     * Falls back to ANDROID_PLATFORM_API_DEFAULT (34) if not found.
     */
    fun detectProjectCompileSdk(projectRootDir: File): Int {
        val compileSdkRegex = Regex("""compileSdk(?:Version)?\s*=?\s*['"]?(\d+)['"]?""")
        val buildGradleFiles = listOf(
            File(projectRootDir, "app/build.gradle"),
            File(projectRootDir, "app/build.gradle.kts"),
            File(projectRootDir, "build.gradle"),
            File(projectRootDir, "build.gradle.kts")
        )
        for (bg in buildGradleFiles) {
            if (bg.exists() && bg.isFile) {
                try {
                    val text = bg.readText()
                    val match = compileSdkRegex.find(text)
                    if (match != null) {
                        val api = match.groupValues[1].toIntOrNull()
                        if (api != null && api in 21..36) return api
                    }
                } catch (_: Throwable) {}
            }
        }
        return ANDROID_PLATFORM_API_DEFAULT
    }

    /** Returns an explicitly requested build-tools version, if the project declares one. */
    fun detectProjectBuildToolsVersion(projectRootDir: File): String? {
        val buildToolsRegex = Regex("""buildToolsVersion\s*=?\s*[\"']([^\"']+)[\"']""")
        val buildGradleFiles = listOf(
            File(projectRootDir, "app/build.gradle"),
            File(projectRootDir, "app/build.gradle.kts"),
            File(projectRootDir, "build.gradle"),
            File(projectRootDir, "build.gradle.kts")
        )
        for (bg in buildGradleFiles) {
            if (bg.exists() && bg.isFile) {
                try {
                    val match = buildToolsRegex.find(bg.readText())
                    val version = match?.groupValues?.getOrNull(1)?.trim()
                    if (!version.isNullOrBlank() && Regex("""\d+\.\d+\.\d+""").matches(version)) {
                        return version
                    }
                } catch (_: Throwable) {}
            }
        }

        // Kotlin/Gradle projects sometimes keep the value in a version catalog.
        val catalog = File(projectRootDir, "gradle/libs.versions.toml")
        if (catalog.exists()) {
            try {
                val catalogRegex = Regex("""(?im)^\s*[\w.-]*(?:buildTools|build-tools)[\w.-]*\s*=\s*[\"'](\d+\.\d+\.\d+)[\"']""")
                catalogRegex.find(catalog.readText())?.groupValues?.getOrNull(1)?.let { return it }
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Makes diverted platform directories available under the canonical Android SDK name.
     * Official SDK metadata is intentionally preserved because AGP uses it to identify targets.
     */
    fun normalizeAllSdkPlatforms(platformsDir: File) {
        if (!platformsDir.exists()) return
        val apiLevels = mutableSetOf<Int>()
        platformsDir.listFiles()?.forEach { f ->
            if (!f.isDirectory) return@forEach
            val name = f.name
            if (name.startsWith("android-")) {
                val rest = name.removePrefix("android-")
                val api = rest.takeWhile { it.isDigit() }.toIntOrNull()
                if (api != null && api in 21..36) apiLevels.add(api)
            }
        }
        for (api in apiLevels) {
            normalizeSdkPlatform(platformsDir, api)
        }
    }

    fun normalizeSdkPlatform(platformsDir: File, apiLevel: Int = ANDROID_PLATFORM_API_DEFAULT) {
        if (!platformsDir.exists()) return
        val targetPlatformDir = File(platformsDir, "android-$apiLevel")

        // 1. Resolve diverted directories created by AGP or extraction (e.g. android-34-2, android-34-3, android-34-ext7, android-34-1)
        val altDirs = platformsDir.listFiles { f ->
            f.isDirectory && f != targetPlatformDir && (f.name.startsWith("android-$apiLevel-") || f.name.contains("android-$apiLevel"))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val targetJar = File(targetPlatformDir, "android.jar")
        val validAlt = if (!targetJar.exists() || targetJar.length() < 100_000L) {
            altDirs.firstOrNull { File(it, "android.jar").exists() && File(it, "android.jar").length() > 1000L }
        } else null
        if (validAlt != null) {
            // A newer/diverted complete download exists (e.g. android-34-3 from AGP). Replace targetPlatformDir with it.
            try {
                if (targetPlatformDir.exists()) {
                    targetPlatformDir.deleteRecursively()
                }
                val renamed = validAlt.renameTo(targetPlatformDir)
                if (!renamed) {
                    validAlt.copyRecursively(targetPlatformDir, overwrite = true)
                    validAlt.deleteRecursively()
                }
            } catch (_: Throwable) {
                try {
                    validAlt.copyRecursively(targetPlatformDir, overwrite = true)
                    validAlt.deleteRecursively()
                } catch (_: Throwable) {}
            }
        }

        if (targetPlatformDir.exists()) {
            normalizePlatformMetadataForAgp(targetPlatformDir, apiLevel)
            val androidJar = File(targetPlatformDir, "android.jar")
            try { androidJar.setReadable(true, false) } catch (_: Throwable) {}
        }

        // sdkmanager creates suffixed directories when a stale canonical directory is
        // present. Once the canonical package is valid, remove those duplicate installs
        // so the next Gradle invocation cannot select android-XX-2 again.
        altDirs.forEach { dir ->
            if (dir.exists() && dir != targetPlatformDir) {
                try { dir.deleteRecursively() } catch (_: Throwable) {}
            }
        }
    }

    /**
     * sdkmanager uses a -2/-3 suffix when a stale or incomplete package directory exists.
     * Collapse only valid duplicate Build-Tools directories before Gradle starts.
     */
    fun normalizeAllSdkBuildTools(buildToolsDir: File) {
        if (!buildToolsDir.exists()) return
        val suffixed = Regex("""^(\d+\.\d+\.\d+)-(\d+)$""")
        val versions = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { suffixed.matchEntire(it.name)?.groupValues?.get(1) }
            ?.toSet()
            .orEmpty()

        for (version in versions) {
            val canonical = File(buildToolsDir, version)
            val duplicates = buildToolsDir.listFiles()
                ?.filter { it.isDirectory && suffixed.matchEntire(it.name)?.groupValues?.get(1) == version }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            if (!isBuildToolsDirectoryReady(canonical)) {
                val valid = duplicates.firstOrNull { isBuildToolsDirectoryReady(it) }
                if (valid != null) {
                    try { canonical.deleteRecursively() } catch (_: Throwable) {}
                    if (!valid.renameTo(canonical)) {
                        try {
                            valid.copyRecursively(canonical, overwrite = true)
                            valid.deleteRecursively()
                        } catch (_: Throwable) {}
                    }
                }
            }
            if (isBuildToolsDirectoryReady(canonical)) {
                duplicates.forEach { duplicate ->
                    if (duplicate.exists()) {
                        try { duplicate.deleteRecursively() } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    private fun isBuildToolsDirectoryReady(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory) return false
        val hasMetadata = File(directory, "source.properties").exists()
        val hasTool = listOf("aapt2", "aapt", "d8", "d8.jar", "zipalign")
            .any { File(directory, it).exists() }
        return hasMetadata && hasTool
    }

    fun describeBuildTools(sdkDir: File, version: String): String {
        normalizeAllSdkBuildTools(File(sdkDir, "build-tools"))
        val dir = File(sdkDir, "build-tools/$version")
        val files = if (dir.exists()) {
            dir.listFiles()?.filter { it.isFile }?.joinToString(", ") { "${it.name}=${it.length()}b" }.orEmpty()
        } else "MISSING"
        return "SDK Build-Tools $version: path=${dir.absolutePath} exists=${dir.exists()} ready=${isBuildToolsDirectoryReady(dir)} files=[$files]"
    }

    fun describeInstalledBuildTools(sdkDir: File): String {
        val buildToolsDir = File(sdkDir, "build-tools")
        normalizeAllSdkBuildTools(buildToolsDir)
        val installed = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.joinToString(", ") { dir ->
                "${dir.name}(ready=${isBuildToolsDirectoryReady(dir)},size=${dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }})"
            }
            .orEmpty()
            .ifBlank { "(none)" }
        return "SDK Build-Tools installed: $installed"
    }

    /**
     * AGP versions used by older user projects do not understand SDK extension targets.
     * Android 34 is distributed by Google as an extension package (ext7), although its
     * target hash is still android-34. Keep the actual android.jar, but expose compatible
     * base-platform metadata so DefaultSdkLoader resolves the canonical target.
     */
    private fun normalizePlatformMetadataForAgp(platformDir: File, apiLevel: Int) {
        val sourceProperties = File(platformDir, "source.properties")
        if (sourceProperties.exists()) {
            try {
                val lines = sourceProperties.readLines()
                    .filterNot {
                        val key = it.substringBefore('=', "").trim()
                        key.equals("AndroidVersion.ExtensionLevel", ignoreCase = true) ||
                                key.equals("AndroidVersion.IsBaseSdk", ignoreCase = true) ||
                                key.equals("ExtensionLevel", ignoreCase = true)
                    }
                    .toMutableList()
                fun upsert(key: String, value: String) {
                    val index = lines.indexOfFirst { it.trim().startsWith("$key=") }
                    if (index >= 0) lines[index] = "$key=$value" else lines.add("$key=$value")
                }
                upsert("Pkg.Desc", "Android SDK Platform $apiLevel")
                upsert("AndroidVersion.ApiLevel", apiLevel.toString())
                sourceProperties.writeText(lines.joinToString("\n") + "\n")
            } catch (_: Throwable) {}
        }

        // package.xml is used by sdkmanager to decide whether the canonical package is
        // already installed. Remove only extension fields; preserve licenses and revision.
        val packageXml = File(platformDir, "package.xml")
        if (packageXml.exists()) {
            try {
                var xml = packageXml.readText()
                xml = xml.replace(Regex("<extension-level>.*?</extension-level>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
                    .replace(Regex("<base-extension>.*?</base-extension>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
                packageXml.writeText(xml)
            } catch (_: Throwable) {}
        }
    }

    /**
     * Deletes a broken canonical platform dir (no usable android.jar) and every diverted
     * android-XX-N sibling. sdkmanager otherwise refuses to install into android-XX
     * ("already exists") and writes android-XX-2, which DefaultSdkLoader cannot resolve.
     */
    fun purgeBrokenPlatform(platformsDir: File, apiLevel: Int) {
        if (!platformsDir.exists()) return
        val target = File(platformsDir, "android-$apiLevel")
        val jar = File(target, "android.jar")
        if (target.exists() && (!jar.exists() || jar.length() < 100_000L)) {
            try { target.deleteRecursively() } catch (_: Throwable) {}
        }
        platformsDir.listFiles()?.forEach { f ->
            if (f.isDirectory && f != target && f.name.startsWith("android-$apiLevel-")) {
                try { f.deleteRecursively() } catch (_: Throwable) {}
            }
        }
    }

    fun isPlatformReady(sdkDir: File, apiLevel: Int): Boolean {
        val platformsDir = File(sdkDir, "platforms")
        normalizeSdkPlatform(platformsDir, apiLevel)
        val jar = File(platformsDir, "android-$apiLevel/android.jar")
        val props = File(platformsDir, "android-$apiLevel/source.properties")
        if (!jar.exists() || jar.length() < 100_000L || !props.exists()) return false
        val text = try { props.readText() } catch (_: Throwable) { return false }
        val declaredApi = Regex("""(?m)^AndroidVersion\.ApiLevel\s*=\s*(\d+)\s*$""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (declaredApi != null && declaredApi != apiLevel) return false
        if (text.contains("ExtensionLevel", ignoreCase = true)) return false
        return true
    }

    fun describePlatform(sdkDir: File, apiLevel: Int): String {
        val platforms = File(sdkDir, "platforms")
        val target = File(platforms, "android-$apiLevel")
        val sb = StringBuilder()
        sb.appendLine("SDK dir: ${sdkDir.absolutePath} exists=${sdkDir.exists()}")
        val names = platforms.list()?.sorted()?.joinToString(", ").orEmpty().ifBlank { "(empty)" }
        sb.appendLine("platforms/: $names")
        if (!target.exists()) {
            sb.appendLine("android-$apiLevel: MISSING")
            return sb.toString().trimEnd()
        }
        target.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val kind = if (f.isDirectory) "dir" else "${f.length()}b"
            sb.appendLine("  ${f.name} [$kind]")
        }
        val props = File(target, "source.properties")
        if (props.exists()) {
            sb.appendLine("--- source.properties ---")
            sb.appendLine(try { props.readText().trim() } catch (t: Throwable) { t.message ?: "unreadable" })
        } else {
            sb.appendLine("source.properties: MISSING")
        }
        sb.appendLine("package.xml exists=${File(target, "package.xml").exists()}")
        return sb.toString().trimEnd()
    }

    fun isAndroidPlatformInstalled(context: Context, apiLevel: Int = ANDROID_PLATFORM_API_DEFAULT): Boolean {
        return isPlatformReady(getAndroidSdkDir(context), apiLevel)
    }

    fun isAndroidBuildToolsInstalled(context: Context, version: String = ANDROID_BUILD_TOOLS_VERSION_DEFAULT): Boolean {
        val sdkDir = findExistingSdk(context)
        normalizeAllSdkBuildTools(File(sdkDir, "build-tools"))
        return isBuildToolsDirectoryReady(File(sdkDir, "build-tools/$version"))
    }

    suspend fun installAndroidPlatform(
        context: Context,
        apiLevel: Int = ANDROID_PLATFORM_API_DEFAULT,
        onProgress: (statusMessage: String, percent: Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val sdkDir = ensureAndroidSdk(context)
        val platformsDir = File(sdkDir, "platforms").also { it.mkdirs() }
        val targetPlatformDir = File(platformsDir, "android-$apiLevel")

        val tempArchive = File(context.cacheDir, "platform-${apiLevel}_r03.zip")

        try {
            onProgress(
                if (isRu) "Загрузка Android SDK Platform $apiLevel (~58 МБ)..."
                else "Downloading Android SDK Platform $apiLevel (~58 MB)...",
                5f
            )

            var downloadSuccess = false
            val hosts = listOf(
                "https://dl.google.com/android/repository/",
                "https://mirrors.cloud.tencent.com/android/repository/",
                "https://mirrors.aliyun.com/android/repository/"
            )
            val archives = listOf(
                "platform-${apiLevel}-ext7_r03.zip",
                "platform-${apiLevel}-ext7_r02.zip",
                "platform-${apiLevel}_r03.zip",
                "platform-${apiLevel}_r02.zip",
                "platform-${apiLevel}_r01.zip"
            )
            val urls = hosts.flatMap { host -> archives.map { host + it } }
            var lastDownloadError = "unknown"
            var downloadedUrl = ""

            for (url in urls) {
                try {
                    onProgress(
                        if (isRu) "Попытка загрузки: $url"
                        else "Attempting download: $url",
                        3f
                    )
                    downloader.download(url, tempArchive) { current, total, percent, _ ->
                        val scaled = 5f + (percent * 0.75f)
                        onProgress(
                            if (isRu) "Загрузка Android SDK Platform $apiLevel: ${(current / (1024 * 1024))} МБ / ${(total / (1024 * 1024))} МБ (${percent.toInt()}%)"
                            else "Downloading Android SDK Platform $apiLevel: ${(current / (1024 * 1024))} MB / ${(total / (1024 * 1024))} MB (${percent.toInt()}%)",
                            scaled
                        )
                    }
                    if (tempArchive.exists() && tempArchive.length() > 1_000_000L) {
                        downloadSuccess = true
                        downloadedUrl = url
                        onProgress(
                            if (isRu) "✔ Успешно загружено ${tempArchive.length() / (1024 * 1024)} МБ из $url"
                            else "✔ Successfully downloaded ${tempArchive.length() / (1024 * 1024)} MB from $url",
                            80f
                        )
                        break
                    }
                    lastDownloadError = "archive too small (${tempArchive.length()} bytes) from $url"
                    tempArchive.delete()
                } catch (e: Exception) {
                    lastDownloadError = "${e.message ?: e.javaClass.simpleName} ($url)"
                    tempArchive.delete()
                }
            }

            if (!downloadSuccess) {
                onProgress(
                    if (isRu) "✖ Ошибка: Не удалось загрузить архив Android Platform: $lastDownloadError"
                    else "✖ Error: Failed to download Android Platform archive: $lastDownloadError",
                    0f
                )
                return@withContext false
            }

            onProgress(
                if (isRu) "Распаковка Android SDK Platform $apiLevel..."
                else "Extracting Android SDK Platform $apiLevel...",
                85f
            )

            val extractSuccess = extractor.extract(tempArchive, platformsDir) { msg ->
                if (!msg.startsWith("Распаковка:") && !msg.startsWith("Unpacking:")) {
                    onProgress(msg, 90f)
                }
            }
            tempArchive.delete()

            if (!extractSuccess) return@withContext false

            // Normalize folder name if extracted as android-34, android-UpsideDownCake or similar
            if (!targetPlatformDir.exists() || !File(targetPlatformDir, "android.jar").exists()) {
                val candidate = platformsDir.listFiles { f ->
                    f.isDirectory && f != targetPlatformDir && File(f, "android.jar").exists()
                }?.maxByOrNull { it.lastModified() }
                    ?: platformsDir.listFiles { f ->
                        f.isDirectory && f != targetPlatformDir && (f.name.contains("android", ignoreCase = true) || f.name.contains("$apiLevel"))
                    }?.maxByOrNull { it.lastModified() }
                if (candidate != null) {
                    if (targetPlatformDir.exists()) targetPlatformDir.deleteRecursively()
                    candidate.renameTo(targetPlatformDir)
                }
            }

            if (!targetPlatformDir.exists() || File(targetPlatformDir, "android.jar").length() < 100_000L) {
                val nested = platformsDir.walkTopDown().maxDepth(4).filter {
                    it.isFile && it.name == "android.jar" && it.length() > 100_000L
                }.maxByOrNull { it.length() }
                val parent = nested?.parentFile
                if (parent != null && parent != targetPlatformDir) {
                    if (targetPlatformDir.exists()) targetPlatformDir.deleteRecursively()
                    val renamed = parent.renameTo(targetPlatformDir)
                    if (!renamed) {
                        parent.copyRecursively(targetPlatformDir, overwrite = true)
                        parent.deleteRecursively()
                    }
                }
            }

            normalizeSdkPlatform(platformsDir, apiLevel)

            val installedJar = File(targetPlatformDir, "android.jar")
            val success = installedJar.exists() && installedJar.length() > 0L
            if (success) {
                onProgress(
                    if (isRu) "✔ Android SDK Platform $apiLevel успешно установлена!"
                    else "✔ Android SDK Platform $apiLevel installed successfully!",
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

    suspend fun installBuildTools(
        context: Context,
        version: String = ANDROID_BUILD_TOOLS_VERSION_DEFAULT,
        onProgress: (statusMessage: String, percent: Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val sdkDir = ensureAndroidSdk(context)
        val buildToolsDir = File(sdkDir, "build-tools").also { it.mkdirs() }
        val targetVersionDir = File(buildToolsDir, version)

        val archiveVersion = if (version == "34.0.0") "34" else version
        val archiveName = "build-tools_r${archiveVersion}-linux.zip"
        val tempArchive = File(context.cacheDir, archiveName)

        try {
            onProgress(
                if (isRu) "Загрузка Android Build-Tools $version (~55 МБ)..."
                else "Downloading Android Build-Tools $version (~55 MB)...",
                5f
            )

            var downloadSuccess = false
            val urls = listOf(
                "https://dl.google.com/android/repository/$archiveName",
                "https://mirrors.cloud.tencent.com/android/repository/$archiveName",
                "https://mirrors.aliyun.com/android/repository/$archiveName"
            )

            for (url in urls) {
                try {
                    downloader.download(url, tempArchive) { current, total, percent, _ ->
                        val scaled = 5f + (percent * 0.75f)
                        onProgress(
                            if (isRu) "Загрузка Android Build-Tools: ${(current / (1024 * 1024))} МБ / ${(total / (1024 * 1024))} МБ (${percent.toInt()}%)"
                            else "Downloading Android Build-Tools: ${(current / (1024 * 1024))} MB / ${(total / (1024 * 1024))} MB (${percent.toInt()}%)",
                            scaled
                        )
                    }
                    if (tempArchive.exists() && tempArchive.length() > 1_000_000L) {
                        downloadSuccess = true
                        onProgress(
                            if (isRu) "✔ Успешно загружено ${tempArchive.length() / (1024 * 1024)} МБ из $url"
                            else "✔ Successfully downloaded ${tempArchive.length() / (1024 * 1024)} MB from $url",
                            80f
                        )
                        break
                    }
                    tempArchive.delete()
                } catch (e: Exception) {
                    tempArchive.delete()
                }
            }

            if (!downloadSuccess) {
                onProgress(
                    if (isRu) "✖ Ошибка: Не удалось загрузить архив Build-Tools"
                    else "✖ Error: Failed to download Build-Tools archive",
                    0f
                )
                return@withContext false
            }

            onProgress(
                if (isRu) "Распаковка Android Build-Tools..."
                else "Extracting Android Build-Tools...",
                85f
            )

            val extractSuccess = extractor.extract(tempArchive, buildToolsDir) { msg ->
                if (!msg.startsWith("Распаковка:") && !msg.startsWith("Unpacking:")) {
                    onProgress(msg, 90f)
                }
            }
            tempArchive.delete()

            if (!extractSuccess) return@withContext false

            // If extracted with name 'android-14', rename to '34.0.0'
            if (!targetVersionDir.exists()) {
                val candidate = buildToolsDir.listFiles { f -> f.isDirectory && f.name == "android-14" }?.firstOrNull()
                    ?: buildToolsDir.listFiles { f -> f.isDirectory && f.name != version }?.firstOrNull()
                if (candidate != null) {
                    candidate.renameTo(targetVersionDir)
                }
            }

            // Set executable permissions
            targetVersionDir.walkTopDown().filter { it.isFile }.forEach {
                try { it.setReadable(true, false) } catch (_: Throwable) {}
                if (it.extension.isEmpty() || it.extension in setOf("so", "sh")) {
                    try { it.setExecutable(true, false) } catch (_: Throwable) {}
                }
            }

            val prop = File(targetVersionDir, "source.properties")
            val success = prop.exists() || File(targetVersionDir, "lib").exists()
            if (success) {
                onProgress(
                    if (isRu) "✔ Android Build-Tools $version успешно установлены!"
                    else "✔ Android Build-Tools $version installed successfully!",
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

    // ==================== Standalone OpenJDK Management ====================

    fun getJdkDir(context: Context): File {
        return File(getToolsDir(context), "jdk")
    }

    fun ensureJdkRuntimeLibraries(context: Context, jdkDir: File = getJdkDir(context)) {
        val libDir = File(jdkDir, "lib")
        if (!libDir.exists()) libDir.mkdirs()

        // Version marker to force re-extraction if APK assets are updated
        val versionMarker = File(libDir, ".prism_libs_version")
        val currentLibsVersion = "v2-libcxx"
        val needUpdate = !versionMarker.exists() || versionMarker.readText().trim() != currentLibsVersion

        // 1. Copy bundled native libraries from app assets (arm64-v8a)
        try {
            val assetManager = context.assets
            val assetFiles = assetManager.list("jdk_libs/arm64-v8a")
            if (assetFiles != null && assetFiles.isNotEmpty()) {
                for (name in assetFiles) {
                    val destFile = File(libDir, name)
                    if (needUpdate || !destFile.exists() || destFile.length() == 0L) {
                        try {
                            assetManager.open("jdk_libs/arm64-v8a/$name").use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            try { destFile.setExecutable(true, false) } catch (_: Throwable) {}
                            try { destFile.setReadable(true, false) } catch (_: Throwable) {}
                        } catch (_: Throwable) {}
                    }
                }
            }
            try { versionMarker.writeText(currentLibsVersion) } catch (_: Throwable) {}
        } catch (_: Throwable) {}

        // 2. Ensure libz.so.1 and libz.so exist
        val libz1 = File(libDir, "libz.so.1")
        val libz = File(libDir, "libz.so")
        val libzReal = File(libDir, "libz.so.1.3.2")
        if (!libz1.exists() || libz1.length() == 0L) {
            if (libzReal.exists()) {
                try { libzReal.copyTo(libz1, overwrite = true) } catch (_: Throwable) {}
                try { libzReal.copyTo(libz, overwrite = true) } catch (_: Throwable) {}
            } else {
                // Fallback to system libz.so
                val systemLibzCandidates = listOf(
                    File("/system/lib64/libz.so"),
                    File("/apex/com.android.runtime/lib64/bionic/libz.so"),
                    File("/system/lib/libz.so")
                )
                val systemLibz = systemLibzCandidates.firstOrNull { it.exists() }
                if (systemLibz != null) {
                    try { systemLibz.copyTo(libz1, overwrite = true) } catch (_: Throwable) {}
                    try { systemLibz.copyTo(libz, overwrite = true) } catch (_: Throwable) {}
                }
            }
        }

        // 3. Set executable & readable permissions on all native libraries
        libDir.walkTopDown().filter { it.isFile && (it.extension == "so" || it.name.contains(".so.")) }.forEach { f ->
            try { f.setExecutable(true, false) } catch (_: Throwable) {}
            try { f.setReadable(true, false) } catch (_: Throwable) {}
        }

        // 4. Ensure all binaries in bin/ are executable
        val binDir = File(jdkDir, "bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    try { f.setExecutable(true, false) } catch (_: Throwable) {}
                    try { f.setReadable(true, false) } catch (_: Throwable) {}
                }
            }
        }
    }

    fun isJdkInstalled(context: Context): Boolean {
        val exe = getJdkExecutable(context) ?: return false
        val jdkDir = getJdkDir(context)
        ensureJdkRuntimeLibraries(context, jdkDir)
        val libz1 = File(jdkDir, "lib/libz.so.1")
        val libcxx = File(jdkDir, "lib/libc++_shared.so")
        val tagFile = File(jdkDir, ".prism_jdk_tag")
        val isTagged = tagFile.exists() && (tagFile.readText().trim() == JDK_BUILD_TAG || tagFile.readText().trim().startsWith("17.0.20-termux-deb"))
        if (exe.exists() && libz1.exists() && libcxx.exists()) {
            if (!tagFile.exists() || tagFile.readText().trim() != JDK_BUILD_TAG) {
                try { tagFile.writeText(JDK_BUILD_TAG) } catch (_: Throwable) {}
            }
            return true
        }
        return isTagged
    }

    fun hasAnyJdkInstalled(context: Context): Boolean {
        return getJdkExecutable(context) != null
    }

    fun getJdkExecutable(context: Context): File? {
        val jdkDir = getJdkDir(context)
        if (!jdkDir.exists()) return null

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val targetName = if (isWindows) "java.exe" else "java"

        val javaExe = jdkDir.walkTopDown().firstOrNull { file ->
            file.isFile && file.name == targetName && file.parentFile?.name == "bin"
        } ?: jdkDir.walkTopDown().firstOrNull { file ->
            file.isFile && file.name == "java" && file.parentFile?.name == "bin"
        }

        javaExe?.let {
            try { it.setExecutable(true, false) } catch (_: Throwable) {}
        }
        return javaExe
    }

    fun getJdkHomeDir(context: Context): File? {
        val exe = getJdkExecutable(context) ?: return null
        return exe.parentFile?.parentFile ?: getJdkDir(context)
    }

    suspend fun installJdk(
        context: Context,
        customUrl: String? = null,
        onProgress: (statusMessage: String, percent: Float) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val jdkTargetDir = getJdkDir(context)

        // Clear existing outdated or unpatched JDK before fresh installation
        try {
            jdkTargetDir.deleteRecursively()
        } catch (_: Throwable) {}
        jdkTargetDir.mkdirs()

        val tempArchive = File(context.cacheDir, "openjdk-17-aarch64.deb")
        if (tempArchive.exists()) {
            tempArchive.delete()
        }

        try {
            onProgress(
                if (isRu) "Подготовка к загрузке официального OpenJDK 17 LTS (Termux ARM64)..."
                else "Preparing to download official OpenJDK 17 LTS (Termux ARM64)...",
                5f
            )

            val urls = mutableListOf<String>()
            if (!customUrl.isNullOrBlank()) {
                urls.add(customUrl.trim())
            }
            urls.add(JDK_DEB_URL_PRIMARY)
            urls.add(JDK_DEB_URL_TSINGHUA)
            urls.add(JDK_DEB_URL_BFSU)
            urls.add(JDK_DEB_URL_GRIMLER)
            urls.add(JDK_TAR_XZ_FALLBACK)
            urls.add(JDK_TAR_XZ_CDN)

            var downloadSuccess = false
            var lastErrorMessage: String? = null

            for (url in urls) {
                try {
                    val archiveToSave = if (url.endsWith(".tar.xz")) {
                        File(context.cacheDir, "openjdk-17-aarch64.tar.xz")
                    } else {
                        tempArchive
                    }
                    if (archiveToSave.exists()) archiveToSave.delete()

                    onProgress(
                        if (isRu) "Подключение к источнику загрузки..."
                        else "Connecting to download server...",
                        10f
                    )
                    downloader.download(url, archiveToSave) { current, total, percent, speedBytesPerSec ->
                        val scaled = 10f + (percent * 0.65f) // 10% to 75%
                        val curMb = current / (1024 * 1024)
                        val totalMb = if (total > 0) total / (1024 * 1024) else 96
                        val speedMb = String.format(java.util.Locale.US, "%.1f", speedBytesPerSec.toFloat() / (1024 * 1024))
                        onProgress(
                            if (isRu) "Загрузка OpenJDK 17: $curMb / $totalMb МБ (${percent.toInt()}%) — $speedMb МБ/с"
                            else "Downloading OpenJDK 17: $curMb / $totalMb MB (${percent.toInt()}%) — $speedMb MB/s",
                            scaled
                        )
                    }
                    if (archiveToSave.exists() && archiveToSave.length() > 10 * 1024 * 1024L) {
                        downloadSuccess = true
                        if (archiveToSave != tempArchive) {
                            try { archiveToSave.renameTo(tempArchive) } catch (_: Throwable) {}
                        }
                        break
                    } else {
                        archiveToSave.delete()
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message
                    tempArchive.delete()
                }
            }

            if (!downloadSuccess) {
                onProgress(
                    if (isRu) "✖ Ошибка: Не удалось загрузить OpenJDK 17: ${lastErrorMessage ?: "ошибка сети"}. Проверьте подключение."
                    else "✖ Error: Failed to download OpenJDK 17: ${lastErrorMessage ?: "network error"}. Check connection.",
                    0f
                )
                return@withContext false
            }

            onProgress(
                if (isRu) "Распаковка OpenJDK 17 во внутреннее хранилище PrismDE..."
                else "Extracting OpenJDK 17 into PrismDE internal storage...",
                78f
            )

            val extractSuccess = extractor.extract(tempArchive, jdkTargetDir) { msg ->
                if (!msg.startsWith("Распаковка:") && !msg.startsWith("Unpacking:")) {
                    onProgress(msg, 85f)
                }
            }

            tempArchive.delete()

            if (!extractSuccess) {
                onProgress(
                    if (isRu) "✖ Ошибка при распаковке архива OpenJDK 17."
                    else "✖ Error extracting OpenJDK 17 archive.",
                    0f
                )
                return@withContext false
            }

            // Ensure executable permissions on all tools in bin/ and shared libraries in lib/
            onProgress(
                if (isRu) "Настройка прав доступа исполняемых файлов..."
                else "Configuring binary permissions...",
                95f
            )
            jdkTargetDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    if (file.parentFile?.name == "bin" || file.extension == "so" || file.name == "java") {
                        try { file.setExecutable(true, false) } catch (_: Throwable) {}
                        try { file.setReadable(true, false) } catch (_: Throwable) {}
                    }
                }
            }

            // Ensure essential runtime dependencies (libz.so.1, libandroid-shmem, libandroid-spawn, libiconv)
            ensureJdkRuntimeLibraries(context, jdkTargetDir)

            val installedJava = getJdkExecutable(context)
            val success = installedJava != null && installedJava.exists()
            if (success) {
                try {
                    File(jdkTargetDir, ".prism_jdk_tag").writeText(JDK_BUILD_TAG)
                } catch (_: Throwable) {}
                onProgress(
                    if (isRu) "✔ OpenJDK 17 (Termux LTS) успешно установлен во внутреннее хранилище!"
                    else "✔ OpenJDK 17 (Termux LTS) installed successfully into internal storage!",
                    100f
                )
            } else {
                onProgress(
                    if (isRu) "⚠ Файлы извлечены, но бинарный файл java не обнаружен."
                    else "⚠ Files extracted, but java binary was not found.",
                    0f
                )
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            tempArchive.delete()
            onProgress(
                if (isRu) "✖ Исключение при установке: ${e.message}"
                else "✖ Installation exception: ${e.message}",
                0f
            )
            false
        }
    }

    // ==================== Java Environment Detection ====================

    fun detectJavaEnvironment(
        context: Context? = null,
        workingDir: File? = null,
        customJavaHome: String? = null
    ): JavaEnvironmentInfo {
        val isRu = java.util.Locale.getDefault().language == "ru"

        // 1. Check user-configured custom JAVA_HOME first if provided
        if (!customJavaHome.isNullOrBlank()) {
            val dir = File(customJavaHome.trim())
            if (dir.exists()) {
                val bin = File(dir, "bin/java")
                val altBin = if (dir.isFile && dir.name == "java") dir else null
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = if (dir.isDirectory) dir else dir.parentFile?.parentFile,
                    javaBin = if (bin.exists()) bin else altBin,
                    sourceDescription = if (isRu) "Пользовательский ($customJavaHome)" else "Custom ($customJavaHome)"
                )
            }
        }

        // 2. Check internal tools/jdk inside PrismDE (completely autonomous, zero external dependencies!)
        if (context != null) {
            val internalJava = getJdkExecutable(context)
            val internalHome = getJdkHomeDir(context)
            if (internalJava != null && internalJava.exists()) {
                ensureJdkRuntimeLibraries(context)
                val tagFile = File(getJdkDir(context), ".prism_jdk_tag")
                if (!tagFile.exists() || tagFile.readText().trim() != JDK_BUILD_TAG) {
                    try { tagFile.writeText(JDK_BUILD_TAG) } catch (_: Throwable) {}
                }
                return JavaEnvironmentInfo(
                    isAvailable = true,
                    javaHome = internalHome,
                    javaBin = internalJava,
                    sourceDescription = if (isRu) "Встроенный PrismDE OpenJDK 17 (Termux LTS)" else "Internal PrismDE OpenJDK 17 (Termux LTS)"
                )
            }
        }

        // 3. Check local.properties in project
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
        val isInternalJdk = isJdkInstalled(context)

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
                id = "gradle",
                name = "Gradle Build Tool",
                version = GRADLE_VERSION,
                isInstalled = isGradleInstalled(context),
                installedPath = getGradleExecutable(context)?.absolutePath,
                description = if (isRu)
                    "Система сборки для проектов Android и Java/Kotlin"
                else
                    "Build system for Android and Java/Kotlin projects",
                sizeLabel = "~125 MB"
            ),
            BuildToolInfo(
                id = "jdk",
                name = "Java Development Kit (JDK)",
                version = "17",
                isInstalled = javaInfo.isAvailable,
                installedPath = javaInfo.javaHome?.absolutePath ?: javaInfo.javaBin?.absolutePath,
                description = if (isRu)
                    if (isInternalJdk) "Встроенный автономный OpenJDK 17 внутри PrismDE"
                    else "Среда выполнения и компилятор Java, необходимый для Maven и Gradle"
                else
                    if (isInternalJdk) "Internal standalone OpenJDK 17 inside PrismDE"
                    else "Java Runtime and Compiler required for Maven and Gradle builds",
                sizeLabel = javaInfo.sourceDescription
            ),
            BuildToolInfo(
                id = "android_platform",
                name = "Android SDK Platform 34",
                version = "API 34",
                isInstalled = isAndroidPlatformInstalled(context, 34),
                installedPath = File(findExistingSdk(context), "platforms/android-34").takeIf { it.exists() }?.absolutePath,
                description = if (isRu)
                    "Базовая библиотека Android (android.jar) для компиляции приложений"
                else
                    "Android Platform library (android.jar) required to compile Android apps",
                sizeLabel = "~58 MB"
            ),
            BuildToolInfo(
                id = "android_build_tools",
                name = "Android SDK Build-Tools",
                version = ANDROID_BUILD_TOOLS_VERSION_DEFAULT,
                isInstalled = isAndroidBuildToolsInstalled(context, ANDROID_BUILD_TOOLS_VERSION_DEFAULT),
                installedPath = File(findExistingSdk(context), "build-tools/$ANDROID_BUILD_TOOLS_VERSION_DEFAULT").takeIf { it.exists() }?.absolutePath,
                description = if (isRu)
                    "Компоненты сборщика Android: d8 (DEX), AAPT2, apksigner, zipalign"
                else
                    "Android build components: d8 (DEX compiler), AAPT2, apksigner, zipalign",
                sizeLabel = "~55 MB"
            )
        )
    }
}
