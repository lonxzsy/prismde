package com.prismde.feature_ndk.engine

import java.io.File

object NdkValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val actualNdkDir: File?,
        val foundClang: Boolean,
        val foundSysroot: Boolean,
        val foundCMakeToolchain: Boolean,
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
                message = "Директория NDK не найдена: ${ndkDir.absolutePath}"
            )
        }

        // Try direct folder first, then check if it's nested inside a single child folder
        val resolvedDir = resolveNdkRoot(ndkDir)

        val clangCandidates = listOf(
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang"),
            File(resolvedDir, "bin/clang"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-x86_64/bin/clang"),
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang++"),
            File(resolvedDir, "bin/clang++")
        )
        val hasClang = clangCandidates.any { it.exists() }

        val sysrootCandidates = listOf(
            File(resolvedDir, "toolchains/llvm/prebuilt/linux-aarch64/sysroot"),
            File(resolvedDir, "sysroot"),
            File(resolvedDir, "platforms")
        )
        val hasSysroot = sysrootCandidates.any { it.exists() }

        val cmakeToolchain = File(resolvedDir, "build/cmake/android.toolchain.cmake")
        val hasCMakeToolchain = cmakeToolchain.exists()

        // As long as clang, sysroot, or cmake is present, the NDK is usable
        val isValid = hasClang || hasSysroot || hasCMakeToolchain

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
            message = message
        )
    }

    private fun resolveNdkRoot(dir: File): File {
        // If dir already contains bin or toolchains or build or ndk-build, it is root
        if (File(dir, "bin").exists() || File(dir, "toolchains").exists() || File(dir, "ndk-build").exists()) {
            return dir
        }
        // If there is only one subdirectory inside, check if that child is the NDK root
        val subdirs = dir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (subdirs.size == 1) {
            val child = subdirs[0]
            if (File(child, "bin").exists() || File(child, "toolchains").exists() || File(child, "ndk-build").exists()) {
                return child
            }
        }
        return dir
    }
}
