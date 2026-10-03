package com.prismde.feature_build.engine

import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import java.io.File

object ProjectDetector {

    val COMPILABLE_EXTENSIONS = setOf("c", "cpp", "cc", "cxx", "c++", "cp", "s", "S", "java", "kt")
    val HEADER_EXTENSIONS = setOf("h", "hpp", "hxx", "hh", "inc", "inl")
    val IGNORED_DIRS = setOf("build", ".git", ".gradle", "libs", "obj", "bin", ".idea", "target", ".mvn")

    fun findJavaHome(workingDir: File? = null): File? {
        if (workingDir != null) {
            val lp = File(workingDir, "local.properties")
            if (lp.exists()) {
                try {
                    for (line in lp.readLines()) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("org.gradle.java.home=") || trimmed.startsWith("java.home=")) {
                            val path = trimmed.substringAfter("=").replace("\\:", ":").replace("\\\\", "/")
                            val f = File(path)
                            if (f.exists() && f.isDirectory) return f
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        val envJava = System.getenv("JAVA_HOME")
        if (!envJava.isNullOrBlank()) {
            val f = File(envJava)
            if (f.exists() && f.isDirectory) return f
        }

        val candidates = listOf(
            File("/data/data/com.termux/files/usr/lib/jvm/openjdk-17"),
            File("/data/data/com.termux/files/usr/lib/jvm/openjdk-21"),
            File("/data/data/com.termux/files/usr/lib/jvm/default-jvm"),
            File("/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk"),
            File("/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk"),
            File("/data/user/0/com.termux/files/usr/lib/jvm/openjdk-17"),
            File("/data/user/0/com.termux/files/usr/lib/jvm/default-jvm"),
            File("/data/data/com.itsaky.androidide/files/usr/lib/jvm/openjdk-17"),
            File("/data/user/0/com.itsaky.androidide/files/usr/lib/jvm/openjdk-17")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    fun isJavaAvailable(workingDir: File? = null): Boolean {
        if (findJavaHome(workingDir) != null) return true

        val binCandidates = listOf(
            File("/data/data/com.termux/files/usr/bin/java"),
            File("/data/user/0/com.termux/files/usr/bin/java"),
            File("/data/data/com.itsaky.androidide/files/usr/bin/java"),
            File("/system/bin/java"),
            File("/system/xbin/java")
        )
        if (binCandidates.any { it.exists() }) return true

        val path = System.getenv("PATH") ?: ""
        for (dir in path.split(File.pathSeparator)) {
            val exeName = if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) "java.exe" else "java"
            val exe = File(dir, exeName)
            if (exe.exists()) return true
        }
        return false
    }

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

        // If Gradle is detected AND Java is available on host device -> GRADLE.
        // If Java is not available on host device, but project has native C++/NDK code -> default to PURE_JNI_SO so user can build .so immediately without JAVA_HOME error!
        val javaAvailable = isJavaAvailable(rootDir)
        if (hasGradle && javaAvailable) {
            return ProjectType.GRADLE
        }

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

        if (hasGradle) {
            return ProjectType.GRADLE
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
