package com.prismde.feature_build.engine

import android.content.Context
import com.prismde.core.model.AndroidAbi
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.DefaultNdkCatalog
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
        ndk: NdkVersion?,
        config: BuildConfiguration,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val detectedType = if (config.projectType == ProjectType.AUTO_DETECT) {
            ProjectDetector.detect(project.rootDir, context)
        } else {
            config.projectType
        }

        val isRu = java.util.Locale.getDefault().language == "ru"

        if (detectedType == ProjectType.GRADLE) {
            val javaAvailable = ProjectDetector.isJavaAvailable(context, project.rootDir) ||
                    config.javaHome.isNotBlank() ||
                    (context != null && BuildToolInstaller.hasAnyJdkInstalled(context))
            if (!javaAvailable && (project.hasJniDir || project.hasAndroidMk) && ndk != null && ndk.isInstalled) {
                _events.emit(BuildOutputEvent.LogLine("=== PrismDE Build System (NDK Native Module) ==="))
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Проект: ${project.name}" else "Project: ${project.name}"))
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "ℹ Java/JDK не установлен для Gradle. В проекте найден нативный C/C++ модуль (${project.jniDir.name})." else "ℹ Java/JDK is not installed for Gradle. Found native C/C++ module (${project.jniDir.name})."))
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Запуск сборки нативной библиотеки .so через Android NDK..." else "Starting native .so library build with Android NDK..."))

                ndk.ensurePermissions()
                val libsDir = File(project.rootDir, "libs/${config.selectedAbi.abiString}")
                libsDir.mkdirs()
                val artifactFile = buildPureJniSo(project, ndk, config, libsDir, context)
                val success = artifactFile != null && artifactFile.exists()
                val exitCode = if (success) 0 else 1
                if (success) {
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Сборка нативной библиотеки успешно завершена! Создан файл: ${artifactFile.absolutePath}" else "✔ Native library build completed successfully! Generated file: ${artifactFile.absolutePath}"))
                } else {
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка сборки. Проверьте карточки ошибок выше." else "✖ Build failed. Check the error diagnostics above.", isError = true))
                }
                _events.emit(BuildOutputEvent.Completed(exitCode, success, artifactFile))
                return@withContext success
            }

            _events.emit(BuildOutputEvent.LogLine("=== PrismDE Build System (Gradle) ==="))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Проект: ${project.name}" else "Project: ${project.name}"))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Тип проекта: GRADLE (Android / Java)" else "Project type: GRADLE (Android / Java)"))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Задачи (Tasks): ${config.gradleTasks} ${config.gradleCustomFlags}" else "Tasks: ${config.gradleTasks} ${config.gradleCustomFlags}"))

            val artifactFile = buildGradle(project, config, ndk, context)
            val success = artifactFile != null && artifactFile.exists()
            val exitCode = if (success) 0 else 1
            if (success) {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Сборка Gradle успешно завершена! Создан артефакт: ${artifactFile.absolutePath}" else "✔ Gradle build completed successfully! Generated artifact: ${artifactFile.absolutePath}"))
            } else {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка сборки Gradle. Проверьте вывод и карточки ошибок выше." else "✖ Gradle build failed. Check the output and error diagnostics above.", isError = true))
            }
            _events.emit(BuildOutputEvent.Completed(exitCode, success, artifactFile))
            return@withContext success
        }

        if (detectedType == ProjectType.MAVEN) {
            _events.emit(BuildOutputEvent.LogLine("=== PrismDE Build System (Maven) ==="))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Проект: ${project.name}" else "Project: ${project.name}"))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Тип проекта: MAVEN (pom.xml)" else "Project type: MAVEN (pom.xml)"))
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "Цели (Goals): ${config.mavenGoals} ${config.mavenCustomFlags}" else "Goals: ${config.mavenGoals} ${config.mavenCustomFlags}"))

            val artifactFile = buildMaven(project, config, context)
            val success = artifactFile != null && artifactFile.exists()
            val exitCode = if (success) 0 else 1
            if (success) {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Сборка Maven успешно завершена! Создан артефакт: ${artifactFile.absolutePath}" else "✔ Maven build completed successfully! Generated artifact: ${artifactFile.absolutePath}"))
            } else {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка сборки Maven. Проверьте вывод и карточки ошибок выше." else "✖ Maven build failed. Check the output and error diagnostics above.", isError = true))
            }
            _events.emit(BuildOutputEvent.Completed(exitCode, success, artifactFile))
            return@withContext success
        }

        if (ndk == null || !ndk.isInstalled) {
            _events.emit(BuildOutputEvent.LogLine(if (isRu) "✖ Ошибка: NDK не установлен для сборки C/C++ проекта." else "✖ Error: NDK is not installed for building C/C++ project.", isError = true))
            _events.emit(BuildOutputEvent.Completed(1, false, null))
            return@withContext false
        }

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
                buildPureJniSo(project, ndk, config, libsDir, context)
            }
            ProjectType.CMAKE -> {
                buildCMake(project, ndk, config, context)
            }
            ProjectType.SINGLE_FILE_EXECUTABLE -> {
                buildSingleExecutable(project, ndk, config, context)
            }
            else -> {
                buildPureJniSo(project, ndk, config, libsDir, context)
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
        libsDir: File,
        context: Context? = null
    ): File? {
        val androidMkFile = when {
            File(project.jniDir, "Android.mk").exists() -> File(project.jniDir, "Android.mk")
            File(project.rootDir, "Android.mk").exists() -> File(project.rootDir, "Android.mk")
            File(project.rootDir, "app/src/main/jni/Android.mk").exists() -> File(project.rootDir, "app/src/main/jni/Android.mk")
            else -> File(project.jniDir, "Android.mk")
        }
        val customModule = parseLocalModule(androidMkFile)
        val soBaseName = customModule ?: project.name
        val soName = if (soBaseName.startsWith("lib")) "$soBaseName.so" else "lib$soBaseName.so"
        val targetSo = File(libsDir, soName)

        val jniDir = if (project.hasJniDir) project.jniDir else project.rootDir
        val directSources = jniDir.walkTopDown().filter { file ->
            file.isFile &&
            file.extension.lowercase() in ProjectDetector.COMPILABLE_EXTENSIONS &&
            file.parentFile?.name !in ProjectDetector.IGNORED_DIRS
        }.toList()
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
            val appMkFile = when {
                File(project.jniDir, "Application.mk").exists() -> File(project.jniDir, "Application.mk")
                File(project.rootDir, "Application.mk").exists() -> File(project.rootDir, "Application.mk")
                File(project.rootDir, "app/src/main/jni/Application.mk").exists() -> File(project.rootDir, "app/src/main/jni/Application.mk")
                else -> null
            }

            val mkDir = androidMkFile.absoluteFile.parentFile ?: project.rootDir
            val mkParentDir = mkDir.parentFile ?: mkDir

            val command = mutableListOf(
                "/system/bin/sh",
                ndkBuildScript.absolutePath,
                "APP_BUILD_SCRIPT=${androidMkFile.absolutePath}",
                "NDK_PROJECT_PATH=${mkDir.absolutePath}",
                "APP_ABI=${config.selectedAbi.abiString}",
                "APP_PLATFORM=android-${config.minApiLevel}",
                "APP_CFLAGS+=-D_USE_MATH_DEFINES",
                "APP_CPPFLAGS+=-D_USE_MATH_DEFINES"
            )
            if (appMkFile != null) {
                command.add("NDK_APPLICATION_MK=${appMkFile.absolutePath}")
            }

            // Clean up stale target .so before building to prevent false positive on compilation failure
            try {
                targetSo.delete()
                File(mkDir, "libs/${config.selectedAbi.abiString}/$soName").delete()
                File(mkParentDir, "libs/${config.selectedAbi.abiString}/$soName").delete()
                File(project.rootDir, "libs/${config.selectedAbi.abiString}/$soName").delete()
            } catch (_: Throwable) {}

            val buildSuccess = executeProcess(command, mkDir, ndkBuildScript.parentFile, ndk, config, context)
            if (!buildSuccess) {
                return null
            }

            val candidateSos = listOf(
                targetSo,
                File(mkDir, "libs/${config.selectedAbi.abiString}/$soName"),
                File(mkParentDir, "libs/${config.selectedAbi.abiString}/$soName"),
                File(project.rootDir, "libs/${config.selectedAbi.abiString}/$soName"),
                File(project.rootDir, "app/libs/${config.selectedAbi.abiString}/$soName")
            )
            val found = candidateSos.firstOrNull { it.exists() }
            return found ?: libsDir.listFiles { _, name -> name.endsWith(".so") }?.firstOrNull()
                ?: project.rootDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("so", ignoreCase = true) }
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

        val success = executeProcess(command, project.rootDir, compilerFile.parentFile, ndk, config, context)
        return if (success && targetSo.exists()) targetSo else null
    }

    private suspend fun buildCMake(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration,
        context: Context? = null
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
        if (!executeProcess(cmakeCommand, project.rootDir, null, ndk, config, context)) return null

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Сборка через Ninja..." else "Building with Ninja..."))
        val ninjaCommand = listOf("ninja", "-C", buildDir.absolutePath)
        if (!executeProcess(ninjaCommand, project.rootDir, null, ndk, config, context)) return null

        return buildDir.walkTopDown().firstOrNull { it.isFile && (it.extension == "so" || it.canExecute()) }
    }

    private suspend fun buildSingleExecutable(
        project: Project,
        ndk: NdkVersion,
        config: BuildConfiguration,
        context: Context? = null
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

        val success = executeProcess(command, project.rootDir, compilerFile.parentFile, ndk, config, context)
        return if (success && targetExe.exists()) targetExe else null
    }

    private suspend fun buildMaven(
        project: Project,
        config: BuildConfiguration,
        context: Context? = null
    ): File? {
        val isRu = java.util.Locale.getDefault().language == "ru"

        // 1. Check for Maven wrapper (mvnw) in project root
        val mvnwFile = File(project.rootDir, "mvnw")
        val mvnwCmd = File(project.rootDir, "mvnw.cmd")

        // 2. Check for internally installed Maven in PrismDE tools
        val internalMvn = if (context != null) BuildToolInstaller.getMavenExecutable(context) else null

        val (executableCmd, workingDir, extraBinDir) = when {
            mvnwFile.exists() -> {
                try { mvnwFile.setExecutable(true, false) } catch (_: Throwable) {}
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется Maven Wrapper (./mvnw)..." else "Using Maven Wrapper (./mvnw)..."))
                Triple(listOf("/system/bin/sh", mvnwFile.absolutePath), project.rootDir, null)
            }
            mvnwCmd.exists() && System.getProperty("os.name")?.lowercase()?.contains("windows") == true -> {
                _events.emit(BuildOutputEvent.LogLine("Using Maven Wrapper (mvnw.cmd)..."))
                Triple(listOf("cmd.exe", "/c", mvnwCmd.absolutePath), project.rootDir, null)
            }
            internalMvn != null && internalMvn.exists() -> {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется встроенный Apache Maven: ${internalMvn.absolutePath}" else "Using internal Apache Maven: ${internalMvn.absolutePath}"))
                Triple(listOf("/system/bin/sh", internalMvn.absolutePath), project.rootDir, internalMvn.parentFile)
            }
            else -> {
                // Search installed mvn binaries in common Termux / system paths
                val candidatePaths = listOf(
                    "/data/data/com.termux/files/usr/bin/mvn",
                    "/data/user/0/com.termux/files/usr/bin/mvn",
                    "/data/data/com.termux/files/usr/share/maven/bin/mvn",
                    "/system/bin/mvn",
                    "/system/xbin/mvn"
                )
                val found = candidatePaths.map { File(it) }.firstOrNull { it.exists() }
                if (found != null) {
                    try { found.setExecutable(true, false) } catch (_: Throwable) {}
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется установленный Maven: ${found.absolutePath}" else "Using installed Maven: ${found.absolutePath}"))
                    Triple(listOf("/system/bin/sh", found.absolutePath), project.rootDir, found.parentFile)
                } else if (context != null) {
                    // Auto-install Maven!
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "ℹ Maven не найден. Запуск автоматической установки Apache Maven ${BuildToolInstaller.MAVEN_VERSION} (~9 МБ)..." else "ℹ Maven not found. Starting automatic installation of Apache Maven ${BuildToolInstaller.MAVEN_VERSION} (~9 MB)..."))
                    val installed = BuildToolInstaller.installMaven(context) { status, pct ->
                        if (pct == 10f || pct == 75f || pct == 100f) {
                            _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                        }
                    }
                    val newlyInstalledMvn = BuildToolInstaller.getMavenExecutable(context)
                    if (installed && newlyInstalledMvn != null) {
                        _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Apache Maven успешно установлен: ${newlyInstalledMvn.absolutePath}" else "✔ Apache Maven installed successfully: ${newlyInstalledMvn.absolutePath}"))
                        Triple(listOf("/system/bin/sh", newlyInstalledMvn.absolutePath), project.rootDir, newlyInstalledMvn.parentFile)
                    } else {
                        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Поиск mvn в системном PATH..." else "Searching for mvn in system PATH..."))
                        Triple(listOf("mvn"), project.rootDir, null)
                    }
                } else {
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "Поиск mvn в системном PATH..." else "Searching for mvn in system PATH..."))
                    Triple(listOf("mvn"), project.rootDir, null)
                }
            }
        }

        // Check JDK availability before execution and auto-install if missing!
        var javaEnv = BuildToolInstaller.detectJavaEnvironment(context, project.rootDir, config.javaHome)
        if (!javaEnv.isAvailable && config.javaHome.isBlank() && !ProjectDetector.isJavaAvailable(context, project.rootDir)) {
            if (context != null) {
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "ℹ Java JDK не найден. Автоматическая загрузка автономного OpenJDK 17 LTS..."
                    else "ℹ Java JDK not found. Automatically downloading standalone OpenJDK 17 LTS..."
                ))
                val jdkInstalled = BuildToolInstaller.installJdk(context) { status, pct ->
                    _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                }
                if (jdkInstalled) {
                    javaEnv = BuildToolInstaller.detectJavaEnvironment(context, project.rootDir, config.javaHome)
                    _events.emit(BuildOutputEvent.LogLine(
                        if (isRu) "✔ OpenJDK 17 успешно установлен во внутреннее хранилище PrismDE!"
                        else "✔ OpenJDK 17 installed successfully into PrismDE internal storage!"
                    ))
                } else {
                    _events.emit(BuildOutputEvent.LogLine(
                        if (isRu) "⚠ Автоматическая установка OpenJDK не удалась. Откройте Настройки -> Инструменты сборки для повтора."
                        else "⚠ Automatic OpenJDK installation failed. Open Settings -> Build Tools to retry.",
                        isError = true
                    ))
                }
            } else {
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "⚠ Внимание: JDK (Java) не найден. Для сборки Maven требуется Java JDK."
                    else "⚠ Warning: JDK (Java) not found. Maven build requires a Java JDK.",
                    isError = true
                ))
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "💡 Установите OpenJDK в Настройках приложения (раздел «Инструменты сборки»)."
                    else "💡 Install OpenJDK in App Settings (Build Tools section).",
                    isError = true
                ))
            }
        }

        val goals = config.mavenGoals.split(" ").filter { it.isNotBlank() }.ifEmpty { listOf("package") }
        val flags = config.mavenCustomFlags.split(" ").filter { it.isNotBlank() }

        val tempDir = if (context != null) {
            File(context.cacheDir, "prism_tmp").also { it.mkdirs() }
        } else {
            File(project.rootDir, ".prism_tmp").also { it.mkdirs() }
        }

        // Configure private app HOME and local repository directory
        val homeDir = if (context != null) {
            File(context.filesDir, "home").also { it.mkdirs() }
        } else {
            File(project.rootDir, ".prism_home").also { it.mkdirs() }
        }
        val m2Dir = File(homeDir, ".m2").also { it.mkdirs() }
        val m2RepoDir = File(m2Dir, "repository").also { it.mkdirs() }
        try {
            homeDir.setReadable(true, false)
            homeDir.setWritable(true, false)
            homeDir.setExecutable(true, false)
            m2Dir.setReadable(true, false)
            m2Dir.setWritable(true, false)
            m2Dir.setExecutable(true, false)
            m2RepoDir.setReadable(true, false)
            m2RepoDir.setWritable(true, false)
            m2RepoDir.setExecutable(true, false)
        } catch (_: Throwable) {}

        // Ensure user settings.xml explicitly directs localRepository into app sandbox
        val userSettingsFile = File(m2Dir, "settings.xml")
        if (!userSettingsFile.exists()) {
            try {
                userSettingsFile.writeText(
                    "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n" +
                    "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "  xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n" +
                    "  <localRepository>${m2RepoDir.absolutePath}</localRepository>\n" +
                    "</settings>"
                )
            } catch (_: Throwable) {}
        }

        // Ensure JDK runtime dependencies (libz.so.1, libandroid-shmem, libandroid-spawn, libiconv) are in place
        if (context != null) {
            BuildToolInstaller.ensureJdkRuntimeLibraries(context)
        }

        // Neutralize Jansi native library dlopen on Android Bionic (avoid missing libc.so.6)
        if (context != null) {
            val mvnHome = BuildToolInstaller.getMavenHomeDir(context)
            if (mvnHome != null) {
                val jansiDir = File(mvnHome, "lib/jansi-native")
                if (jansiDir.exists()) {
                    try { jansiDir.deleteRecursively() } catch (_: Throwable) {}
                }
                val mvnBin = File(mvnHome, "bin/mvn")
                if (mvnBin.exists()) {
                    try {
                        val txt = mvnBin.readText()
                        if (txt.contains("jansi-native")) {
                            val patched = txt.replace("\"\${MAVEN_HOME}/lib/jansi-native\"", "\"\"")
                                             .replace("\${MAVEN_HOME}/lib/jansi-native", "")
                            mvnBin.writeText(patched)
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        val command = mutableListOf<String>()
        command.addAll(executableCmd)
        command.addAll(goals)
        command.addAll(flags)

        // Essential Maven CLI system properties (-D...):
        // Note: HotSpot JVM options like -XX:-UseCompressedOops belong strictly in JAVA_TOOL_OPTIONS / MAVEN_OPTS,
        // because Maven's CLI parser interprets flags starting with -X as debug + plugin goals (e.g. prefix 'X').
        val mavenSystemProps = listOf(
            "-Duser.home=${homeDir.absolutePath}",
            "-Dmaven.repo.local=${m2RepoDir.absolutePath}",
            "-Dlibrary.jansi.path=",
            "-Djansi.native=false",
            "-Djansi.mode=strip",
            "-Djansi.passthrough=true",
            "-Dstyle.color=never",
            "-Dmaven.color=false",
            "-Djava.io.tmpdir=${tempDir.absolutePath}",
            "-Djansi.tmpdir=${tempDir.absolutePath}"
        )
        for (prop in mavenSystemProps) {
            if (!command.contains(prop)) {
                command.add(prop)
            }
        }

        // Add batch mode flag to avoid interactive prompt freezes in mobile background process
        if (!command.contains("-B") && !command.contains("--batch-mode")) {
            command.add("-B")
        }

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Запуск команды Maven:" else "Executing Maven command:"))
        _events.emit(BuildOutputEvent.LogLine(command.joinToString(" ")))

        val success = executeProcess(command, workingDir, extraBinDir, null, config, context)

        // Find resulting artifact in target/ directory (.jar, .aar, .war, .apk)
        val targetDir = File(project.rootDir, "target")
        if (!targetDir.exists()) {
            if (!success) {
                _events.emit(
                    BuildOutputEvent.LogLine(
                        if (isRu) "Подсказка: Проверьте логи сборки выше и настройки pom.xml. Убедитесь, что все зависимости и плагины Maven доступны."
                        else "Hint: Check build logs above and pom.xml settings. Ensure all dependencies and Maven plugins are accessible.",
                        isError = true
                    )
                )
            }
            return null
        }

        val artifacts = project.rootDir.walkTopDown().maxDepth(5).filter { file ->
            file.isFile &&
                    (file.parentFile?.name == "target" || file.path.contains("target")) &&
                    (file.extension.equals("jar", ignoreCase = true) ||
                     file.extension.equals("aar", ignoreCase = true) ||
                     file.extension.equals("war", ignoreCase = true) ||
                     file.extension.equals("apk", ignoreCase = true)) &&
                    !file.name.endsWith("-sources.jar", ignoreCase = true) &&
                    !file.name.endsWith("-javadoc.jar", ignoreCase = true) &&
                    !file.name.startsWith("original-", ignoreCase = true)
        }.toList()

        if (artifacts.isEmpty()) {
            if (!success) {
                _events.emit(
                    BuildOutputEvent.LogLine(
                        if (isRu) "Подсказка: Проверьте логи сборки выше и настройки pom.xml. Убедитесь, что все зависимости и плагины Maven доступны."
                        else "Hint: Check build logs above and pom.xml settings. Ensure all dependencies and Maven plugins are accessible.",
                        isError = true
                    )
                )
            }
            return null
        }

        return artifacts.maxByOrNull { it.lastModified() }
    }

    private suspend fun buildGradle(
        project: Project,
        config: BuildConfiguration,
        ndk: NdkVersion?,
        context: Context? = null
    ): File? {
        val isRu = java.util.Locale.getDefault().language == "ru"

        val effectiveNdk = ndk ?: DefaultNdkCatalog.AVAILABLE_VERSIONS.firstOrNull {
            it.getEffectiveNdkDir()?.exists() == true
        }

        // Ensure JDK runtime dependencies (libz.so.1, libc++_shared.so, etc.) and auto-install if missing
        var javaEnv = BuildToolInstaller.detectJavaEnvironment(context, project.rootDir, config.javaHome)
        if (!javaEnv.isAvailable && config.javaHome.isBlank() && !ProjectDetector.isJavaAvailable(context, project.rootDir)) {
            if (context != null) {
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "ℹ Java JDK не найден. Автоматическая загрузка автономного OpenJDK 17 LTS..."
                    else "ℹ Java JDK not found. Automatically downloading standalone OpenJDK 17 LTS..."
                ))
                var lastJdkPct = -1
                val jdkInstalled = BuildToolInstaller.installJdk(context) { status, pct ->
                    val step = (pct / 25f).toInt() * 25
                    if (step != lastJdkPct || pct >= 99f || pct == 0f || status.startsWith("✔") || status.startsWith("✖")) {
                        lastJdkPct = step
                        _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                    }
                }
                if (jdkInstalled) {
                    javaEnv = BuildToolInstaller.detectJavaEnvironment(context, project.rootDir, config.javaHome)
                    _events.emit(BuildOutputEvent.LogLine(
                        if (isRu) "✔ OpenJDK 17 успешно установлен во внутреннее хранилище PrismDE!"
                        else "✔ OpenJDK 17 installed successfully into PrismDE internal storage!"
                    ))
                }
            }
        }
        if (context != null) {
            BuildToolInstaller.ensureJdkRuntimeLibraries(context)
            // Ensure Gradle Wrapper and Android-optimized gradle.properties are in place
            BuildToolInstaller.ensureGradleWrapper(context, project.rootDir)

            // Ensure Android SDK directory & licenses are created in PrismDE storage
            val sdkDir = BuildToolInstaller.ensureAndroidSdk(context)

            // Ensure NDK has source.properties and permissions!
            effectiveNdk?.let {
                it.ensureSourceProperties(context = context)
                it.ensurePermissions(context)
            }

            // Ensure local.properties in project root has sdk.dir and ndk.dir
            BuildToolInstaller.ensureLocalProperties(project.rootDir, sdkDir, effectiveNdk?.getEffectiveNdkDir(), context)

            // Ensure project build scripts do not reference unsupported ABIs (e.g. armeabi) and specify ndkVersion
            BuildToolInstaller.ensureProjectAbiFilters(
                project.rootDir,
                config.selectedAbi.abiString,
                effectiveNdk?.getPkgRevision() ?: "26.2.11394342"
            )

            // Auto-install Android SDK Platform 34 (android.jar) if missing!
            if (!BuildToolInstaller.isAndroidPlatformInstalled(context, 34)) {
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "ℹ Android SDK Platform 34 (android.jar) не найден. Автоматическая загрузка (~58 МБ)..."
                    else "ℹ Android SDK Platform 34 (android.jar) not found. Automatically downloading (~58 MB)..."
                ))
                var lastPlatformPct = -1
                val platformInstalled = BuildToolInstaller.installAndroidPlatform(context, 34) { status, pct ->
                    val step = (pct / 25f).toInt() * 25
                    if (step != lastPlatformPct || pct >= 99f || pct == 0f || status.startsWith("✔") || status.startsWith("✖") || status.startsWith("Распаковка") || status.startsWith("Extracting")) {
                        lastPlatformPct = step
                        _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                    }
                }
                if (platformInstalled) {
                    _events.emit(BuildOutputEvent.LogLine(
                        if (isRu) "✔ Android SDK Platform 34 успешно установлен!"
                        else "✔ Android SDK Platform 34 installed successfully!"
                    ))
                }
            }

            // Auto-install Android Build-Tools 34.0.0 if missing!
            if (!BuildToolInstaller.isAndroidBuildToolsInstalled(context, "34.0.0")) {
                _events.emit(BuildOutputEvent.LogLine(
                    if (isRu) "ℹ Android Build-Tools 34.0.0 не найдены. Автоматическая загрузка (~55 МБ)..."
                    else "ℹ Android Build-Tools 34.0.0 not found. Automatically downloading (~55 MB)..."
                ))
                var lastBtPct = -1
                val btInstalled = BuildToolInstaller.installBuildTools(context, "34.0.0") { status, pct ->
                    val step = (pct / 25f).toInt() * 25
                    if (step != lastBtPct || pct >= 99f || pct == 0f || status.startsWith("✔") || status.startsWith("✖") || status.startsWith("Распаковка") || status.startsWith("Extracting")) {
                        lastBtPct = step
                        _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                    }
                }
                if (btInstalled) {
                    _events.emit(BuildOutputEvent.LogLine(
                        if (isRu) "✔ Android Build-Tools 34.0.0 успешно установлены!"
                        else "✔ Android Build-Tools 34.0.0 installed successfully!"
                    ))
                }
            }
        }

        // 1. Detect Gradle Wrapper (gradlew / gradlew.bat) or installed Gradle binary
        val gradlewFile = File(project.rootDir, "gradlew")
        val gradlewBat = File(project.rootDir, "gradlew.bat")
        val internalGradle = if (context != null) BuildToolInstaller.getGradleExecutable(context) else null

        val (executableCmd, workingDir, extraBinDir) = when {
            gradlewFile.exists() -> {
                try {
                    gradlewFile.setExecutable(true, false)
                    val txt = gradlewFile.readText()
                    if (txt.contains("\r\n")) {
                        gradlewFile.writeText(txt.replace("\r\n", "\n"))
                    }
                } catch (_: Throwable) {}
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется Gradle Wrapper (./gradlew)..." else "Using Gradle Wrapper (./gradlew)..."))
                Triple(listOf("/system/bin/sh", gradlewFile.absolutePath), project.rootDir, null)
            }
            gradlewBat.exists() && System.getProperty("os.name")?.lowercase()?.contains("windows") == true -> {
                _events.emit(BuildOutputEvent.LogLine("Using Gradle Wrapper (gradlew.bat)..."))
                Triple(listOf("cmd.exe", "/c", gradlewBat.absolutePath), project.rootDir, null)
            }
            internalGradle != null && internalGradle.exists() -> {
                _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется встроенный Gradle: ${internalGradle.absolutePath}" else "Using internal Gradle: ${internalGradle.absolutePath}"))
                Triple(listOf("/system/bin/sh", internalGradle.absolutePath), project.rootDir, internalGradle.parentFile)
            }
            else -> {
                // Search installed gradle binary in Termux / system paths
                val candidatePaths = listOf(
                    "/data/data/com.termux/files/usr/bin/gradle",
                    "/data/user/0/com.termux/files/usr/bin/gradle",
                    "/data/data/com.termux/files/usr/share/gradle/bin/gradle",
                    "/system/bin/gradle",
                    "/system/xbin/gradle"
                )
                val found = candidatePaths.map { File(it) }.firstOrNull { it.exists() }
                if (found != null) {
                    try { found.setExecutable(true, false) } catch (_: Throwable) {}
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "Используется установленный Gradle: ${found.absolutePath}" else "Using installed Gradle: ${found.absolutePath}"))
                    Triple(listOf("/system/bin/sh", found.absolutePath), project.rootDir, found.parentFile)
                } else if (context != null) {
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "ℹ Gradle не найден. Запуск автоматической установки Gradle ${BuildToolInstaller.GRADLE_VERSION}..." else "ℹ Gradle not found. Starting automatic installation of Gradle ${BuildToolInstaller.GRADLE_VERSION}..."))
                    val installed = BuildToolInstaller.installGradle(context) { status, pct ->
                        if (pct == 10f || pct == 75f || pct == 100f) {
                            _events.tryEmit(BuildOutputEvent.LogLine("  → $status"))
                        }
                    }
                    val newlyInstalledGradle = BuildToolInstaller.getGradleExecutable(context)
                    if (installed && newlyInstalledGradle != null) {
                        _events.emit(BuildOutputEvent.LogLine(if (isRu) "✔ Gradle успешно установлен: ${newlyInstalledGradle.absolutePath}" else "✔ Gradle installed successfully: ${newlyInstalledGradle.absolutePath}"))
                        Triple(listOf("/system/bin/sh", newlyInstalledGradle.absolutePath), project.rootDir, newlyInstalledGradle.parentFile)
                    } else {
                        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Поиск gradle в системном PATH..." else "Searching for gradle in system PATH..."))
                        Triple(listOf("gradle"), project.rootDir, null)
                    }
                } else {
                    _events.emit(BuildOutputEvent.LogLine(if (isRu) "Поиск gradle в системном PATH..." else "Searching for gradle in system PATH..."))
                    Triple(listOf("gradle"), project.rootDir, null)
                }
            }
        }

        val tasks = config.gradleTasks.split(" ").filter { it.isNotBlank() }.ifEmpty { listOf("assembleDebug") }
        val flags = config.gradleCustomFlags.split(" ").filter { it.isNotBlank() }

        val command = mutableListOf<String>()
        command.addAll(executableCmd)
        command.addAll(tasks)
        command.addAll(flags)

        // Avoid daemon background issues in constrained mobile containers if not already specified
        if (!command.contains("--no-daemon") && !command.contains("--daemon")) {
            command.add("--no-daemon")
        }
        if (!command.contains("--stacktrace")) {
            command.add("--stacktrace")
        }
        // Force plain console output to eliminate interactive animation / carriage return spam
        if (!command.contains("--console")) {
            command.add("--console=plain")
        }

        // Inject target ABI and NDK version so AGP only configures and compiles for the device architecture (e.g. arm64-v8a)
        val targetAbi = config.selectedAbi.abiString
        if (targetAbi.isNotBlank() && !command.any { it.startsWith("-Pandroid.injected.build.abi") }) {
            command.add("-Pandroid.injected.build.abi=$targetAbi")
        }
        val ndkRev = effectiveNdk?.getPkgRevision() ?: "26.2.11394342"
        if (!command.any { it.startsWith("-Pandroid.ndkVersion") }) {
            command.add("-Pandroid.ndkVersion=$ndkRev")
        }

        _events.emit(BuildOutputEvent.LogLine(if (isRu) "Запуск команды Gradle:" else "Executing Gradle command:"))
        _events.emit(BuildOutputEvent.LogLine(command.joinToString(" ")))

        val success = executeProcess(command, workingDir, extraBinDir, effectiveNdk, config, context)

        // Search for generated APK, AAR, or JAR in build outputs
        val artifacts = project.rootDir.walkTopDown().maxDepth(6).filter { file ->
            file.isFile &&
                    (file.extension.equals("apk", ignoreCase = true) ||
                     file.extension.equals("aar", ignoreCase = true) ||
                     file.extension.equals("jar", ignoreCase = true)) &&
                    file.path.contains("build", ignoreCase = true) &&
                    !file.name.endsWith("-unaligned.apk", ignoreCase = true) &&
                    !file.name.endsWith("-sources.jar", ignoreCase = true)
        }.toList()

        if (artifacts.isEmpty()) {
            if (!success) {
                _events.emit(
                    BuildOutputEvent.LogLine(
                        if (isRu) "Подсказка: Для сборки Gradle убедитесь, что в системе установлен JDK (openjdk-17) и Android SDK, либо настроен gradlew."
                        else "Hint: For Gradle builds, ensure JDK (openjdk-17) and Android SDK are installed, or gradlew is properly configured.",
                        isError = true
                    )
                )
            }
            return null
        }

        // Return generated APK if available, or newest artifact
        return artifacts.filter { it.extension.equals("apk", ignoreCase = true) }.maxByOrNull { it.lastModified() }
            ?: artifacts.maxByOrNull { it.lastModified() }
    }

    private fun parseLocalModule(file: File): String? {
        if (!file.exists()) return null
        return try {
            for (line in file.readLines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("LOCAL_MODULE") && (trimmed.contains(":=") || trimmed.contains("="))) {
                    val raw = trimmed.substringAfter("=").trim()
                    if (raw.isNotBlank() && !raw.startsWith("libcurl") && !raw.startsWith("libssl") && !raw.startsWith("libcrypto")) {
                        return raw
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun executeProcess(
        command: List<String>,
        workingDir: File,
        extraBinDir: File? = null,
        ndk: NdkVersion? = null,
        config: BuildConfiguration? = null,
        context: Context? = null
    ): Boolean {
        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)

            val env = processBuilder.environment()
            val existingPath = env["PATH"] ?: "/system/bin"
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val pathEntries = mutableListOf<String>()
            if (extraBinDir != null && extraBinDir.exists()) {
                pathEntries.add(extraBinDir.absolutePath)
            }
            if (context != null) {
                for (binDir in BuildToolInstaller.getToolsBinDirs(context)) {
                    if (binDir.exists() && !pathEntries.contains(binDir.absolutePath)) {
                        pathEntries.add(binDir.absolutePath)
                    }
                }
            }
            if (File(termuxBin).exists()) {
                pathEntries.add(termuxBin)
            }

            // Add NDK toolchain bin directories to PATH so ndk-build, make, clang are directly accessible
            ndk?.getEffectiveNdkDir()?.let { ndkDir ->
                if (ndkDir.exists()) {
                    val ndkBinDirs = listOf(
                        File(ndkDir, "prebuilt/linux-arm64/bin"),
                        File(ndkDir, "prebuilt/linux-aarch64/bin"),
                        File(ndkDir, "toolchains/llvm/prebuilt/linux-arm64/bin"),
                        File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/bin"),
                        File(ndkDir, "bin"),
                        ndkDir
                    )
                    for (b in ndkBinDirs) {
                        if (b.exists() && b.isDirectory && !pathEntries.contains(b.absolutePath)) {
                            pathEntries.add(b.absolutePath)
                        }
                    }
                }
            }

            // Auto-detect or use configured JAVA_HOME
            val configuredJava = if (config?.javaHome?.isNotBlank() == true) File(config.javaHome) else null
            val effectiveJavaHome = if (configuredJava != null && configuredJava.exists()) {
                configuredJava
            } else {
                ProjectDetector.findJavaHome(context, workingDir)
                    ?: BuildToolInstaller.detectJavaEnvironment(context, workingDir).javaHome
            }
            if (effectiveJavaHome != null && effectiveJavaHome.exists()) {
                env["JAVA_HOME"] = effectiveJavaHome.absolutePath
                val jvmBin = File(effectiveJavaHome, "bin")
                if (jvmBin.exists()) {
                    pathEntries.add(0, jvmBin.absolutePath)
                }
            }

            if (pathEntries.isNotEmpty()) {
                env["PATH"] = pathEntries.joinToString(":") + ":$existingPath"
            }

            // Auto-detect ANDROID_HOME / ANDROID_SDK_ROOT
            if (env["ANDROID_HOME"].isNullOrBlank() && env["ANDROID_SDK_ROOT"].isNullOrBlank()) {
                val localProps = File(workingDir, "local.properties")
                if (localProps.exists()) {
                    try {
                        for (line in localProps.readLines()) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("sdk.dir=")) {
                                val path = trimmed.substringAfter("sdk.dir=").replace("\\:", ":").replace("\\\\", "/")
                                if (File(path).exists()) {
                                    env["ANDROID_HOME"] = path
                                    env["ANDROID_SDK_ROOT"] = path
                                    break
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
                if (env["ANDROID_HOME"].isNullOrBlank()) {
                    val sdkDir = if (context != null) BuildToolInstaller.findExistingSdk(context) else null
                    if (sdkDir != null && sdkDir.exists()) {
                        env["ANDROID_HOME"] = sdkDir.absolutePath
                        env["ANDROID_SDK_ROOT"] = sdkDir.absolutePath
                    }
                }
            }

            // Provide active NDK location to Gradle / CMake
            ndk?.getEffectiveNdkDir()?.let { ndkDir ->
                if (ndkDir.exists()) {
                    env["ANDROID_NDK_HOME"] = ndkDir.absolutePath
                    env["ANDROID_NDK_ROOT"] = ndkDir.absolutePath
                    env["NDK_HOME"] = ndkDir.absolutePath
                }
            }

            val tempDir = if (context != null) {
                File(context.cacheDir, "prism_tmp").also { it.mkdirs() }
            } else {
                File(workingDir, ".prism_tmp").also { it.mkdirs() }
            }
            try {
                tempDir.setReadable(true, false)
                tempDir.setWritable(true, false)
                tempDir.setExecutable(true, false)
            } catch (_: Throwable) {}
            env["TMPDIR"] = tempDir.absolutePath
            env["NDK_ANDROID_TMPDIR"] = tempDir.absolutePath
            env["TEMP"] = tempDir.absolutePath
            // Configure private app HOME and local repository directory
            val homeDir = if (context != null) {
                File(context.filesDir, "home").also { it.mkdirs() }
            } else {
                File(workingDir, ".prism_home").also { it.mkdirs() }
            }
            val m2Dir = File(homeDir, ".m2").also { it.mkdirs() }
            val m2RepoDir = File(m2Dir, "repository").also { it.mkdirs() }
            val gradleHomeDir = File(homeDir, ".gradle").also { it.mkdirs() }
            try {
                homeDir.setReadable(true, false)
                homeDir.setWritable(true, false)
                homeDir.setExecutable(true, false)
                m2Dir.setReadable(true, false)
                m2Dir.setWritable(true, false)
                m2Dir.setExecutable(true, false)
                m2RepoDir.setReadable(true, false)
                m2RepoDir.setWritable(true, false)
                m2RepoDir.setExecutable(true, false)
                gradleHomeDir.setReadable(true, false)
                gradleHomeDir.setWritable(true, false)
                gradleHomeDir.setExecutable(true, false)
            } catch (_: Throwable) {}

            env["HOME"] = homeDir.absolutePath
            env["USERPROFILE"] = homeDir.absolutePath
            env["GRADLE_USER_HOME"] = gradleHomeDir.absolutePath

            val mvnHome = context?.let { BuildToolInstaller.getMavenHomeDir(it) }
            if (mvnHome != null && mvnHome.exists()) {
                env["M2_HOME"] = mvnHome.absolutePath
                env["MAVEN_HOME"] = mvnHome.absolutePath
                val settingsXml = File(mvnHome, "conf/settings.xml")
                if (settingsXml.exists()) {
                    try {
                        val content = settingsXml.readText()
                        if (!content.contains("<localRepository>${m2RepoDir.absolutePath}</localRepository>")) {
                            val patched = if (content.contains("<localRepository>")) {
                                content.replace(Regex("<localRepository>.*?</localRepository>"), "<localRepository>${m2RepoDir.absolutePath}</localRepository>")
                            } else {
                                content.replace("<settings", "<settings>\n  <localRepository>${m2RepoDir.absolutePath}</localRepository>")
                            }
                            settingsXml.writeText(patched)
                        }
                    } catch (_: Throwable) {}
                }
            }

            val jvmOpts = "-Duser.home=${homeDir.absolutePath} -Dmaven.repo.local=${m2RepoDir.absolutePath} -Dlibrary.jansi.path= -Djansi.native=false -Djansi.mode=strip -Djansi.passthrough=true -Dstyle.color=never -Dmaven.color=false -Djava.io.tmpdir=${tempDir.absolutePath} -Djansi.tmpdir=${tempDir.absolutePath} -XX:-UseCompressedOops -XX:-UseCompressedClassPointers"
            env["JAVA_TOOL_OPTIONS"] = jvmOpts
            env["MAVEN_OPTS"] = jvmOpts
            env["MAVEN_ARGS"] = "-Duser.home=${homeDir.absolutePath} -Dmaven.repo.local=${m2RepoDir.absolutePath}"
            env["GRADLE_OPTS"] = "-Dorg.gradle.daemon=false -Duser.home=${homeDir.absolutePath} -Djava.io.tmpdir=${tempDir.absolutePath} -XX:-UseCompressedOops -XX:-UseCompressedClassPointers"
            env["MALLOC_CHECK_"] = "0"
            env["SCUDO_OPTIONS"] = "DeallocationTypeMismatch=false:DeleteSizeMismatch=false:QuarantineSizeKb=0"

            // Configure LD_LIBRARY_PATH so child processes (java, ndk-build, clang) locate their shared libraries
            val jdkHome = effectiveJavaHome ?: (context?.let { BuildToolInstaller.getJdkHomeDir(it) })
            val ldPaths = mutableListOf<String>()
            if (jdkHome != null && jdkHome.exists()) {
                val jdkLib = File(jdkHome, "lib")
                val jdkServer = File(jdkLib, "server")
                val jdkJli = File(jdkLib, "jli")
                if (jdkLib.exists()) ldPaths.add(jdkLib.absolutePath)
                if (jdkServer.exists()) ldPaths.add(jdkServer.absolutePath)
                if (jdkJli.exists()) ldPaths.add(jdkJli.absolutePath)
            }
            ndk?.getEffectiveNdkDir()?.let { ndkDir ->
                val ndkLibDirs = listOf(
                    File(ndkDir, "toolchains/llvm/prebuilt/linux-arm64/lib64"),
                    File(ndkDir, "toolchains/llvm/prebuilt/linux-arm64/lib"),
                    File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/lib64"),
                    File(ndkDir, "toolchains/llvm/prebuilt/linux-aarch64/lib"),
                    File(ndkDir, "lib")
                )
                for (libDir in ndkLibDirs) {
                    if (libDir.exists() && libDir.isDirectory) {
                        ldPaths.add(libDir.absolutePath)
                    }
                }
            }
            extraBinDir?.let { binDir ->
                val parentLib = File(binDir.parentFile, "lib")
                if (parentLib.exists()) ldPaths.add(parentLib.absolutePath)
            }
            context?.applicationInfo?.nativeLibraryDir?.let { appLibDir ->
                if (File(appLibDir).exists()) ldPaths.add(appLibDir)
            }
            val currentLd = env["LD_LIBRARY_PATH"] ?: ""
            if (currentLd.isNotBlank()) ldPaths.add(currentLd)
            ldPaths.add("/system/lib64")
            ldPaths.add("/vendor/lib64")
            ldPaths.add("/apex/com.android.runtime/lib64/bionic")
            env["LD_LIBRARY_PATH"] = ldPaths.distinct().joinToString(":")

            val process = processBuilder.start()

            val ansiRegex = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")
            var lastStdoutWasBlank = false

            // Stream stdout and parse diagnostics (e.g. Maven, Gradle, Javac output to stdout)
            val stdoutThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { raw ->
                            val clean = raw.replace(ansiRegex, "").trimEnd('\r')
                            val trimmed = clean.trim()
                            // Filter out Gradle dynamic progress bar animation noise
                            if (trimmed.startsWith("<") && (trimmed.contains("%") || trimmed.contains("====") || trimmed.contains("----"))) {
                                return@let
                            }
                            if (trimmed.isEmpty()) {
                                if (lastStdoutWasBlank) return@let
                                lastStdoutWasBlank = true
                            } else {
                                lastStdoutWasBlank = false
                            }

                            _events.tryEmit(BuildOutputEvent.LogLine(clean))
                            val diag = parser.parseLine(clean)
                            if (diag != null) {
                                _events.tryEmit(BuildOutputEvent.DiagnosticFound(diag))
                            }
                        }
                    }
                }
            }

            var lastStderrWasBlank = false
            // Stream stderr and parse diagnostics
            val stderrThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { raw ->
                            val clean = raw.replace(ansiRegex, "").trimEnd('\r')
                            val trimmed = clean.trim()
                            if (trimmed.startsWith("<") && (trimmed.contains("%") || trimmed.contains("====") || trimmed.contains("----"))) {
                                return@let
                            }
                            if (trimmed.isEmpty()) {
                                if (lastStderrWasBlank) return@let
                                lastStderrWasBlank = true
                            } else {
                                lastStderrWasBlank = false
                            }

                            _events.tryEmit(BuildOutputEvent.LogLine(clean, isError = true))
                            val diag = parser.parseLine(clean)
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
