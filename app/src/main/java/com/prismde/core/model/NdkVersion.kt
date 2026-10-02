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

    fun ensurePermissions() {
        getEffectiveNdkDir()?.let { ensureNdkPermissions(it) }
    }

    companion object {
        fun ensureNdkPermissions(ndkDir: File) {
            if (!ndkDir.exists()) return

            fun applyChmod755(target: File) {
                try {
                    android.system.Os.chmod(target.absolutePath, 493) // 0755 in octal
                } catch (_: Throwable) {}
                try {
                    target.setExecutable(true, false)
                    target.setReadable(true, false)
                } catch (_: Throwable) {}
            }

            // 1. Try system chmod -R 755
            try {
                val process = ProcessBuilder("/system/bin/chmod", "-R", "755", ndkDir.absolutePath).start()
                process.waitFor()
            } catch (_: Throwable) {}

            // 2. Explicitly ensure busybox and all prebuilt tools exist and are executable
            val prebuiltBinDirs = listOf(
                File(ndkDir, "prebuilt/linux-arm64/bin"),
                File(ndkDir, "prebuilt/linux-aarch64/bin"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-arm64/bin"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-aarch64/bin")
            )

            for (prebuiltBin in prebuiltBinDirs) {
                if (!prebuiltBin.exists() || !prebuiltBin.isDirectory) continue

                val busybox = File(prebuiltBin, "busybox")
                if (busybox.exists()) {
                    applyChmod755(busybox)
                    val essentialTools = listOf(
                        "mkdir", "make", "sh", "rm", "cp", "mv", "sed", "awk", "cat",
                        "echo", "uname", "tar", "grep", "find", "chmod", "basename", "dirname"
                    )
                    for (tool in essentialTools) {
                        val toolFile = File(prebuiltBin, tool)
                        val needsFix = !toolFile.exists() || (toolFile.isFile && toolFile.length() == 0L)
                        if (needsFix) {
                            try { toolFile.delete() } catch (_: Throwable) {}
                            try { android.system.Os.remove(toolFile.absolutePath) } catch (_: Throwable) {}
                            var linked = false
                            try {
                                android.system.Os.symlink("busybox", toolFile.absolutePath)
                                linked = true
                            } catch (_: Throwable) {}
                            if (!linked) {
                                try { busybox.copyTo(toolFile, overwrite = true) } catch (_: Throwable) {}
                            }
                        }
                        applyChmod755(toolFile)
                    }
                }

                prebuiltBin.listFiles()?.forEach { f ->
                    applyChmod755(f)
                }
            }

            // 3. Ensure LLVM bin directory tools are executable
            val llvmBinDirs = listOf(
                File(ndkDir, "toolchains/llvm/prebuilt/linux-arm64/bin"),
                File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/bin"),
                File(ndkDir, "bin"),
                File(ndkDir, "android-ndk-aide/toolchains/llvm/prebuilt/linux-arm64/bin")
            )
            for (binDir in llvmBinDirs) {
                if (!binDir.exists() || !binDir.isDirectory) continue
                binDir.listFiles()?.forEach { f ->
                    applyChmod755(f)
                }
            }

            // 4. Ensure scripts are executable
            listOf(
                File(ndkDir, "ndk-build"),
                File(ndkDir, "ndk-build-android"),
                File(ndkDir, "android-ndk-aide/ndk-build"),
                File(ndkDir, "android-ndk-aide/ndk-build-android")
            ).forEach { script ->
                if (script.exists()) {
                    applyChmod755(script)
                }
            }

            // 5. Ensure tmp directory exists and is writable
            listOf(
                File(ndkDir, "tmp"),
                File(ndkDir, "android-ndk-aide/tmp")
            ).forEach { tmpDir ->
                try {
                    tmpDir.mkdirs()
                    tmpDir.setWritable(true, false)
                    tmpDir.setReadable(true, false)
                    tmpDir.setExecutable(true, false)
                    try { android.system.Os.chmod(tmpDir.absolutePath, 511) } catch (_: Throwable) {} // 0777
                } catch (_: Throwable) {}
            }
        }
    }
}

object DefaultNdkCatalog {
    // Official release assets from https://github.com/lonxzsy/prismde-ndk/releases/tag/v1.0.1 and v1.0.0
    const val DEFAULT_ACTIVE_TAG = "r26c"
    const val R26_ASSET_URL = "https://github.com/lonxzsy/prismde-ndk/releases/download/v1.0.1/ndk.tar.gz"
    const val R26_SIZE_BYTES = 404_183_894L

    const val R26_LIGHT_TAG = "r26c_light"
    const val R26_LIGHT_URL = "https://github.com/lonxzsy/prismde-ndk/releases/download/v1.0.0/ndk-arm64-26.tar.gz"
    const val R26_LIGHT_SIZE_BYTES = 105_975_765L
    const val R26_LIGHT_SHA256 = "bb839e34dcb0ab025e51457ef14a15ae4355feaca55079901ab0ddb020286d21"

    val AVAILABLE_VERSIONS = listOf(
        NdkVersion(
            versionTag = "r26c",
            displayName = "Android NDK r26c (Полный тулчейн - Релиз v1.0.1)",
            llvmVersion = "LLVM / Clang 17 (~385 МБ)",
            downloadUrl = R26_ASSET_URL,
            archiveSizeBytes = R26_SIZE_BYTES,
            isAvailable = true
        ),
        NdkVersion(
            versionTag = "r26c_light",
            displayName = "Android NDK r26c Light (Компактный - Релиз v1.0.0)",
            llvmVersion = "LLVM / Clang 17 (~100 МБ)",
            downloadUrl = R26_LIGHT_URL,
            archiveSizeBytes = R26_LIGHT_SIZE_BYTES,
            sha256Checksum = R26_LIGHT_SHA256,
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
