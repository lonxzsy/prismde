package com.prismde.feature_build.engine

import java.io.File

data class ProjectToolchainRequirements(
    val compileSdk: Int = 34,
    val buildToolsVersion: String? = null,
    val agpVersion: String? = null,
    val gradleVersion: String? = null,
    val jvmToolchainVersion: Int? = null,
    val sourceCompatibility: Int? = null,
    val targetCompatibility: Int? = null,
    val kotlinJvmTarget: Int? = null,
    val isAndroidProject: Boolean = false,
    val isMavenProject: Boolean = false
)

/**
 * Smart project requirements detector:
 * Parses build.gradle, build.gradle.kts, libs.versions.toml, gradle-wrapper.properties,
 * and pom.xml to accurately extract compileSdk, buildToolsVersion, AGP version,
 * and required Java bytecode/toolchain levels.
 */
object ProjectRequirementsDetector {

    fun detect(projectRootDir: File): ProjectToolchainRequirements {
        val pomFile = File(projectRootDir, "pom.xml")
        if (pomFile.exists()) {
            return parsePomXml(pomFile)
        }

        var detectedCompileSdk: Int? = null
        var detectedBuildTools: String? = null
        var detectedAgp: String? = null
        var detectedToolchain: Int? = null
        var detectedSourceCompat: Int? = null
        var detectedTargetCompat: Int? = null
        var detectedKotlinTarget: Int? = null
        var isAndroid = false

        // 1. Gradle Wrapper version
        val gradleVersion = parseGradleWrapperVersion(projectRootDir)

        // 2. Version Catalog (libs.versions.toml)
        val catalogFile = File(projectRootDir, "gradle/libs.versions.toml")
        if (catalogFile.exists()) {
            try {
                val catalogText = catalogFile.readText()
                detectedAgp = findTomlVersion(catalogText, listOf("agp", "androidGradlePlugin", "android-gradle-plugin"))
                detectedBuildTools = findTomlVersion(catalogText, listOf("buildTools", "build-tools", "buildToolsVersion"))
                val tomlCompileSdk = findTomlVersion(catalogText, listOf("compileSdk", "compileSdkVersion"))?.toIntOrNull()
                if (tomlCompileSdk != null && tomlCompileSdk in 21..36) {
                    detectedCompileSdk = tomlCompileSdk
                }
            } catch (_: Throwable) {}
        }

        // 3. Scan all *.gradle and *.gradle.kts
        val buildFiles = mutableListOf<File>()
        projectRootDir.walkTopDown().maxDepth(3).forEach { file ->
            if (file.isFile && (file.name == "build.gradle" || file.name == "build.gradle.kts")) {
                buildFiles.add(file)
            }
        }

        for (bf in buildFiles) {
            try {
                val content = bf.readText()
                if (content.contains("com.android.") || content.contains("android {")) {
                    isAndroid = true
                }

                // compileSdk / compileSdkVersion
                if (detectedCompileSdk == null) {
                    val sdkMatch = Regex("""(?:compileSdk(?:Version)?)\s*(?:=|\s)\s*['"]?(\d+)['"]?""").find(content)
                    sdkMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (it in 21..36) detectedCompileSdk = it
                    }
                }

                // buildToolsVersion
                if (detectedBuildTools == null) {
                    val btMatch = Regex("""buildToolsVersion\s*(?:=|\s)\s*['"]([^'"]+)['"]""").find(content)
                    btMatch?.groupValues?.get(1)?.trim()?.let {
                        if (Regex("""\d+\.\d+\.\d+""").matches(it)) detectedBuildTools = it
                    }
                }

                // AGP Version
                if (detectedAgp == null) {
                    val agpMatch = Regex("""(?:id\s*\(?['"]com\.android\.(?:application|library)['"]\)?\s*version\s*['"]([^'"]+)['"]|com\.android\.tools\.build:gradle:([^'"]+)['"])""")
                        .find(content)
                    val v = agpMatch?.groupValues?.let { it.getOrNull(1)?.ifBlank { null } ?: it.getOrNull(2) }
                    if (!v.isNullOrBlank()) detectedAgp = v
                }

                // jvmToolchain
                if (detectedToolchain == null) {
                    val toolchainMatch = Regex("""jvmToolchain\s*(?:\(\s*(\d+)\s*\)|\{\s*.*JavaLanguageVersion\.of\((\d+)\))""").find(content)
                    val tc = toolchainMatch?.groupValues?.let { it.getOrNull(1)?.ifBlank { null } ?: it.getOrNull(2) }?.toIntOrNull()
                    if (tc != null) detectedToolchain = tc
                }

                // sourceCompatibility / targetCompatibility
                if (detectedSourceCompat == null) {
                    detectedSourceCompat = extractJavaVersion(content, "sourceCompatibility")
                }
                if (detectedTargetCompat == null) {
                    detectedTargetCompat = extractJavaVersion(content, "targetCompatibility")
                }

                // Kotlin jvmTarget
                if (detectedKotlinTarget == null) {
                    val ktMatch = Regex("""jvmTarget\s*(?:=|\.set\()?\s*['"]?(?:JvmTarget\.JVM_)?(\d+)['"]?\s*\)?""").find(content)
                    ktMatch?.groupValues?.get(1)?.toIntOrNull()?.let { detectedKotlinTarget = it }
                }
            } catch (_: Throwable) {}
        }

