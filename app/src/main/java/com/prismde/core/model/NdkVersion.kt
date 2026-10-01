package com.prismde.core.model

import java.io.File

data class NdkVersion(
    val versionTag: String,             // e.g. "r26c"
    val displayName: String,            // "Android NDK r26c (LTS)"
    val llvmVersion: String,            // "Clang 17.0.2"
    val downloadUrl: String,            // Direct GitHub Releases URL
    val archiveSizeBytes: Long,
    val sha256Checksum: String? = null,
    val isInstalled: Boolean = false,
    val installPath: String? = null
) {
    val clangExecutable: File?
        get() = installPath?.let { File(it, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang") }

    val clangPlusExecutable: File?
        get() = installPath?.let { File(it, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang++") }

    val cmakeToolchainFile: File?
        get() = installPath?.let { File(it, "build/cmake/android.toolchain.cmake") }

    val ndkBuildScript: File?
        get() = installPath?.let { File(it, "ndk-build") }
}

object DefaultNdkCatalog {
    private const val BASE_URL = "https://github.com/lonxzsy/prismde-ndk/releases/download"

    val AVAILABLE_VERSIONS = listOf(
        NdkVersion(
            versionTag = "r27",
            displayName = "Android NDK r27 (Latest)",
            llvmVersion = "Clang 18.0.1",
            downloadUrl = "$BASE_URL/v1.0.0/android-ndk-r27-aarch64.tar.xz",
            archiveSizeBytes = 430_000_000L
        ),
        NdkVersion(
            versionTag = "r26c",
            displayName = "Android NDK r26c (LTS - Recommended)",
            llvmVersion = "Clang 17.0.2",
            downloadUrl = "$BASE_URL/v1.0.0/android-ndk-r26c-aarch64.tar.xz",
            archiveSizeBytes = 385_000_000L
        ),
        NdkVersion(
            versionTag = "r25c",
            displayName = "Android NDK r25c (LTS)",
            llvmVersion = "Clang 14.0.7",
            downloadUrl = "$BASE_URL/v1.0.0/android-ndk-r25c-aarch64.tar.xz",
            archiveSizeBytes = 350_000_000L
        ),
        NdkVersion(
            versionTag = "r23c",
            displayName = "Android NDK r23c (Legacy)",
            llvmVersion = "Clang 12.0.8",
            downloadUrl = "$BASE_URL/v1.0.0/android-ndk-r23c-aarch64.tar.xz",
            archiveSizeBytes = 310_000_000L
        )
    )
}
