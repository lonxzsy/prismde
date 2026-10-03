package com.prismde.feature_ndk.engine

import java.io.File

object NdkValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val actualNdkDir: File?,
        val foundClang: Boolean,
        val foundSysroot: Boolean,
        val foundCMakeToolchain: Boolean,
        val foundNdkBuild: Boolean,
        val message: String
    )

    fun validate(ndkDir: File): ValidationResult {
        val isRu = java.util.Locale.getDefault().language == "ru"
        if (!ndkDir.exists() || !ndkDir.isDirectory) {
            return ValidationResult(
                isValid = false,
                actualNdkDir = null,
                foundClang = false,
                foundSysroot = false,
                foundCMakeToolchain = false,
                foundNdkBuild = false,
                message = if (isRu) "Директория NDK не найдена: ${ndkDir.absolutePath}" else "NDK directory not found: ${ndkDir.absolutePath}"
            )
        }

        // Try direct folder first, then check if it's nested inside android-ndk-aide or a subfolder
        val resolvedDir = resolveNdkRoot(ndkDir)

        val clangCandidates = listOf(
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-arm64/bin/clang"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-x86_64/bin/clang"),
            File(resolvedDir, "bin/clang"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-arm64/bin/clang++"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang++"),
            File(resolvedDir, "bin/clang++")
        )
        val hasClang = clangCandidates.any { it.exists() }

        val sysrootCandidates = listOf(
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-arm64/sysroot"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/sysroot"),
            File(resolvedDir, "sysroot"),
            File(resolvedDir, "platforms")
        )
        val hasSysroot = sysrootCandidates.any { it.exists() }

        val cmakeToolchain = File(resolvedDir, "build/cmake/android.toolchain.cmake")
        val hasCMakeToolchain = cmakeToolchain.exists()

        val ndkBuildCandidates = listOf(
            File(resolvedDir, "ndk-build"),
            File(resolvedDir, "build/ndk-build"),
            File(resolvedDir, "ndk-build-android")
        )
        val hasNdkBuild = ndkBuildCandidates.any { it.exists() }

        // As long as clang, ndk-build, sysroot, or cmake is present, the NDK is usable
        val isValid = hasClang || hasNdkBuild || hasSysroot || hasCMakeToolchain

        val message = when {
            isValid -> if (isRu) "NDK тулчейн успешно проверен и готов к сборке" else "NDK toolchain verified and ready for build"
            else -> if (isRu) "В директории отсутствуют ключевые компоненты компилятора NDK" else "Key NDK compiler components missing in directory"
        }

        return ValidationResult(
            isValid = isValid,
            actualNdkDir = if (isValid) resolvedDir else null,
            foundClang = hasClang,
            foundSysroot = hasSysroot,
            foundCMakeToolchain = hasCMakeToolchain,
            foundNdkBuild = hasNdkBuild,
            message = message
        )
    }

    fun resolveNdkRoot(dir: File): File {
        if (!dir.exists() || !dir.isDirectory) return dir

        fun isNdkRoot(f: File): Boolean {
            return File(f, "bin/clang").exists() ||
                    File(f, "bin/clang++").exists() ||
                    File(f, "toolchains").exists() ||
                    File(f, "ndk-build").exists() ||
                    File(f, "build/cmake/android.toolchain.cmake").exists()
        }

        if (isNdkRoot(dir)) {
            return dir
        }

        val aideChild = File(dir, "android-ndk-aide")
        if (aideChild.exists() && aideChild.isDirectory) {
            val resolved = resolveNdkRoot(aideChild)
            if (isNdkRoot(resolved)) return resolved
        }

        // Check common NDK root folder names
        val commonNames = listOf("android-ndk-r26c", "android-ndk-r26", "android-ndk", "ndk")
        for (name in commonNames) {
            val child = File(dir, name)
            if (child.exists() && child.isDirectory) {
                if (isNdkRoot(child)) return child
                val resolved = resolveNdkRoot(child)
                if (isNdkRoot(resolved)) return resolved
            }
        }

        val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (child in subdirs) {
            if (isNdkRoot(child)) {
                return child
            }
        }

        // Recursively inspect child folders up to 2 levels
        for (child in subdirs) {
            val nested = resolveNdkRoot(child)
            if (isNdkRoot(nested)) {
                return nested
            }
        }

        return dir
    }
}
