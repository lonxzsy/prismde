package com.prismde.feature_build.engine

import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import java.io.File

object ProjectDetector {

    fun detect(rootDir: File): ProjectType {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return ProjectType.SINGLE_FILE_EXECUTABLE
        }

        val jniDir = File(rootDir, "jni")
        val hasJni = jniDir.exists() && jniDir.isDirectory
        val hasRootGradle = File(rootDir, "build.gradle").exists() || File(rootDir, "build.gradle.kts").exists()
        val hasRootCMake = File(rootDir, "CMakeLists.txt").exists()

        // Pure JNI project case: has jni/ folder and no Android Studio / Gradle wrapper at root
        if (hasJni && !hasRootGradle) {
            val jniFiles = jniDir.listFiles() ?: emptyArray()
            val hasSources = jniFiles.any {
                it.extension.lowercase() in listOf("c", "cpp", "cc", "cxx") ||
                        it.name == "Android.mk" ||
                        it.name == "CMakeLists.txt"
            }
            if (hasSources) {
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
                    if (file.name !in listOf("build", ".git", ".gradle", "libs", "obj")) {
                        scan(file)
                    }
                } else {
                    if (file.extension.lowercase() in listOf("c", "cpp", "cc", "cxx", "h", "hpp")) {
                        sources.add(file)
                    }
                }
            }
        }

        scan(root)
        return sources
    }
}
