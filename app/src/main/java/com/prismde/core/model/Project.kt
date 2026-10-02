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
        get() = if (rootDir.name.equals("jni", ignoreCase = true)) rootDir else File(rootDir, "jni")

    val hasJniDir: Boolean
        get() = rootDir.name.equals("jni", ignoreCase = true) || (File(rootDir, "jni").exists() && File(rootDir, "jni").isDirectory)

    val hasCMakeLists: Boolean
        get() = File(rootDir, "CMakeLists.txt").exists() || File(jniDir, "CMakeLists.txt").exists()

    val hasAndroidMk: Boolean
        get() = File(jniDir, "Android.mk").exists() || File(rootDir, "Android.mk").exists()
}
