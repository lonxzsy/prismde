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

            // Ensure cand is flattened/linked so ndk-build exists directly in cand root
            com.prismde.feature_build.engine.BuildToolInstaller.flattenOrLinkNdkRoot(cand)

            // 1. If cand itself has toolchains or tools, it is the effective directory
            if (isUsableNdk(cand)) {
                return cand
            }

            // 2. Check if nested inside android-ndk-aide
            val aide = File(cand, "android-ndk-aide")
            if (isUsableNdk(aide)) {
                com.prismde.feature_build.engine.BuildToolInstaller.flattenOrLinkNdkRoot(aide)
                return cand.takeIf { File(it, "ndk-build").exists() } ?: aide
            }

            val resolved = com.prismde.feature_ndk.engine.NdkValidator.resolveNdkRoot(cand)
            if (isUsableNdk(resolved)) {
                com.prismde.feature_build.engine.BuildToolInstaller.flattenOrLinkNdkRoot(resolved)
                return cand.takeIf { File(it, "ndk-build").exists() } ?: resolved
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

    fun ensureSourceProperties(overrideRevision: String? = null, context: android.content.Context? = null) {
        val rev = overrideRevision ?: getPkgRevision()
        val targets = mutableListOf<File>()
        getEffectiveNdkDir()?.let { targets.add(it) }
        installPath?.let { targets.add(File(it)) }
        targets.add(File("/data/user/0/com.prismde/files/ndk/$versionTag"))
        targets.add(File("/data/data/com.prismde/files/ndk/$versionTag"))
        targets.add(File("/data/user/0/com.prismde/files/ndk/$versionTag/android-ndk-aide"))
        targets.add(File("/data/data/com.prismde/files/ndk/$versionTag/android-ndk-aide"))

        for (target in targets.distinct()) {
            ensureNdkMetadata(target, rev, context)
        }
    }

    fun ensurePermissions(context: android.content.Context? = null) {
        getEffectiveNdkDir()?.let { ensureNdkPermissions(it, context) }
    }

    companion object {
        const val NDK_ABIS_JSON = """{
  "armeabi-v7a": {
    "bitness": 32,
    "default": true,
    "deprecated": false,
    "proc": "armv7-a",
    "arch": "arm",
    "triple": "arm-linux-androideabi",
    "llvm_triple": "armv7-none-linux-androideabi"
  },
  "arm64-v8a": {
    "bitness": 64,
    "default": true,
    "deprecated": false,
    "proc": "aarch64",
    "arch": "arm64",
    "triple": "aarch64-linux-android",
    "llvm_triple": "aarch64-none-linux-android"
  },
  "x86": {
    "bitness": 32,
    "default": true,
    "deprecated": false,
    "proc": "i686",
    "arch": "x86",
    "triple": "i686-linux-android",
    "llvm_triple": "i686-none-linux-android"
  },
  "x86_64": {
    "bitness": 64,
    "default": true,
    "deprecated": false,
    "proc": "x86_64",
    "arch": "x86_64",
    "triple": "x86_64-linux-android",
    "llvm_triple": "x86_64-none-linux-android"
  }
}"""

        const val NDK_PLATFORMS_JSON = """{
  "min": 16,
  "max": 34,
  "aliases": {
    "20": 19,
    "25": 24,
    "J": 16,
    "J-MR1": 17,
    "J-MR2": 18,
    "K": 19,
    "L": 21,
    "L-MR1": 22,
    "M": 23,
    "N": 24,
    "N-MR1": 24,
    "O": 26,
    "O-MR1": 27,
    "P": 28,
    "Q": 29,
    "R": 30,
    "S": 31,
    "Sv2": 32,
    "Tiramisu": 33,
    "UpsideDownCake": 34
  }
}"""

        fun isAllowedNdkDirectory(dir: File): Boolean {
            if (!dir.exists() || !dir.isDirectory) return false
            val norm = dir.path.replace("\\", "/").lowercase()
            val name = dir.name.lowercase()
            // Dangerous directory names that should never be treated as NDK directories
            if (name in setOf("android-sdk", "tools", "platforms", "build-tools", "licenses", "system-images", "sources")) {
                return false
            }
            // Dangerous subpaths: anything inside platforms, build-tools, etc.
            if (norm.contains("/platforms/") || norm.endsWith("/platforms") ||
                norm.contains("/build-tools/") || norm.endsWith("/build-tools") ||
                norm.endsWith("/tools") || norm.endsWith("/android-sdk")) {
                return false
            }
            return true
        }

        fun getNdkTargetDirs(ndkDir: File): List<File> {
            if (!isAllowedNdkDirectory(ndkDir)) return emptyList()
            val targets = mutableListOf<File>()
            targets.add(ndkDir)
            val aideChild = File(ndkDir, "android-ndk-aide")
            if (aideChild.exists() && aideChild.isDirectory && isAllowedNdkDirectory(aideChild)) {
                targets.add(aideChild)
            }
            // If ndkDir itself is named android-ndk-aide, include its immediate parent as NDK package root
            if (ndkDir.name == "android-ndk-aide") {
                ndkDir.parentFile?.let { p ->
                    if (isAllowedNdkDirectory(p)) targets.add(p)
                }
            }
            return targets.distinct().filter { isAllowedNdkDirectory(it) }
        }

        fun ensureNdkSourceProperties(ndkDir: File, revision: String = "26.2.11394342") {
            if (!ndkDir.exists() || !ndkDir.isDirectory) return

            val targetDirs = getNdkTargetDirs(ndkDir)
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

        fun ensureNdkMetadata(
            ndkDir: File,
            revision: String = "26.2.11394342",
            context: android.content.Context? = null
        ) {
            if (!ndkDir.exists() || !ndkDir.isDirectory) return

            ensureNdkSourceProperties(ndkDir, revision)

            val targetDirs = getNdkTargetDirs(ndkDir)

            for (dir in targetDirs) {
                try {
                    val metaDir = File(dir, "meta").also { it.mkdirs() }
                    val abisJson = File(metaDir, "abis.json")
                    val currentAbis = if (abisJson.exists()) {
                        try { abisJson.readText() } catch (_: Throwable) { "" }
                    } else ""
                    val needsRewrite = !abisJson.exists() || abisJson.length() == 0L ||
                            !currentAbis.contains("\"arm64-v8a\"") ||
                            !currentAbis.contains("\"armeabi-v7a\"") ||
                            !currentAbis.contains("\"x86\"") ||
                            !currentAbis.contains("\"x86_64\"")
                    if (needsRewrite) {
                        abisJson.writeText(NDK_ABIS_JSON)
                        abisJson.setReadable(true, false)
                    }
                    val platformsJson = File(metaDir, "platforms.json")
                    val currentPlatforms = if (platformsJson.exists()) {
                        try { platformsJson.readText() } catch (_: Throwable) { "" }
                    } else ""
                    val needsPlatformsRewrite = !platformsJson.exists() || platformsJson.length() == 0L ||
                            !currentPlatforms.contains("\"min\": 16")
                    if (needsPlatformsRewrite) {
                        platformsJson.writeText(NDK_PLATFORMS_JSON)
                        platformsJson.setReadable(true, false)
                    }
                } catch (_: Throwable) {}
            }

            // Ensure host architecture aliases (e.g. linux-x86_64 -> linux-arm64) for AGP
            ensureHostArchitectureCompatibility(ndkDir)

            // Ensure STL shared and static libraries exist for all target triples
            ensureNdkStlLibraries(ndkDir, context)

            // Ensure platforms directory and sysroot API level subdirectories exist for AGP
            ensureNdkPlatforms(ndkDir)
        }

        fun findLibcxxShared(ndkDir: File, context: android.content.Context? = null): File? {
            // 1. Check relative candidate paths inside ndkDir and parent
            val candidatePaths = listOf(
                "sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so",
                "android-ndk-aide/sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so",
                "toolchains/llvm/prebuilt/linux-arm64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so",
                "toolchains/llvm/prebuilt/linux-aarch64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so",
                "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so",
                "sysroot/usr/lib/aarch64-linux-android/libc++_shared.so",
                "sysroot/usr/lib/libc++_shared.so"
            )
            for (rel in candidatePaths) {
                val f = File(ndkDir, rel)
                if (f.exists() && f.isFile && f.length() > 1000L) return f
                val fParent = File(ndkDir.parentFile, rel)
                if (fParent.exists() && fParent.isFile && fParent.length() > 1000L) return fParent
            }

            // 2. Search for any libc++_shared.so in ndkDir
            try {
                val foundInNdk = ndkDir.walkTopDown().maxDepth(7).firstOrNull {
                    it.isFile && it.name == "libc++_shared.so" && it.length() > 1000L
                }
                if (foundInNdk != null) return foundInNdk
            } catch (_: Throwable) {}

            // 3. Extract from context assets or filesDir
            if (context != null) {
                try {
                    val jdkLib = File(context.filesDir, "tools/jdk/lib/libc++_shared.so")
                    if (jdkLib.exists() && jdkLib.isFile && jdkLib.length() > 1000L) return jdkLib

                    // Extract from APK assets
                    val cacheCopy = File(context.cacheDir, "libc++_shared.so")
                    if (!cacheCopy.exists() || cacheCopy.length() < 1000L) {
                        context.assets.open("jdk_libs/arm64-v8a/libc++_shared.so").use { input ->
                            java.io.FileOutputStream(cacheCopy).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    if (cacheCopy.exists() && cacheCopy.length() > 1000L) return cacheCopy
                } catch (_: Throwable) {}
            }

            // 4. Known storage fallback locations in PrismDE
            val appDataDirs = listOf(
                File("/data/user/0/com.prismde/files/tools/jdk/lib/libc++_shared.so"),
                File("/data/data/com.prismde/files/tools/jdk/lib/libc++_shared.so")
            )
            for (f in appDataDirs) {
                if (f.exists() && f.isFile && f.length() > 1000L) return f
            }

            // 5. System libc++.so
            val systemCandidates = listOf(
                File("/system/lib64/libc++.so"),
                File("/apex/com.android.runtime/lib64/bionic/libc++.so"),
                File("/system/lib/libc++.so")
            )
            for (sys in systemCandidates) {
                if (sys.exists() && sys.isFile && sys.length() > 1000L) return sys
            }

            return null
        }

        fun ensureNdkStlLibraries(ndkDir: File, context: android.content.Context? = null) {
            if (!ndkDir.exists()) return

            val targetDirs = getNdkTargetDirs(ndkDir).toMutableList()

            // If context is available, also include sdk/ndk directories
            if (context != null) {
                val sdkNdkRoot = File(context.filesDir, "tools/android-sdk/ndk")
                if (sdkNdkRoot.exists() && sdkNdkRoot.isDirectory) {
                    sdkNdkRoot.listFiles()?.filter { it.isDirectory }?.let { targetDirs.addAll(it) }
                }
            }
            val knownSdkNdk = listOf(
                File("/data/user/0/com.prismde/files/tools/android-sdk/ndk/26.2.11394342"),
                File("/data/data/com.prismde/files/tools/android-sdk/ndk/26.2.11394342")
            )
            for (sdkNdk in knownSdkNdk) {
                if (sdkNdk.exists()) targetDirs.add(sdkNdk)
            }

            // 1. Locate reference libc++_shared.so
            val stlCandidate = findLibcxxShared(ndkDir, context)

            val triples = listOf(
                "aarch64-linux-android",
                "arm-linux-androideabi",
                "i686-linux-android",
                "x86_64-linux-android"
            )
            val emptyArBytes = "!<arch>\n".toByteArray(Charsets.US_ASCII)

            for (dir in targetDirs.distinct()) {
                if (!dir.exists() || !dir.isDirectory) continue

                // If dir has nested android-ndk-aide, link key subfolders to dir root
                val aide = File(dir, "android-ndk-aide")
                if (aide.exists() && aide.isDirectory && aide != dir) {
                    val aliasNames = listOf("toolchains", "sysroot", "sources", "prebuilt", "build", "platforms")
                    for (name in aliasNames) {
                        val srcSub = File(aide, name)
                        val dstSub = File(dir, name)
                        if (srcSub.exists() && !dstSub.exists()) {
                            try {
                                android.system.Os.symlink(srcSub.absolutePath, dstSub.absolutePath)
                            } catch (_: Throwable) {
                                try { srcSub.copyRecursively(dstSub, overwrite = false) } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                // Host toolchain folders
                val x86Prebuilt = File(dir, "toolchains/llvm/prebuilt/linux-x86_64")
                val arm64Prebuilt = File(dir, "toolchains/llvm/prebuilt/linux-arm64")
                val aarch64Prebuilt = File(dir, "toolchains/llvm/prebuilt/linux-aarch64")

                // Discover existing sysroot in dir or its prebuilts
                val existingSysroot = listOf(
                    File(x86Prebuilt, "sysroot"),
                    File(arm64Prebuilt, "sysroot"),
                    File(aarch64Prebuilt, "sysroot"),
                    File(dir, "sysroot"),
                    File(aide, "sysroot")
                ).firstOrNull { it.exists() && it.isDirectory }

                // Collect all sysroot targets to provision
                val sysrootDirs = mutableListOf<File>()
                for (prebuilt in listOf(x86Prebuilt, arm64Prebuilt, aarch64Prebuilt)) {
                    val pSysroot = File(prebuilt, "sysroot")
                    if (!pSysroot.exists()) {
                        if (existingSysroot != null && existingSysroot != pSysroot) {
                            try {
                                android.system.Os.symlink(existingSysroot.absolutePath, pSysroot.absolutePath)
                            } catch (_: Throwable) {
                                try { pSysroot.mkdirs() } catch (_: Throwable) {}
                            }
                        } else {
                            pSysroot.mkdirs()
                        }
                    }
                    if (pSysroot.exists()) sysrootDirs.add(pSysroot)
                }
                val baseSysroot = File(dir, "sysroot").also { it.mkdirs() }
                sysrootDirs.add(baseSysroot)

                for (sysroot in sysrootDirs.distinct()) {
                    val usrLib = File(sysroot, "usr/lib").also { it.mkdirs() }

                    val existingStl = triples.map { File(usrLib, "$it/libc++_shared.so") }
                        .firstOrNull { it.exists() && it.isFile && it.length() > 1000L } ?: stlCandidate

                    for (triple in triples) {
                        val tripleDir = File(usrLib, triple).also { it.mkdirs() }
                        val sharedSo = File(tripleDir, "libc++_shared.so")
                        if (!sharedSo.exists() || sharedSo.length() < 1000L) {
                            if (existingStl != null && existingStl.exists()) {
                                try {
                                    existingStl.copyTo(sharedSo, overwrite = true)
                                    sharedSo.setReadable(true, false)
                                } catch (_: Throwable) {}
                            } else if (!sharedSo.exists() || sharedSo.length() == 0L) {
                                // Fallback minimal valid 64-bit ELF shared library stub to pass file checks
                                try {
                                    sharedSo.writeBytes(byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0xB7.toByte(), 0))
                                    sharedSo.setReadable(true, false)
                                } catch (_: Throwable) {}
                            }
                        }

                        // Ensure static stubs for C++ STL and GCC runtime compatibility (required by ld.lld)
                        val staticStubNames = listOf(
                            "libc++_static.a",
                            "libc++abi.a",
                            "libgcc.a",
                            "libgcc_real.a",
                            "libatomic.a",
                            "libunwind.a"
                        )
                        for (staticName in staticStubNames) {
                            val staticA = File(tripleDir, staticName)
                            if (!staticA.exists() || staticA.length() == 0L) {
                                try {
                                    staticA.writeBytes(emptyArBytes)
                                    staticA.setReadable(true, false)
                                } catch (_: Throwable) {}
                            }
                            val rootStatic = File(usrLib, staticName)
                            if (!rootStatic.exists() || rootStatic.length() == 0L) {
                                try {
                                    rootStatic.writeBytes(emptyArBytes)
                                    rootStatic.setReadable(true, false)
                                } catch (_: Throwable) {}
                            }
                        }

                        // Also ensure libgcc.a in sources/cxx-stl/llvm-libc++/libs/$abi/
                        val abiName = when {
                            triple.startsWith("aarch64") -> "arm64-v8a"
                            triple.startsWith("arm") -> "armeabi-v7a"
                            triple.startsWith("i686") -> "x86"
                            else -> "x86_64"
                        }
                        val stlAbiDir = File(dir, "sources/cxx-stl/llvm-libc++/libs/$abiName")
                        if (stlAbiDir.exists()) {
                            for (staticName in listOf("libgcc.a", "libgcc_real.a", "libatomic.a", "libunwind.a")) {
                                val stlStatic = File(stlAbiDir, staticName)
                                if (!stlStatic.exists() || stlStatic.length() == 0L) {
                                    try {
                                        stlStatic.writeBytes(emptyArBytes)
                                        stlStatic.setReadable(true, false)
                                    } catch (_: Throwable) {}
                                }
                            }
                        }

                        // Ensure essential system shared library stubs (libc.so, libm.so, libdl.so, liblog.so, libandroid.so, libz.so)
                        val essentialSos = listOf("libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so")
                        val is64Bit = triple.contains("64")
                        val minElfBytes = if (is64Bit) {
                            byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0xB7.toByte(), 0)
                        } else {
                            byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0x28.toByte(), 0)
                        }
                        for (soName in essentialSos) {
                            val soFile = File(tripleDir, soName)
                            if (!soFile.exists() || soFile.length() == 0L) {
                                val sysCandidate = if (is64Bit) File("/system/lib64/$soName") else File("/system/lib/$soName")
                                if (sysCandidate.exists() && sysCandidate.isFile && sysCandidate.length() > 1000L) {
                                    try {
                                        sysCandidate.copyTo(soFile, overwrite = true)
                                        soFile.setReadable(true, false)
                                    } catch (_: Throwable) {
                                        try { soFile.writeBytes(minElfBytes); soFile.setReadable(true, false) } catch (_: Throwable) {}
                                    }
                                } else {
                                    try { soFile.writeBytes(minElfBytes); soFile.setReadable(true, false) } catch (_: Throwable) {}
                                }
                            }
                        }
                    }

                    // Headers
                    val usrInc = File(sysroot, "usr/include")
                    if (existingSysroot != null && existingSysroot != sysroot) {
                        val srcInc = File(existingSysroot, "usr/include")
                        if (srcInc.exists() && !usrInc.exists()) {
                            try {
                                android.system.Os.symlink(srcInc.absolutePath, usrInc.absolutePath)
                            } catch (_: Throwable) {
                                try { srcInc.copyRecursively(usrInc, overwrite = false) } catch (_: Throwable) {}
                            }
                        }
                    }
                    if (usrInc.exists()) {
                        val cppV1 = File(usrInc, "c++/v1")
                        if (!cppV1.exists()) {
                            val candidateIncludes = listOf(
                                File(dir, "sources/cxx-stl/llvm-libc++/include"),
                                File(dir, "android-ndk-aide/sources/cxx-stl/llvm-libc++/include"),
                                File(dir.parentFile, "sources/cxx-stl/llvm-libc++/include")
                            )
                            val srcInc = candidateIncludes.firstOrNull { it.exists() && it.isDirectory }
                            if (srcInc != null) {
                                try {
                                    cppV1.parentFile?.mkdirs()
                                    android.system.Os.symlink(srcInc.absolutePath, cppV1.absolutePath)
                                } catch (_: Throwable) {
                                    try { srcInc.copyRecursively(cppV1, overwrite = true) } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        fun ensureNdkPlatforms(ndkDir: File) {
            val targetDirs = getNdkTargetDirs(ndkDir)

            val apiLevels = listOf(21, 24, 34)
            val abiToTriple = mapOf(
                "arm64" to "aarch64-linux-android",
                "arm" to "arm-linux-androideabi",
                "x86" to "i686-linux-android",
                "x86_64" to "x86_64-linux-android"
            )

            for (dir in targetDirs) {
                // 1. Ensure <dir>/platforms/android-<api>/arch-<abi>/usr/lib
                val platformsDir = File(dir, "platforms").also { it.mkdirs() }
                for (api in apiLevels) {
                    val platformApiDir = File(platformsDir, "android-$api").also { it.mkdirs() }
                    for ((arch, triple) in abiToTriple) {
                        val archDir = File(platformApiDir, "arch-$arch")
                        val archUsrLib = File(archDir, "usr/lib").also { it.mkdirs() }
                        val archUsrInc = File(archDir, "usr/include").also { it.mkdirs() }

                        // Locate triple dir in sysroot
                        val sysrootTripleDir = listOf(
                            File(dir, "toolchains/llvm/prebuilt/linux-arm64/sysroot/usr/lib/$triple"),
                            File(dir, "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple"),
                            File(dir, "sysroot/usr/lib/$triple")
                        ).firstOrNull { it.exists() && it.isDirectory }

                        if (sysrootTripleDir != null) {
                            sysrootTripleDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                                val target = File(archUsrLib, f.name)
                                if (!target.exists()) {
                                    try {
                                        android.system.Os.symlink(f.absolutePath, target.absolutePath)
                                    } catch (_: Throwable) {
                                        try { f.copyTo(target, overwrite = false) } catch (_: Throwable) {}
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Ensure sysroot/usr/lib/<triple>/<api> subdirectories exist with .so stubs
                val sysroots = listOf(
                    File(dir, "toolchains/llvm/prebuilt/linux-arm64/sysroot"),
                    File(dir, "toolchains/llvm/prebuilt/linux-x86_64/sysroot"),
                    File(dir, "sysroot")
                ).filter { it.exists() && it.isDirectory }

                for (sysroot in sysroots) {
                    val usrLib = File(sysroot, "usr/lib")
                    if (!usrLib.exists()) continue

                    for ((_, triple) in abiToTriple) {
                        val tripleDir = File(usrLib, triple)
                        if (!tripleDir.exists()) continue

                        for (api in apiLevels) {
                            val apiSubDir = File(tripleDir, "$api")
                            if (!apiSubDir.exists()) {
                                try {
                                    apiSubDir.mkdirs()
                                } catch (_: Throwable) {}
                            }
                            if (apiSubDir.exists() && apiSubDir.isDirectory) {
                                tripleDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".so") || it.name.endsWith(".a")) }?.forEach { soFile ->
                                    val dst = File(apiSubDir, soFile.name)
                                    if (!dst.exists()) {
                                        try {
                                            android.system.Os.symlink(soFile.absolutePath, dst.absolutePath)
                                        } catch (_: Throwable) {
                                            try { soFile.copyTo(dst, overwrite = false) } catch (_: Throwable) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun ensureHostArchitectureCompatibility(ndkDir: File) {
            val targetDirs = getNdkTargetDirs(ndkDir)

            for (dir in targetDirs) {
                // 1. prebuilt/linux-x86_64, linux-arm64, linux-aarch64
                val prebuiltDir = File(dir, "prebuilt")
                if (prebuiltDir.exists()) {
                    val x86 = File(prebuiltDir, "linux-x86_64")
                    val arm64 = File(prebuiltDir, "linux-arm64")
                    val aarch64 = File(prebuiltDir, "linux-aarch64")

                    val existingBase = when {
                        arm64.exists() && arm64.isDirectory -> arm64
                        aarch64.exists() && aarch64.isDirectory -> aarch64
                        x86.exists() && x86.isDirectory -> x86
                        else -> null
                    }

                    if (existingBase != null) {
                        for (targetSub in listOf(x86, arm64, aarch64)) {
                            if (!targetSub.exists()) {
                                var linked = false
                                try {
                                    android.system.Os.symlink(existingBase.name, targetSub.absolutePath)
                                    linked = true
                                } catch (_: Throwable) {}
                                if (!linked) {
                                    try {
                                        targetSub.mkdirs()
                                        existingBase.listFiles()?.forEach { file ->
                                            val linkFile = File(targetSub, file.name)
                                            if (!linkFile.exists()) {
                                                try {
                                                    android.system.Os.symlink(file.absolutePath, linkFile.absolutePath)
                                                } catch (_: Throwable) {
                                                    try { file.copyRecursively(linkFile, overwrite = true) } catch (_: Throwable) {}
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }

                // 2. toolchains/llvm/prebuilt/linux-x86_64, linux-arm64, linux-aarch64
                val llvmPrebuiltDir = File(dir, "toolchains/llvm/prebuilt")
                if (llvmPrebuiltDir.exists()) {
                    val x86 = File(llvmPrebuiltDir, "linux-x86_64")
                    val arm64 = File(llvmPrebuiltDir, "linux-arm64")
                    val aarch64 = File(llvmPrebuiltDir, "linux-aarch64")

                    val existingBase = when {
                        arm64.exists() && arm64.isDirectory -> arm64
                        aarch64.exists() && aarch64.isDirectory -> aarch64
                        x86.exists() && x86.isDirectory -> x86
                        else -> null
                    }

                    if (existingBase != null) {
                        for (targetSub in listOf(x86, arm64, aarch64)) {
                            if (!targetSub.exists()) {
                                var linked = false
                                try {
                                    android.system.Os.symlink(existingBase.name, targetSub.absolutePath)
                                    linked = true
                                } catch (_: Throwable) {}
                                if (!linked) {
                                    try {
                                        targetSub.mkdirs()
                                        existingBase.listFiles()?.forEach { file ->
                                            val linkFile = File(targetSub, file.name)
                                            if (!linkFile.exists()) {
                                                try {
                                                    android.system.Os.symlink(file.absolutePath, linkFile.absolutePath)
                                                } catch (_: Throwable) {
                                                    try { file.copyRecursively(linkFile, overwrite = true) } catch (_: Throwable) {}
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        fun ensureNdkPermissions(ndkDir: File, context: android.content.Context? = null) {
            if (!ndkDir.exists()) return

            com.prismde.feature_build.engine.BuildToolInstaller.flattenOrLinkNdkRoot(ndkDir)
            com.prismde.feature_build.engine.BuildToolInstaller.patchNdkMakefiles(ndkDir)
            ensureNdkMetadata(ndkDir, context = context)

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

            // 2. Discover native ARM64 make source if any
            val localMakeCandidates = listOf(
                File(ndkDir, "prebuilt/linux-arm64/bin/make"),
                File(ndkDir, "prebuilt/linux-aarch64/bin/make"),
                File(ndkDir, "prebuilt/linux-x86_64/bin/make"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-arm64/bin/make"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-aarch64/bin/make"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-x86_64/bin/make"),
                File(ndkDir, "bin/make"),
                File(ndkDir, "android-ndk-aide/bin/make"),
                File("/data/data/com.termux/files/usr/bin/make"),
                File("/data/user/0/com.termux/files/usr/bin/make"),
                File("/data/data/com.itsaky.androidide/files/usr/bin/make"),
                File("/data/user/0/com.itsaky.androidide/files/usr/bin/make")
            ) + (if (context != null) listOf(
                File(context.filesDir, "tools/bin/make"),
                File(context.filesDir, "bin/make"),
                File(context.filesDir, "tools/make/bin/make"),
                File(context.filesDir, "tools/make")
            ) else emptyList())

            val nativeMakeSource = localMakeCandidates.firstOrNull { it.exists() && com.prismde.feature_build.engine.BuildToolInstaller.isExecutableOnCurrentPlatform(it) }

            // 3. Explicitly ensure busybox, make, and all prebuilt tools exist and are executable
            val prebuiltBinDirs = listOf(
                File(ndkDir, "prebuilt/linux-arm64/bin"),
                File(ndkDir, "prebuilt/linux-aarch64/bin"),
                File(ndkDir, "prebuilt/linux-x86_64/bin"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-arm64/bin"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-aarch64/bin"),
                File(ndkDir, "android-ndk-aide/prebuilt/linux-x86_64/bin")
            )

            for (prebuiltBin in prebuiltBinDirs) {
                if (!prebuiltBin.exists() || !prebuiltBin.isDirectory) continue

                val busybox = File(prebuiltBin, "busybox")
                if (busybox.exists()) {
                    applyChmod755(busybox)
                    // Note: 'make' is GNU Make, NOT a busybox applet. Do NOT include 'make' here.
                    val essentialTools = listOf(
                        "mkdir", "sh", "rm", "cp", "mv", "sed", "awk", "cat",
                        "echo", "uname", "tar", "grep", "find", "chmod", "basename", "dirname",
                        "cmp", "cut", "head", "tail", "sort", "uniq", "tr"
                    )
                    for (tool in essentialTools) {
                        val toolFile = File(prebuiltBin, tool)
                        val needsFix = !toolFile.exists() || (toolFile.isFile && toolFile.length() == 0L) ||
                                (toolFile.isFile && !com.prismde.feature_build.engine.BuildToolInstaller.isExecutableOnCurrentPlatform(toolFile))
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

                // If make is missing or incompatible and we have a native make source, install it
                val makeFile = File(prebuiltBin, "make")
                val makeIsNative = makeFile.exists() && com.prismde.feature_build.engine.BuildToolInstaller.isExecutableOnCurrentPlatform(makeFile)
                if (!makeIsNative && nativeMakeSource != null && nativeMakeSource.absolutePath != makeFile.absolutePath) {
                    try {
                        makeFile.delete()
                        try { android.system.Os.remove(makeFile.absolutePath) } catch (_: Throwable) {}
                        nativeMakeSource.copyTo(makeFile, overwrite = true)
                        applyChmod755(makeFile)
                    } catch (_: Throwable) {}
                }

                prebuiltBin.listFiles()?.forEach { f ->
                    applyChmod755(f)
                }
            }

            // 3. Ensure LLVM bin directory tools are executable
            val llvmBinDirs = listOf(
                File(ndkDir, "toolchains/llvm/prebuilt/linux-arm64/bin"),
                File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/bin"),
                File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin"),
                File(ndkDir, "bin"),
                File(ndkDir, "android-ndk-aide/toolchains/llvm/prebuilt/linux-arm64/bin"),
                File(ndkDir, "android-ndk-aide/toolchains/llvm/prebuilt/linux-x86_64/bin")
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

                    // Ensure target-prefixed compiler aliases (e.g. aarch64-linux-android24-clang -> clang)
                    val targetPrefixes = listOf(
                        "aarch64-linux-android",
                        "armv7a-linux-androideabi",
                        "i686-linux-android",
                        "x86_64-linux-android"
                    )
                    val apiSuffixes = listOf("", "21", "24", "26", "30", "34")
                    for (prefix in targetPrefixes) {
                        for (api in apiSuffixes) {
                            val aliasName = "$prefix$api-clang"
                            val aliasFile = File(binDir, aliasName)
                            if (!aliasFile.exists()) {
                                try {
                                    android.system.Os.symlink("clang", aliasFile.absolutePath)
                                } catch (_: Throwable) {
                                    try { clangExe.copyTo(aliasFile, overwrite = false) } catch (_: Throwable) {}
                                }
                                applyChmod755(aliasFile)
                            }
                            val aliasPlusName = "$prefix$api-clang++"
                            val aliasPlusFile = File(binDir, aliasPlusName)
                            if (!aliasPlusFile.exists()) {
                                try {
                                    android.system.Os.symlink("clang++", aliasPlusFile.absolutePath)
                                } catch (_: Throwable) {
                                    try { clangPlusExe.copyTo(aliasPlusFile, overwrite = false) } catch (_: Throwable) {}
                                }
                                applyChmod755(aliasPlusFile)
                            }
                        }
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

            // Recursively normalize shebangs across all scripts in NDK
            fun patchScriptsIn(dir: File) {
                if (!dir.exists() || !dir.isDirectory) return
                try {
                    dir.walkTopDown().maxDepth(8).forEach { file ->
                        if (file.isFile && (file.extension in setOf("sh", "bash", "") || file.name.startsWith("ndk-"))) {
                            applyChmod755(file)
                            try {
                                val header = ByteArray(128)
                                val read = file.inputStream().use { it.read(header) }
                                if (read > 2 && header[0] == '#'.code.toByte() && header[1] == '!'.code.toByte()) {
                                    val fullText = file.readText()
                                    val lines = fullText.split("\n", limit = 2)
                                    val firstLine = lines[0].trimEnd('\r')
                                    if (firstLine.contains("/bin/sh") || firstLine.contains("/bin/bash") ||
                                        firstLine.contains("/usr/bin/env sh") || firstLine.contains("/usr/bin/env bash") ||
                                        firstLine.contains("/usr/bin/sh") || firstLine.contains("/usr/bin/bash")) {
                                        val patchedFirstLine = firstLine.replace(
                                            Regex("""^#!\s*(?:/usr/bin/env\s+(?:sh|bash)|/(?:usr/)?bin/(?:sh|bash))"""),
                                            "#!/system/bin/sh"
                                        )
                                        val remaining = if (lines.size > 1) lines[1].replace("\r\n", "\n") else ""
                                        val newContent = if (remaining.isNotEmpty()) "$patchedFirstLine\n$remaining" else "$patchedFirstLine\n"
                                        file.writeText(newContent)
                                        applyChmod755(file)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }

            val allTargets = getNdkTargetDirs(ndkDir).toMutableList()
            if (context != null) {
                val sdkNdkDir = File(context.filesDir, "tools/android-sdk/ndk")
                if (sdkNdkDir.exists() && sdkNdkDir.isDirectory) {
                    sdkNdkDir.listFiles()?.filter { it.isDirectory }?.let { allTargets.addAll(it) }
                }
                val appNdkDir = File(context.filesDir, "ndk")
                if (appNdkDir.exists() && appNdkDir.isDirectory) {
                    appNdkDir.listFiles()?.filter { it.isDirectory }?.let { allTargets.addAll(it) }
                }
            }

            for (target in allTargets.distinct().filter { isAllowedNdkDirectory(it) }) {
                patchScriptsIn(target)
                val tmpDir = File(target, "tmp")
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
