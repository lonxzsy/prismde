package com.prismde.feature_ndk.engine

import java.io.File

object NdkValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val foundClang: Boolean,
        val foundSysroot: Boolean,
        val foundCMakeToolchain: Boolean,
        val message: String
    )

    fun validate(ndkDir: File): ValidationResult {
        if (!ndkDir.exists() || !ndkDir.isDirectory) {
            return ValidationResult(
                isValid = false,
                foundClang = false,
                foundSysroot = false,
                foundCMakeToolchain = false,
                message = "Директория NDK не найдена: ${ndkDir.absolutePath}"
            )
        }

        // Look for clang / clang++
        val clangCandidates = listOf(
            File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/bin/clang"),
            File(ndkDir, "bin/clang"),
            File(ndkDir, "toolchains/llvm/prebuilt/linux-x86_64/bin/clang")
        )
        val hasClang = clangCandidates.any { it.exists() }

        // Look for sysroot
        val sysrootCandidates = listOf(
            File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/sysroot"),
            File(ndkDir, "sysroot"),
            File(ndkDir, "platforms")
        )
        val hasSysroot = sysrootCandidates.any { it.exists() }

        // Look for android.toolchain.cmake
        val cmakeToolchain = File(ndkDir, "build/cmake/android.toolchain.cmake")
        val hasCMakeToolchain = cmakeToolchain.exists()

        val isValid = hasClang || hasSysroot || hasCMakeToolchain

        val message = when {
            isValid -> "NDK тулчейн успешно проверен и готов к сборке"
            else -> "В директории отсутствуют ключевые компоненты компилятора NDK"
        }

        return ValidationResult(
            isValid = isValid,
            foundClang = hasClang,
            foundSysroot = hasSysroot,
            foundCMakeToolchain = hasCMakeToolchain,
            message = message
        )
    }
}
