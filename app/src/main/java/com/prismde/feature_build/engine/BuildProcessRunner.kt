package com.prismde.feature_build.engine

import com.prismde.core.model.AndroidAbi
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class BuildOutputEvent {
    data class LogLine(val text: String, val isError: Boolean = false) : BuildOutputEvent()
    data class DiagnosticFound(val diagnostic: Diagnostic) : BuildOutputEvent()
    data class Completed(val exitCode: Int, val success: Boolean, val artifactFile: File? = null) : BuildOutputEvent()
}

class BuildProcessRunner {

    private val isRu get() = java.util.Locale.getDefault().language == "ru"
    private val parser = ClangDiagnosticParser()
    private val _events = MutableSharedFlow<BuildOutputEvent>(extraBufferCapacity = 500)
    val events: SharedFlow<BuildOutputEvent> = _events

    suspend fun runBuild(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration
    ): Boolean = withContext(Dispatchers.IO) {
        val detectedType = if (config.projectType == ProjectType.AUTO_DETECT) {
            ProjectDetector.detect(project.rootDir)
        } else {
            config.projectType
        }

        val isRu = java.util.Locale.getDefault().language == "ru"
        _events.emit(BuildOutputEvent.LogLine("=== PrismDE Build System ==="))
        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Проект: ${project.name}" else "Project: ${project.name}"))
        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Тип проекта: $detectedType" else "Project type: $detectedType"))
        _events.emit(BuildOutputEvent.LogLine(if (isRu) "NDK: ${ndk.displayName} (версия ${ndk.versionTag})" else "NDK: ${ndk.displayName} (version ${ndk.versionTag})"))
        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Целевой ABI: ${config.selectedAbi.abiString} (API ${config.minApiLevel})" else "Target ABI: ${config.selectedAbi.abiString} (API ${config.minApiLevel})"))

        // Ensure all NDK tools, busybox applets and scripts have executable permissions
        ndk.ensurePermissions()

        val libsDir = File(project.rootDir, "libs/${config.selectedAbi.abiString}")
        libsDir.mkdirs()

        val artifactFile = when (detectedType) {
            ProjectType.PURE_JNI_SO -> {
                buildPureJniSo(project, ndk, config, libsDir)
            }
            ProjectType.CMAKE -> {
                buildCMake(project, ndk, config)
            }
            ProjectType.SINGLE_FILE_EXECUTABLE -> {
                buildSingleExecutable(project, ndk, config)
            }
            else -> {
                buildPureJniSo(project, ndk, config, libsDir)
            }
        }

        val success = artifactFile != null && artifactFile.exists()
        val exitCode = if (success) 0 else 1
        if (success) {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Сборка успешно завершена! Создан файл: ${artifactFile.absolutePath}" else "✔ Build completed successfully! Generated file: ${artifactFile.absolutePath}"))
        } else {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка сборки. Проверьте карточки ошибок выше." else "✖ Build failed. Check the error diagnostics above.", isError = true))
        }

        _events.emit(BuildOutputEvent.Completed(exitCode, success, artifactFile))
        success
    }

