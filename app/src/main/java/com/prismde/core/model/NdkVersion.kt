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
        get() = installPath?.let {
            val f1 = File(it, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang")
            if (f1.exists()) f1 else File(it, "bin/clang")
        }

    val clangPlusExecutable: File?
        get() = installPath?.let {
            val f1 = File(it, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang++")
            if (f1.exists()) f1 else File(it, "bin/clang++")
        }

    val cmakeToolchainFile: File?
        get() = installPath?.let {
            val f1 = File(it, "build/cmake/android.toolchain.cmake")
            if (f1.exists()) f1 else null
        }

    val ndkBuildScript: File?
        get() = installPath?.let {
            val f1 = File(it, "ndk-build")
            if (f1.exists()) f1 else null
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
