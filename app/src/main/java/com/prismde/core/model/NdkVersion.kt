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
        fun isUsableNdk(dir: File): Boolean {
            if (!dir.exists() || !dir.isDirectory) return false
            if (File(dir, "toolchains").exists() || File(dir, "bin").exists() || File(dir, "ndk-build").exists()) {
                return true
            }
            return false
        }

        val candidates = mutableListOf<File>()
        installPath?.let { candidates.add(File(it)) }
        candidates.add(File("/data/user/0/com.prismde/files/ndk/$versionTag"))
        candidates.add(File("/data/data/com.prismde/files/ndk/$versionTag"))

        for (cand in candidates) {
            if (!cand.exists() || !cand.isDirectory) continue

            // 1. If cand itself has toolchains or tools, it is the effective directory
            if (isUsableNdk(cand)) {
                return cand
            }

            // 2. Check if nested inside android-ndk-aide
            val aide = File(cand, "android-ndk-aide")
            if (isUsableNdk(aide)) {
                return aide
            }

            val resolved = com.prismde.feature_ndk.engine.NdkValidator.resolveNdkRoot(cand)
            if (isUsableNdk(resolved)) {
                return resolved
            }
        }

        return installPath?.let { File(it) }
    }

    private fun resolveCompilerBinary(isPlusPlus: Boolean): File? {
        val rootDir = getEffectiveNdkDir() ?: return null

        val binDirs = listOf(
            File(rootDir, "toolchains/llvm/prebuilt/linux-arm64/bin"),
            File(rootDir, "toolchains/llvm/prebuilt/linux-aarch64/bin"),
            File(rootDir, "toolchains/llvm/prebuilt/linux-x86_64/bin"),
            File(rootDir, "bin"),
            File(rootDir, "android-ndk-aide/toolchains/llvm/prebuilt/linux-arm64/bin")
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
            if (primaryTarget.exists() && primaryTarget.isFile && primaryTarget.length() > 1000L) {
                primaryTarget.setExecutable(true, false)
                return primaryTarget
            }

            // Find any clang executable in binDir (e.g. clang-7, clang-17, clang-21)
            val anyClang = binDir.listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith("clang") && !it.name.endsWith(".h") && it.length() > 10000L
            }

            if (anyClang != null) {
                anyClang.setExecutable(true, false)
                // Attempt to link or copy to primaryTarget (clang++ or clang)
                if (!primaryTarget.exists() || primaryTarget.length() < 1000L) {
                    try { primaryTarget.delete() } catch (_: Throwable) {}
                    try {
                        android.system.Os.symlink(anyClang.name, primaryTarget.absolutePath)
                    } catch (_: Throwable) {
                        try {
                            anyClang.copyTo(primaryTarget, overwrite = true)
                        } catch (_: Throwable) {}
                    }
                }
                if (primaryTarget.exists() && primaryTarget.length() > 1000L) {
                    primaryTarget.setExecutable(true, false)
                    return primaryTarget
                }
                return anyClang
            }
        }

        // Recursive fallback across rootDir
        val found = rootDir.walkTopDown().maxDepth(6).firstOrNull { f ->
            f.isFile && f.name.startsWith("clang") && !f.name.endsWith(".h") && f.length() > 10000L
        }
        if (found != null) {
            found.setExecutable(true, false)
            val targetSibling = File(found.parentFile, if (isPlusPlus) "clang++" else "clang")
            if (!targetSibling.exists() || targetSibling.length() < 1000L) {
                try {
                    android.system.Os.symlink(found.name, targetSibling.absolutePath)
                } catch (_: Throwable) {
                    try {
                        found.copyTo(targetSibling, overwrite = true)
                    } catch (_: Throwable) {}
                }
            }
            if (targetSibling.exists() && targetSibling.length() > 1000L) {
                targetSibling.setExecutable(true, false)
                return targetSibling
            }
            return found
        }

        return null
    }

    fun getPkgRevision(): String {
        return when {
            versionTag.startsWith("r27") -> "27.0.12077973"
            versionTag.startsWith("r26") -> "26.2.11394342"
            versionTag.startsWith("r25") -> "25.2.9519653"
            versionTag.startsWith("r23") -> "23.2.8568313"
            else -> "26.2.11394342"
        }
    }

    fun ensureSourceProperties(overrideRevision: String? = null) {
        val rev = overrideRevision ?: getPkgRevision()
        val targets = mutableListOf<File>()
        getEffectiveNdkDir()?.let { targets.add(it) }
        installPath?.let { targets.add(File(it)) }
        targets.add(File("/data/user/0/com.prismde/files/ndk/$versionTag"))
        targets.add(File("/data/data/com.prismde/files/ndk/$versionTag"))
        targets.add(File("/data/user/0/com.prismde/files/ndk/$versionTag/android-ndk-aide"))
        targets.add(File("/data/data/com.prismde/files/ndk/$versionTag/android-ndk-aide"))

        for (target in targets.distinct()) {
            ensureNdkSourceProperties(target, rev)
        }
    }

    fun ensurePermissions() {
        getEffectiveNdkDir()?.let { ensureNdkPermissions(it) }
    }

    companion object {
        fun ensureNdkSourceProperties(ndkDir: File, revision: String = "26.2.11394342") {
            if (!ndkDir.exists() || !ndkDir.isDirectory) return

            val targetDirs = listOf(
                ndkDir,
                File(ndkDir, "android-ndk-aide"),
                ndkDir.parentFile
            ).filterNotNull().filter { it.exists() && it.isDirectory }

            val content = "Pkg.Desc = Android NDK\nPkg.Revision = $revision\n"

            for (dir in targetDirs) {
                val propFile = File(dir, "source.properties")
                if (!propFile.exists() || propFile.length() == 0L) {
                    try {
                        propFile.writeText(content)
                        propFile.setReadable(true, false)
                    } catch (_: Throwable) {}
                }
            }
        }

        fun ensureNdkPermissions(ndkDir: File) {
            if (!ndkDir.exists()) return

            ensureNdkSourceProperties(ndkDir)

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

                val clangBinary = binDir.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith("clang") && !it.name.endsWith(".h") && it.length() > 10000L
                }
                if (clangBinary != null) {
                    val clangExe = File(binDir, "clang")
                    val clangPlusExe = File(binDir, "clang++")
                    for (target in listOf(clangExe, clangPlusExe)) {
                        if (!target.exists() || target.length() < 1000L) {
                            try { target.delete() } catch (_: Throwable) {}
                            try {
                                android.system.Os.symlink(clangBinary.name, target.absolutePath)
                            } catch (_: Throwable) {
                                try {
                                    clangBinary.copyTo(target, overwrite = true)
                                } catch (_: Throwable) {}
                            }
                        }
                        applyChmod755(target)
                    }
                }

                binDir.listFiles()?.forEach { f ->
                    applyChmod755(f)
                }
            }

            // 4. Ensure scripts are executable, have valid shebangs and proper aliases
            for (dir in listOf(ndkDir, File(ndkDir, "android-ndk-aide"))) {
                if (dir.exists() && dir.isDirectory) {
                    val mainNdkBuild = File(dir, "ndk-build")
                    val altNdkBuild = File(dir, "ndk-build-android")
                    if (!mainNdkBuild.exists() && altNdkBuild.exists()) {
                        try {
                            android.system.Os.symlink("ndk-build-android", mainNdkBuild.absolutePath)
                        } catch (_: Throwable) {
                            try { altNdkBuild.copyTo(mainNdkBuild, overwrite = true) } catch (_: Throwable) {}
                        }
                    }
                }
            }

            val scriptTargets = listOf(
                File(ndkDir, "ndk-build"),
                File(ndkDir, "ndk-build-android"),
                File(ndkDir, "build/ndk-build"),
                File(ndkDir, "android-ndk-aide/ndk-build"),
                File(ndkDir, "android-ndk-aide/ndk-build-android"),
                File(ndkDir, "android-ndk-aide/build/ndk-build")
            )

            for (script in scriptTargets) {
                if (script.exists()) {
                    applyChmod755(script)
                    try {
                        val txt = script.readText()
                        var modified = false
                        var newTxt = txt
                        if (txt.contains("\r\n")) {
                            newTxt = newTxt.replace("\r\n", "\n")
                            modified = true
                        }
                        if (newTxt.startsWith("#!/bin/sh") || newTxt.startsWith("#!/usr/bin/sh") || newTxt.startsWith("#!/bin/bash")) {
                            newTxt = newTxt.replaceFirst(Regex("^#![^\\n]+"), "#!/system/bin/sh")
                            modified = true
                        }
                        if (modified) {
                            script.writeText(newTxt)
                            applyChmod755(script)
                        }
                    } catch (_: Throwable) {}
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

    val AVAILABLE_VERSIONS: List<NdkVersion>
        get() {
            val isRu = java.util.Locale.getDefault().language == "ru"
            return listOf(
                NdkVersion(
                    versionTag = "r26c",
                    displayName = if (isRu) "Android NDK r26c (Полный тулчейн - Релиз v1.0.1)" else "Android NDK r26c (Full Toolchain - Release v1.0.1)",
                    llvmVersion = if (isRu) "LLVM / Clang 17 (~385 МБ)" else "LLVM / Clang 17 (~385 MB)",
                    downloadUrl = R26_ASSET_URL,
                    archiveSizeBytes = R26_SIZE_BYTES,
                    isAvailable = true
                ),
                NdkVersion(
                    versionTag = "r26c_light",
                    displayName = if (isRu) "Android NDK r26c Light (Компактный - Релиз v1.0.0)" else "Android NDK r26c Light (Compact - Release v1.0.0)",
                    llvmVersion = if (isRu) "LLVM / Clang 17 (~100 МБ)" else "LLVM / Clang 17 (~100 MB)",
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
}
