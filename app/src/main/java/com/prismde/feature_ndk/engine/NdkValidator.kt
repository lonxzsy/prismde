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
        if (!ndkDir.exists() || !ndkDir.isDirectory) {
            return ValidationResult(
                isValid = false,
                actualNdkDir = null,
                foundClang = false,
                foundSysroot = false,
                foundCMakeToolchain = false,
                foundNdkBuild = false,
                message = "Директория NDK не найдена: ${ndkDir.absolutePath}"
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
            isValid -> "NDK тулчейн успешно проверен и готов к сборке"
            else -> "В директории отсутствуют ключевые компоненты компилятора NDK"
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

        if (File(dir, "bin").exists() ||
            File(dir, "toolchains").exists() ||
            File(dir, "ndk-build").exists() ||
            File(dir, "build/cmake/android.toolchain.cmake").exists()
        ) {
            return dir
        }

        val aideChild = File(dir, "android-ndk-aide")
        if (aideChild.exists() && aideChild.isDirectory) {
            return resolveNdkRoot(aideChild)
        }

        val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (subdirs.size == 1) {
            val child = subdirs[0]
            if (File(child, "bin").exists() ||
                File(child, "toolchains").exists() ||
                File(child, "ndk-build").exists() ||
                File(child, "build/cmake/android.toolchain.cmake").exists()
            ) {
                return child
            }
        }
        return dir
    }
}
