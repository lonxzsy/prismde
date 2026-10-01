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
        get() = File(rootDir, "jni")

    val hasJniDir: Boolean
        get() = jniDir.exists() && jniDir.isDirectory

    val hasCMakeLists: Boolean
        get() = File(rootDir, "CMakeLists.txt").exists() || File(jniDir, "CMakeLists.txt").exists()

    val hasAndroidMk: Boolean
        get() = File(jniDir, "Android.mk").exists()
}