    private suspend fun buildPureJniSo(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration,
        libsDir: File
    ): File? {
        val soName = "lib${project.name}.so"
        val targetSo = File(libsDir, soName)

        val jniDir = if (project.hasJniDir) project.jniDir else project.rootDir
        val directSources = jniDir.listFiles { _, name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in ProjectDetector.COMPILABLE_EXTENSIONS
        }?.toList() ?: emptyList()
        val sources = if (directSources.isNotEmpty()) directSources else ProjectDetector.findSourceFiles(project)

        if (sources.isEmpty()) {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Не найдено исходных файлов C/C++ в ${jniDir.absolutePath}" else "No C/C++ source files found in ${jniDir.absolutePath}", isError = true))
            return null
        }

        // Check if ndk-build script is available
        val ndkBuildScript = ndk.ndkBuildScript
        if (project.hasAndroidMk && ndkBuildScript != null && ndkBuildScript.exists()) {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется ndk-build с файлом Android.mk..." else "Using ndk-build with Android.mk..."))
            ndkBuildScript.setExecutable(true, false)
            val projectPath = if (project.rootDir.name.equals("jni", ignoreCase = true)) {
                project.rootDir.parentFile?.absolutePath ?: project.rootDir.absolutePath
            } else {
                project.rootDir.absolutePath
            }
            val command = listOf(
                "/system/bin/sh",
                ndkBuildScript.absolutePath,
                "NDK_PROJECT_PATH=$projectPath",
                "APP_ABI=${config.selectedAbi.abiString}",
                "APP_PLATFORM=android-${config.minApiLevel}"
            )
            executeProcess(command, project.rootDir, ndkBuildScript.parentFile)
            return if (targetSo.exists()) targetSo else libsDir.listFiles { _, name -> name.endsWith(".so") }?.firstOrNull()
        }

        // Direct Clang++ invocation
        val compilerFile = ndk.clangPlusExecutable ?: ndk.clangExecutable
        if (compilerFile == null || !compilerFile.exists()) {
            val effectiveDir = ndk.getEffectiveNdkDir()?.absolutePath ?: if (isRu) "не найдена" else "not found"
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка: Компилятор Clang++ не найден в NDK (директория: $effectiveDir)." else "✖ Error: Clang++ compiler not found in NDK (directory: $effectiveDir).", isError = true))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Пожалуйста, проверьте установку NDK во вкладке «Настройки» или выполните переустановку." else "Please check your NDK installation in the Settings tab or reinstall it.", isError = true))
            return null
        }
        compilerFile.setExecutable(true, false)
        val clangPath = compilerFile.absolutePath
        val targetTriple = "${config.selectedAbi.triple}${config.minApiLevel}"

        val command = mutableListOf(
            clangPath,
            "-target", targetTriple,
            "-shared",
            "-fPIC",
            "-fdiagnostics-parseable-fixits",
            config.cppStandard.flag,
            config.optimizationLevel.flag
        )

        // Add include directories (-I) so headers like obfuscate.h are found without passing them as compilation units
        val includeDirs = ProjectDetector.findIncludeDirectories(project)
        for (inc in includeDirs) {
            command.add("-I${inc.absolutePath}")
        }

        // Split flags
        command.addAll(config.customCFlags.split(" ").filter { it.isNotBlank() })
        command.addAll(sources.map { it.absolutePath })
        command.add("-o")
        command.add(targetSo.absolutePath)
        command.addAll(config.customLdFlags.split(" ").filter { it.isNotBlank() })

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Выполнение команды Clang++:" else "Executing Clang++ command:"))
        _events.emit(BuildOutputEvent.LogLine(command.joinToString(" ")))

        val success = executeProcess(command, project.rootDir, compilerFile.parentFile)
        return if (success && targetSo.exists()) targetSo else null
    }

    private suspend fun buildCMake(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration
    ): File? {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val buildDir = File(project.rootDir, "build/${config.selectedAbi.abiString}")
        buildDir.mkdirs()

        val toolchainFile = ndk.cmakeToolchainFile?.absolutePath
        val cmakeCommand = mutableListOf(
            "cmake",
            "-B", buildDir.absolutePath,
            "-G", "Ninja",
            "-DANDROID_ABI=${config.selectedAbi.abiString}",
            "-DANDROID_PLATFORM=android-${config.minApiLevel}"
        )
        if (toolchainFile != null) {
            cmakeCommand.add("-DCMAKE_TOOLCHAIN_FILE=$toolchainFile")
        }

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Генерация проекта CMake..." else "Configuring CMake project..."))
        if (!executeProcess(cmakeCommand, project.rootDir)) return null

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Сборка через Ninja..." else "Building with Ninja..."))
        val ninjaCommand = listOf("ninja", "-C", buildDir.absolutePath)
        if (!executeProcess(ninjaCommand, project.rootDir)) return null

        return buildDir.walkTopDown().firstOrNull { it.isFile && (it.extension == "so" || it.canExecute()) }
    }

    private suspend fun buildSingleExecutable(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration
    ): File? {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val binDir = File(project.rootDir, "bin/${config.selectedAbi.abiString}")
        binDir.mkdirs()
        val targetExe = File(binDir, project.name)

        val sources = ProjectDetector.findSourceFiles(project)
        if (sources.isEmpty()) {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Не найдено исходных файлов для сборки" else "No source files found to build", isError = true))
            return null
        }

        val compilerFile = ndk.clangPlusExecutable ?: ndk.clangExecutable
        if (compilerFile == null || !compilerFile.exists()) {
            val effectiveDir = ndk.getEffectiveNdkDir()?.absolutePath ?: if (isRu) "не найдена" else "not found"
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка: Компилятор Clang++ не найден в NDK (директория: $effectiveDir)." else "✖ Error: Clang++ compiler not found in NDK (directory: $effectiveDir).", isError = true))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Пожалуйста, проверьте установку NDK во вкладке «Настройки» или выполните переустановку." else "Please check your NDK installation in the Settings tab or reinstall it.", isError = true))
            return null
        }
        compilerFile.setExecutable(true, false)
        val clangPath = compilerFile.absolutePath
        val targetTriple = "${config.selectedAbi.triple}${config.minApiLevel}"

        val command = mutableListOf(
            clangPath,
            "-target", targetTriple,
            "-fPIE",
            "-pie",
            "-fdiagnostics-parseable-fixits",
            config.cppStandard.flag,
            config.optimizationLevel.flag
        )

        // Add include directories (-I) so headers like obfuscate.h are found without passing them as compilation units
        val includeDirs = ProjectDetector.findIncludeDirectories(project)
        for (inc in includeDirs) {
            command.add("-I${inc.absolutePath}")
        }

        command.addAll(config.customCFlags.split(" ").filter { it.isNotBlank() })
        command.addAll(sources.map { it.absolutePath })
        command.add("-o")
        command.add(targetExe.absolutePath)
        command.addAll(config.customLdFlags.split(" ").filter { it.isNotBlank() })

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Выполнение команды Clang++:" else "Executing Clang++ command:"))
        _events.emit(BuildOutputEvent.LogLine(command.joinToString(" ")))

        val success = executeProcess(command, project.rootDir, compilerFile.parentFile)
        return if (success && targetExe.exists()) targetExe else null
    }

    private suspend fun executeProcess(
        command: List<String>,
        workingDir: File,
        extraBinDir: File? = null
    ): Boolean {
        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)

            val env = processBuilder.environment()
            val existingPath = env["PATH"] ?: "/system/bin"
            if (extraBinDir != null && extraBinDir.exists()) {
                env["PATH"] = "${extraBinDir.absolutePath}:$existingPath"
            }
            val tempDir = File(workingDir, ".prism_tmp").also { it.mkdirs() }
            try {
                tempDir.setReadable(true, false)
                tempDir.setWritable(true, false)
                tempDir.setExecutable(true, false)
            } catch (_: Throwable) {}
            env["TMPDIR"] = tempDir.absolutePath
            env["NDK_ANDROID_TMPDIR"] = tempDir.absolutePath
            env["TEMP"] = tempDir.absolutePath
            env["HOME"] = workingDir.absolutePath

            val process = processBuilder.start()

            // Stream stdout
            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { _events.tryEmit(BuildOutputEvent.LogLine(it)) }
                    }
                }
            }

            // Stream stderr and parse diagnostics
            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { l ->
                            _events.tryEmit(BuildOutputEvent.LogLine(l, isError = true))
                            val diag = parser.parseLine(l)
                            if (diag != null) {
                                _events.tryEmit(BuildOutputEvent.DiagnosticFound(diag))
                            }
                        }
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            stdoutThread.join()
            stderrThread.join()

            val code = process.waitFor()
            code == 0
        } catch (e: Exception) {
            val isRu = java.util.Locale.getDefault().language == "ru"
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Исключение при запуске процесса: ${e.message}" else "Exception launching process: ${e.message}", isError = true))
            false
        }
    }
}
