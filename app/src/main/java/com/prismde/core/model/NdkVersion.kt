package com.prismde.core.model

import java.io.File

data class NdkVersion(
    val versionTag: String,             // e.g. "r26c"
    val displayName: String,            // "Android NDK r26c (LTS)"
    val llvmVersion: String,            // "Clang 17.0.2"
    val downloadUrl: String,            // Direct GitHub Releases URL
    val archiveSizeBytes: Long,
    val sha256Checksum: String? = null,
    val isAvailable: Boolean = true,    // True for active/released versions, false for grayed out ones
    val isInstalled: Boolean = false,
    val installPath: String? = null
) {
    val clangExecutable: File?
        get() = resolveCompilerBinary(isPlusPlus = false)

    val clangPlusExecutable: File?
        get() = resolveCompilerBinary(isPlusPlus = true)

    val cmakeToolchainFile: File?
        get() = getEffectiveNdkDir()?.let { root ->
            val candidates = listOf(
                File(root, "build/cmake/android.toolchain.cmake"),
                File(root, "android-ndk-aide/build/cmake/android.toolchain.cmake")
            )
            candidates.firstOrNull { it.exists() }
        }

    val ndkBuildScript: File?
        get() = getEffectiveNdkDir()?.let { root ->
            val candidates = listOf(
                File(root, "ndk-build"),
                File(root, "build/ndk-build"),
                File(root, "ndk-build-android")
            )
            val found = candidates.firstOrNull { it.exists() }
            found?.setExecutable(true, false)
            found
        }

    fun getEffectiveNdkDir(): File? {
        val path = installPath
        if (path != null) {
            val f = File(path)
            if (f.exists()) {
                val aide = File(f, "android-ndk-aide")
                return if (aide.exists()) aide else f
            }
        }
        val fallbackPaths = listOf(
            "/data/user/0/com.prismde/files/ndk/$versionTag/android-ndk-aide",
            "/data/user/0/com.prismde/files/ndk/$versionTag",
            "/data/data/com.prismde/files/ndk/$versionTag/android-ndk-aide",
            "/data/data/com.prismde/files/ndk/$versionTag"
        )
        for (fb in fallbackPaths) {
            val f = File(fb)
            if (f.exists()) return f
        }
        return null
    }

    private fun resolveCompilerBinary(isPlusPlus: Boolean): File? {
        val rootDir = getEffectiveNdkDir() ?: return null

        val binDirs = listOf(
            File(rootDir, "toolchains/llvm/prebuilt/linux-arm64/bin"),
            File(rootDir, "toolchains/llvm/prebuilt/linux-aarch64/bin"),
            File(rootDir, "toolchains/llvm/prebuilt/linux-x86_64/bin"),
            File(rootDir, "bin")
        )

        for (binDir in binDirs) {
            if (!binDir.exists() || !binDir.isDirectory) continue

            // Ensure companion tools in binDir have executable permissions
            try {
                binDir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.canExecute()) {
                        file.setExecutable(true, false)
                    }
                }
            } catch (_: Throwable) {}

            val primaryTarget = if (isPlusPlus) File(binDir, "clang++") else File(binDir, "clang")
            if (primaryTarget.exists() && primaryTarget.isFile) {
                primaryTarget.setExecutable(true, false)
                return primaryTarget
            }

            // Fallback candidates: clang-21, clang-17, clang
            val fallbackCandidates = listOf(
                File(binDir, "clang-21"),
                File(binDir, "clang-17"),
                File(binDir, "clang"),
                File(binDir, "clang++")
            )
            val existingFallback = fallbackCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 1000L }
            if (existingFallback != null) {
                existingFallback.setExecutable(true, false)
                if (!primaryTarget.exists()) {
                    try {
                        android.system.Os.symlink(existingFallback.name, primaryTarget.absolutePath)
                    } catch (_: Throwable) {
                        try {
                            existingFallback.copyTo(primaryTarget, overwrite = true)
                        } catch (_: Throwable) {}
                    }
                }
                if (primaryTarget.exists()) {
                    primaryTarget.setExecutable(true, false)
                    return primaryTarget
                }
                return existingFallback
            }
        }

        // Recursive search if bin directory structure was relocated
        val found = rootDir.walkTopDown().maxDepth(6).firstOrNull { f ->
            f.isFile && (
                (isPlusPlus && (f.name == "clang++" || f.name.endsWith("-clang++"))) ||
                (!isPlusPlus && (f.name == "clang" || f.name.startsWith("clang-")))
            )
        }
        found?.setExecutable(true, false)
        return found
    }
}

object DefaultNdkCatalog {
    // Exact official release asset uploaded to https://github.com/lonxzsy/prismde-ndk/releases/tag/v1.0.0
    const val DEFAULT_ACTIVE_TAG = "r26c"
    const val R26_ASSET_URL = "https://github.com/lonxzsy/prismde-ndk/releases/download/v1.0.0/ndk-arm64-26.tar.gz"
    const val R26_SIZE_BYTES = 105_975_765L
    const val R26_SHA256 = "bb839e34dcb0ab025e51457ef14a15ae4355feaca55079901ab0ddb020286d21"

    val AVAILABLE_VERSIONS = listOf(
        NdkVersion(
            versionTag = "r26c",
            displayName = "Android NDK r26c (AArch64 - Релиз v1.0.0)",
            llvmVersion = "LLVM / Clang 17",
            downloadUrl = R26_ASSET_URL,
            archiveSizeBytes = R26_SIZE_BYTES,
            sha256Checksum = R26_SHA256,
            isAvailable = true
        ),
        NdkVersion(
            versionTag = "r27",
            displayName = "Android NDK r27 (Latest)",
            llvmVersion = "Clang 18.0.1",
            downloadUrl = "",
            archiveSizeBytes = 430_000_000L,
            isAvailable = false // Grayed out in UI
        ),
        NdkVersion(
            versionTag = "r25c",
            displayName = "Android NDK r25c (LTS)",
            llvmVersion = "Clang 14.0.7",
            downloadUrl = "",
            archiveSizeBytes = 350_000_000L,
            isAvailable = false // Grayed out in UI
        ),
        NdkVersion(
            versionTag = "r23c",
            displayName = "Android NDK r23c (Legacy)",
            llvmVersion = "Clang 12.0.8",
            downloadUrl = "",
            archiveSizeBytes = 310_000_000L,
            isAvailable = false // Grayed out in UI
        )
    )
}