        return ProjectToolchainRequirements(
            compileSdk = detectedCompileSdk ?: 34,
            buildToolsVersion = detectedBuildTools,
            agpVersion = detectedAgp,
            gradleVersion = gradleVersion,
            jvmToolchainVersion = detectedToolchain,
            sourceCompatibility = detectedSourceCompat,
            targetCompatibility = detectedTargetCompat,
            kotlinJvmTarget = detectedKotlinTarget,
            isAndroidProject = isAndroid,
            isMavenProject = false
        )
    }

    private fun extractJavaVersion(content: String, keyword: String): Int? {
        val regex = Regex("""$keyword\s*(?:=|\s)\s*(?:JavaVersion\.VERSION_(\d+(?:_\d+)?)|['"]?(\d+(?:\.\d+)?)['"]?)""")
        val match = regex.find(content) ?: return null
        val raw = match.groupValues[1].ifBlank { match.groupValues[2] }
        return when (raw) {
            "1_8", "1.8", "8" -> 8
            "11", "1.11" -> 11
            "17" -> 17
            "21" -> 21
            else -> raw.toIntOrNull()
        }
    }

    private fun parseGradleWrapperVersion(rootDir: File): String? {
        val wrapperProps = File(rootDir, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperProps.exists()) return null
        return try {
            val distUrl = wrapperProps.readLines().firstOrNull { it.trim().startsWith("distributionUrl=") }
            distUrl?.let {
                Regex("""gradle-([0-9.]+)-(all|bin)\.zip""").find(it)?.groupValues?.get(1)
            }
        } catch (_: Throwable) { null }
    }

    private fun findTomlVersion(toml: String, keys: List<String>): String? {
        for (key in keys) {
            val regex = Regex("""(?im)^\s*${Regex.escape(key)}\s*=\s*['"]([^'"]+)['"]""")
            regex.find(toml)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    private fun parsePomXml(pomFile: File): ProjectToolchainRequirements {
        return try {
            val content = pomFile.readText()
            val javaVerRegex = Regex("""<(?:java\.version|maven\.compiler\.source|maven\.compiler\.target|maven\.compiler\.release)>(\d+)<\/[^>]+>""")
            val ver = javaVerRegex.find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 17
            ProjectToolchainRequirements(
                sourceCompatibility = ver,
                targetCompatibility = ver,
                isMavenProject = true
            )
        } catch (_: Throwable) {
            ProjectToolchainRequirements(isMavenProject = true)
        }
    }
}
