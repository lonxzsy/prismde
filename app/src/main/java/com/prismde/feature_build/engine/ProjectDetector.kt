package com.prismde.feature_build.engine

import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import java.io.File

object ProjectDetector {

    val COMPILABLE_EXTENSIONS = setOf("c", "cpp", "cc", "cxx", "c++", "cp", "s", "S", "java", "kt")
    val HEADER_EXTENSIONS = setOf("h", "hpp", "hxx", "hh", "inc", "inl")
    val IGNORED_DIRS = setOf("build", ".git", ".gradle", "libs", "obj", "bin", ".idea", "target", ".mvn")

    fun detect(rootDir: File): ProjectType {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return ProjectType.SINGLE_FILE_EXECUTABLE
        }

        // 1. Maven project detection
        val hasPom = File(rootDir, "pom.xml").exists() || File(rootDir, "mvnw").exists() || File(rootDir, "mvnw.cmd").exists()
        if (hasPom) {
            return ProjectType.MAVEN
        }

        // 2. Gradle project detection
        val hasGradle = File(rootDir, "build.gradle").exists() ||
                File(rootDir, "build.gradle.kts").exists() ||
                File(rootDir, "settings.gradle").exists() ||
                File(rootDir, "settings.gradle.kts").exists() ||
                File(rootDir, "gradlew").exists() ||
                File(rootDir, "gradlew.bat").exists()
        if (hasGradle) {
            return ProjectType.GRADLE
        }

        val isJniNamed = rootDir.name.equals("jni", ignoreCase = true)
        val candidateJniDirs = listOf(
            if (isJniNamed) rootDir else File(rootDir, "jni"),
            File(rootDir, "app/src/main/jni"),
            File(rootDir, "src/main/jni"),
            File(rootDir, "app/src/main/cpp"),
            File(rootDir, "src/main/cpp"),
            File(rootDir, "cpp")
        )
        val effectiveJniDir = candidateJniDirs.firstOrNull { it.exists() && it.isDirectory }
        val hasJni = effectiveJniDir != null

        val hasRootCMake = File(rootDir, "CMakeLists.txt").exists() || (hasJni && File(effectiveJniDir, "CMakeLists.txt").exists())
        val hasAndroidMk = File(rootDir, "Android.mk").exists() || (hasJni && File(effectiveJniDir, "Android.mk").exists()) || File(rootDir, "Application.mk").exists()

        // Pure JNI project case: has Android.mk, or is/contains jni/ folder
        if (hasAndroidMk || hasJni) {
            val jniFiles = effectiveJniDir?.listFiles() ?: emptyArray()
            val hasSources = jniFiles.any {
                it.extension.lowercase() in COMPILABLE_EXTENSIONS ||
                        it.name == "Android.mk" ||
                        it.name == "CMakeLists.txt"
            }
            if (hasSources || hasAndroidMk) {
                return ProjectType.PURE_JNI_SO
            }
        }

        if (hasRootCMake) {
            return ProjectType.CMAKE
        }

        return ProjectType.SINGLE_FILE_EXECUTABLE
    }

    fun findSourceFiles(project: Project): List<File> {
        val root = project.rootDir
        val sources = mutableListOf<File>()

        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (file.name.lowercase() !in IGNORED_DIRS) {
                        scan(file)
                    }
                } else {
                    if (file.extension.lowercase() in COMPILABLE_EXTENSIONS) {
                        sources.add(file)
                    }
                }
            }
        }

        scan(root)
        return sources
    }

    fun findIncludeDirectories(project: Project): List<File> {
        val root = project.rootDir
        val includeDirs = linkedSetOf<File>()
        includeDirs.add(root)
        if (project.hasJniDir) {
            includeDirs.add(project.jniDir)
        }

        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            var hasHeaders = false
            for (file in files) {
                if (file.isDirectory) {
                    if (file.name.lowercase() !in IGNORED_DIRS) {
                        scan(file)
                    }
                } else if (file.extension.lowercase() in HEADER_EXTENSIONS) {
                    hasHeaders = true
                }
            }
            if (hasHeaders) {
                includeDirs.add(dir)
            }
        }

        scan(root)
        return includeDirs.toList()
    }
}
