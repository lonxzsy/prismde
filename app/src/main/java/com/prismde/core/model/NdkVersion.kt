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
            ensureNdkMetadata(target, rev)
        }
    }

    fun ensurePermissions(context: android.content.Context? = null) {
        getEffectiveNdkDir()?.let { ensureNdkPermissions(it, context) }
    }

    companion object {
        const val NDK_ABIS_JSON = """{
  "arm64-v8a": {
    "bitness": 64,
    "default": true,
    "deprecated": false,
    "proc": "aarch64",
    "arch": "arm64",
    "triple": "aarch64-linux-android",
    "llvm_triple": "aarch64-none-linux-android"
  }
}"""

        const val NDK_PLATFORMS_JSON = """{
  "min": 21,
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

        fun ensureNdkMetadata(
            ndkDir: File,
            revision: String = "26.2.11394342",
            context: android.content.Context? = null
        ) {
            if (!ndkDir.exists() || !ndkDir.isDirectory) return

            ensureNdkSourceProperties(ndkDir, revision)

            val targetDirs = listOf(
                ndkDir,
                File(ndkDir, "android-ndk-aide"),
                ndkDir.parentFile
            ).filterNotNull().filter { it.exists() && it.isDirectory }

            for (dir in targetDirs) {
                try {
                    val metaDir = File(dir, "meta").also { it.mkdirs() }
                    val abisJson = File(metaDir, "abis.json")
                    val needsRewrite = !abisJson.exists() || abisJson.length() == 0L ||
                            abisJson.readText().contains("\"proc\": \"armv7-a\"") ||
                            !abisJson.readText().contains("\"arm64-v8a\"")
                    if (needsRewrite) {
                        abisJson.writeText(NDK_ABIS_JSON)
                        abisJson.setReadable(true, false)
                    }
                    val platformsJson = File(metaDir, "platforms.json")
                    if (!platformsJson.exists() || platformsJson.length() == 0L) {
                        platformsJson.writeText(NDK_PLATFORMS_JSON)
                        platformsJson.setReadable(true, false)
                    }
                } catch (_: Throwable) {}
            }

            // Ensure host architecture aliases (e.g. linux-x86_64 -> linux-arm64) for AGP
            ensureHostArchitectureCompatibility(ndkDir)

            // Ensure STL shared and static libraries exist for all target triples
            ensureNdkStlLibraries(ndkDir, context)
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

            // 4. System libc++.so
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

            val targetDirs = listOf(
                ndkDir,
                File(ndkDir, "android-ndk-aide"),
                ndkDir.parentFile
            ).filterNotNull().filter { it.exists() && it.isDirectory }

            // 1. Locate libc++_shared.so
            val stlCandidate = findLibcxxShared(ndkDir, context)

            // 2. Locate all sysroot directories
            val sysrootDirs = mutableListOf<File>()
            for (dir in targetDirs) {
                val candidates = listOf(
                    File(dir, "toolchains/llvm/prebuilt/linux-x86_64/sysroot"),
                    File(dir, "toolchains/llvm/prebuilt/linux-arm64/sysroot"),
                    File(dir, "toolchains/llvm/prebuilt/linux-aarch64/sysroot"),
                    File(dir, "sysroot")
                )
                for (cand in candidates) {
                    if (cand.exists() && cand.isDirectory) {
                        sysrootDirs.add(cand)
                    }
                }
            }

            // Triples that AGP or build tools might inspect
            val triples = listOf(
                "aarch64-linux-android",
                "arm-linux-androideabi",
                "i686-linux-android",
                "x86_64-linux-android"
            )

            val emptyArBytes = "!<arch>\n".toByteArray(Charsets.US_ASCII)

            for (sysroot in sysrootDirs.distinct()) {
                val usrLib = File(sysroot, "usr/lib").also { it.mkdirs() }

                // Check if any triple already has a valid libc++_shared.so to reuse
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
                        }
                    }

                    // Ensure static stubs
                    val staticA = File(tripleDir, "libc++_static.a")
                    if (!staticA.exists() || staticA.length() == 0L) {
                        try {
                            staticA.writeBytes(emptyArBytes)
                            staticA.setReadable(true, false)
                        } catch (_: Throwable) {}
                    }
                    val abiA = File(tripleDir, "libc++abi.a")
                    if (!abiA.exists() || abiA.length() == 0L) {
                        try {
                            abiA.writeBytes(emptyArBytes)
                            abiA.setReadable(true, false)
                        } catch (_: Throwable) {}
                    }
                }

                // C++ STL headers: usr/include/c++/v1
                val usrInc = File(sysroot, "usr/include")
                if (usrInc.exists()) {
                    val cppV1 = File(usrInc, "c++/v1")
                    if (!cppV1.exists()) {
                        val candidateIncludes = listOf(
                            File(ndkDir, "sources/cxx-stl/llvm-libc++/include"),
                            File(ndkDir, "android-ndk-aide/sources/cxx-stl/llvm-libc++/include"),
                            File(ndkDir.parentFile, "sources/cxx-stl/llvm-libc++/include")
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

        fun ensureHostArchitectureCompatibility(ndkDir: File) {
            val targetDirs = listOf(
                ndkDir,
                File(ndkDir, "android-ndk-aide"),
                ndkDir.parentFile
            ).filterNotNull().filter { it.exists() && it.isDirectory }

            for (dir in targetDirs) {
                // 1. prebuilt/linux-x86_64
                val prebuiltDir = File(dir, "prebuilt")
                if (prebuiltDir.exists()) {
                    val x86 = File(prebuiltDir, "linux-x86_64")
                    val arm64 = File(prebuiltDir, "linux-arm64")
                    val aarch64 = File(prebuiltDir, "linux-aarch64")
                    val src = if (arm64.exists()) arm64 else if (aarch64.exists()) aarch64 else null
                    if (!x86.exists()) {
                        var linked = false
                        if (src != null) {
                            try {
                                android.system.Os.symlink(src.name, x86.absolutePath)
                                linked = true
                            } catch (_: Throwable) {}
                        }
                        if (!linked) {
                            try {
                                x86.mkdirs()
                                if (src != null && src.isDirectory) {
                                    src.listFiles()?.forEach { file ->
                                        val linkFile = File(x86, file.name)
                                        if (!linkFile.exists()) {
                                            try {
                                                android.system.Os.symlink(file.absolutePath, linkFile.absolutePath)
                                            } catch (_: Throwable) {
                                                try { file.copyRecursively(linkFile, overwrite = true) } catch (_: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }

                // 2. toolchains/llvm/prebuilt/linux-x86_64
                val llvmPrebuiltDir = File(dir, "toolchains/llvm/prebuilt")
                if (llvmPrebuiltDir.exists()) {
                    val x86 = File(llvmPrebuiltDir, "linux-x86_64")
                    val arm64 = File(llvmPrebuiltDir, "linux-arm64")
                    val aarch64 = File(llvmPrebuiltDir, "linux-aarch64")
                    val src = if (arm64.exists()) arm64 else if (aarch64.exists()) aarch64 else null
                    if (!x86.exists()) {
                        var linked = false
                        if (src != null) {
                            try {
                                android.system.Os.symlink(src.name, x86.absolutePath)
                                linked = true
                            } catch (_: Throwable) {}
                        }
                        if (!linked) {
                            try {
                                x86.mkdirs()
                                if (src != null && src.isDirectory) {
                                    src.listFiles()?.forEach { file ->
                                        val linkFile = File(x86, file.name)
                                        if (!linkFile.exists()) {
                                            try {
                                                android.system.Os.symlink(file.absolutePath, linkFile.absolutePath)
                                            } catch (_: Throwable) {
                                                try { file.copyRecursively(linkFile, overwrite = true) } catch (_: Throwable) {}
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }

        fun ensureNdkPermissions(ndkDir: File, context: android.content.Context? = null) {
            if (!ndkDir.exists()) return

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

            // 2. Explicitly ensure busybox and all prebuilt tools exist and are executable
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
