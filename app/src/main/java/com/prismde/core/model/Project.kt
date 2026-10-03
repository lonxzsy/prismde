package com.prismde.core.model

import java.io.File

data class Project(
    val name: String,
    val rootPath: String,
    val id: String = rootPath,
    val detectedType: ProjectType = ProjectType.AUTO_DETECT,
    val buildConfiguration: BuildConfiguration = BuildConfiguration()
) {
    val rootDir: File
        get() = File(rootPath)

    val jniDir: File
        get() {
            if (rootDir.name.equals("jni", ignoreCase = true)) return rootDir
            val candidates = listOf(
                File(rootDir, "jni"),
                File(rootDir, "app/src/main/jni"),
                File(rootDir, "src/main/jni"),
                File(rootDir, "app/src/main/cpp"),
                File(rootDir, "src/main/cpp"),
                File(rootDir, "cpp")
            )
            return candidates.firstOrNull { it.exists() && it.isDirectory } ?: File(rootDir, "jni")
        }

    val hasJniDir: Boolean
        get() = rootDir.name.equals("jni", ignoreCase = true) ||
                File(rootDir, "jni").isDirectory ||
                File(rootDir, "app/src/main/jni").isDirectory ||
                File(rootDir, "src/main/jni").isDirectory ||
                File(rootDir, "app/src/main/cpp").isDirectory ||
                File(rootDir, "src/main/cpp").isDirectory ||
                File(rootDir, "cpp").isDirectory

    val hasCMakeLists: Boolean
        get() = File(rootDir, "CMakeLists.txt").exists() ||
                File(jniDir, "CMakeLists.txt").exists() ||
                File(rootDir, "app/src/main/cpp/CMakeLists.txt").exists() ||
                File(rootDir, "src/main/cpp/CMakeLists.txt").exists()

    val hasAndroidMk: Boolean
        get() = File(jniDir, "Android.mk").exists() ||
                File(rootDir, "Android.mk").exists() ||
                File(rootDir, "app/src/main/jni/Android.mk").exists() ||
                File(rootDir, "src/main/jni/Android.mk").exists()
}
