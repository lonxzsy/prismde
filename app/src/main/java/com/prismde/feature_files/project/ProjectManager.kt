package com.prismde.feature_files.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import com.prismde.feature_build.engine.ProjectDetector
import java.io.File
import java.io.FileOutputStream

object ProjectManager {

    fun getProjectsDir(context: Context): File {
        return File(context.filesDir, "projects").also { it.mkdirs() }
    }

    fun listProjects(context: Context): List<Project> {
        val root = getProjectsDir(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()

        return dirs.map { dir ->
            val detected = ProjectDetector.detect(dir)
            Project(
                name = dir.name,
                rootPath = dir.absolutePath,
                detectedType = detected
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun createProject(context: Context, name: String, type: ProjectType): Project {
        val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val finalName = safeName.ifBlank { "my_ndk_project" }

        val root = getProjectsDir(context)
        var projectDir = File(root, finalName)
        var suffix = 1
        while (projectDir.exists()) {
            projectDir = File(root, "${finalName}_$suffix")
            suffix++
        }
        projectDir.mkdirs()

        when (type) {
            ProjectType.PURE_JNI_SO -> {
                val jniDir = File(projectDir, "jni").also { it.mkdirs() }

                File(jniDir, "Android.mk").writeText(
                    """
                    LOCAL_PATH := $(call my-dir)

                    include $(CLEAR_VARS)
                    LOCAL_MODULE    := native-lib
                    LOCAL_SRC_FILES := native-lib.cpp
                    LOCAL_LDLIBS    := -llog -landroid

                    include $(BUILD_SHARED_LIBRARY)
                    """.trimIndent()
                )

                File(jniDir, "Application.mk").writeText(
                    """
                    APP_ABI := arm64-v8a armeabi-v7a
                    APP_PLATFORM := android-24
                    APP_STL := c++_shared
                    """.trimIndent()
                )

                File(jniDir, "native-lib.cpp").writeText(
                    """
                    #include <jni.h>
                    #include <string>
                    #include <android/log.h>

                    #define LOG_TAG "PrismNative"
                    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

                    extern "C" JNIEXPORT jstring JNICALL
                    Java_com_example_MainActivity_stringFromJNI(
                            JNIEnv* env,
                            jobject /* this */) {
                        std::string hello = "Hello from PrismDE C++!";
                        LOGI("stringFromJNI called successfully");
                        return env->NewStringUTF(hello.c_str());
                    }
                    """.trimIndent()
                )
            }

            ProjectType.CMAKE -> {
                File(projectDir, "CMakeLists.txt").writeText(
                    """
                    cmake_minimum_required(VERSION 3.22.1)
                    project(prism_project CXX)

                    set(CMAKE_CXX_STANDARD 17)

                    add_library(native-lib SHARED
                        native-lib.cpp
                    )

                    find_library(log-lib log)
                    target_link_libraries(native-lib ${'$'}{log-lib})
                    """.trimIndent()
                )

                File(projectDir, "native-lib.cpp").writeText(
                    """
                    #include <iostream>

                    extern "C" void hello() {
                        std::cout << "Hello from CMake project!" << std::endl;
                    }
                    """.trimIndent()
                )
            }

            ProjectType.SINGLE_FILE_EXECUTABLE, ProjectType.AUTO_DETECT -> {
                File(projectDir, "main.cpp").writeText(
                    """
                    #include <iostream>

                    int main() {
                        std::cout << "Hello, World from PrismDE!" << std::endl;
                        return 0;
                    }
                    """.trimIndent()
                )
            }
        }

        return Project(
            name = projectDir.name,
            rootPath = projectDir.absolutePath,
            detectedType = ProjectDetector.detect(projectDir)
        )
    }

    fun importProjectFromFolder(folder: File): Project? {
        if (!folder.exists() || !folder.isDirectory) return null
        return Project(
            name = folder.name,
            rootPath = folder.absolutePath,
            detectedType = ProjectDetector.detect(folder)
        )
    }

    fun importProjectFromTreeUri(context: Context, treeUri: Uri): Project? {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val rawName = rootDoc.name ?: "imported_project"
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "imported_project" }

        val rootDir = getProjectsDir(context)
        var targetDir = File(rootDir, safeName)
        var suffix = 1
        while (targetDir.exists()) {
            targetDir = File(rootDir, "${safeName}_$suffix")
            suffix++
        }
        targetDir.mkdirs()

        copyDocumentFileRecursively(context, rootDoc, targetDir)

        return Project(
            name = targetDir.name,
            rootPath = targetDir.absolutePath,
            detectedType = ProjectDetector.detect(targetDir)
        )
    }

    private fun copyDocumentFileRecursively(context: Context, doc: DocumentFile, destDir: File) {
        val files = doc.listFiles()
        for (f in files) {
            val name = f.name ?: continue
            val destFile = File(destDir, name)

            if (f.isDirectory) {
                destFile.mkdirs()
                copyDocumentFileRecursively(context, f, destFile)
            } else if (f.isFile) {
                try {
                    context.contentResolver.openInputStream(f.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteProject(project: Project): Boolean {
        return project.rootDir.deleteRecursively()
    }
}
