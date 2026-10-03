package com.prismde.feature_files.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import com.prismde.feature_build.engine.BuildToolInstaller
import com.prismde.feature_build.engine.ProjectDetector
import java.io.File
import java.io.FileOutputStream

object ProjectManager {

    private const val PREFS_NAME = "prism_projects_registry"
    private const val KEY_EXTERNAL_PROJECTS = "external_projects_paths"

    fun getExternalProjectPaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_EXTERNAL_PROJECTS, emptySet()) ?: emptySet()
    }

    fun registerExternalProject(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_EXTERNAL_PROJECTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(path)
        prefs.edit().putStringSet(KEY_EXTERNAL_PROJECTS, current).apply()
    }

    fun unregisterExternalProject(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_EXTERNAL_PROJECTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(path)
        prefs.edit().putStringSet(KEY_EXTERNAL_PROJECTS, current).apply()
    }

    fun getProjectsDir(context: Context): File {
        return File(context.filesDir, "projects").also { it.mkdirs() }
    }

    fun listProjects(context: Context): List<Project> {
        val projects = mutableListOf<Project>()

        // 1. Internal projects in app storage
        val root = getProjectsDir(context)
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        for (dir in dirs) {
            projects.add(
                Project(
                    name = dir.name,
                    rootPath = dir.absolutePath,
                    detectedType = ProjectDetector.detect(dir, context)
                )
            )
        }

        // 2. External projects opened directly from user storage
        val externalPaths = getExternalProjectPaths(context)
        for (path in externalPaths) {
            val f = File(path)
            if (f.exists() && f.isDirectory && f.absolutePath != root.absolutePath) {
                if (projects.none { it.rootPath == f.absolutePath }) {
                    projects.add(
                        Project(
                            name = f.name,
                            rootPath = f.absolutePath,
                            detectedType = ProjectDetector.detect(f, context)
                        )
                    )
                }
            }
        }

        return projects.sortedBy { it.name.lowercase() }
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

            ProjectType.MAVEN -> {
                File(projectDir, "pom.xml").writeText(
                    """
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>

                        <groupId>com.prismde</groupId>
                        <artifactId>$finalName</artifactId>
                        <version>1.0-SNAPSHOT</version>

                        <properties>
                            <maven.compiler.source>17</maven.compiler.source>
                            <maven.compiler.target>17</maven.compiler.target>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        </properties>
                    </project>
                    """.trimIndent()
                )

                val javaDir = File(projectDir, "src/main/java/com/example").also { it.mkdirs() }
                File(javaDir, "Main.java").writeText(
                    """
                    package com.example;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println("Hello from Maven project in PrismDE!");
                        }
                    }
                    """.trimIndent()
                )
            }

            ProjectType.GRADLE -> {
                File(projectDir, "settings.gradle").writeText(
                    """
                    rootProject.name = '$finalName'
                    include ':app'
                    """.trimIndent()
                )

                File(projectDir, "build.gradle").writeText(
                    """
                    buildscript {
                        repositories {
                            google()
                            mavenCentral()
                        }
                        dependencies {
                            classpath 'com.android.tools.build:gradle:8.1.0'
                        }
                    }

                    allprojects {
                        repositories {
                            google()
                            mavenCentral()
                        }
                    }
                    """.trimIndent()
                )

                val appDir = File(projectDir, "app").also { it.mkdirs() }
                File(appDir, "build.gradle").writeText(
                    """
                    plugins {
                        id 'com.android.application'
                    }

                    android {
                        namespace 'com.example.$finalName'
                        compileSdk 34

                        defaultConfig {
                            applicationId "com.example.$finalName"
                            minSdk 24
                            targetSdk 34
                            versionCode 1
                            versionName "1.0"

                            externalNativeBuild {
                                ndkBuild {
                                    abiFilters 'arm64-v8a'
                                }
                            }
                        }

                        externalNativeBuild {
                            ndkBuild {
                                path "src/main/jni/Android.mk"
                            }
                        }
                    }

                    dependencies {
                        implementation 'androidx.appcompat:appcompat:1.6.1'
                    }
                    """.trimIndent()
                )

                val srcMain = File(appDir, "src/main").also { it.mkdirs() }
                File(srcMain, "AndroidManifest.xml").writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                        <application
                            android:allowBackup="true"
                            android:label="$finalName"
                            android:supportsRtl="true">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                    """.trimIndent()
                )

                val javaDir = File(srcMain, "java/com/example/$finalName").also { it.mkdirs() }
                File(javaDir, "MainActivity.java").writeText(
                    """
                    package com.example.$finalName;

                    import android.os.Bundle;
                    import androidx.appcompat.app.AppCompatActivity;

                    public class MainActivity extends AppCompatActivity {
                        static {
                            System.loadLibrary("native-lib");
                        }

                        public native String stringFromJNI();

                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                    """.trimIndent()
                )

                val jniDir = File(srcMain, "jni").also { it.mkdirs() }
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
                    APP_ABI := arm64-v8a
                    APP_PLATFORM := android-24
                    APP_STL := c++_shared
                    """.trimIndent()
                )

                File(jniDir, "native-lib.cpp").writeText(
                    """
                    #include <jni.h>
                    #include <string>

                    extern "C" JNIEXPORT jstring JNICALL
                    Java_com_example_${finalName}_MainActivity_stringFromJNI(
                            JNIEnv* env,
                            jobject /* this */) {
                        std::string hello = "Hello from PrismDE C++!";
                        return env->NewStringUTF(hello.c_str());
                    }
                    """.trimIndent()
                )

                // Provision Gradle Wrapper (gradlew, gradlew.bat, wrapper jar/properties) & gradle.properties
                BuildToolInstaller.ensureGradleWrapper(context, projectDir)
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
            detectedType = ProjectDetector.detect(projectDir, context)
        )
    }

    fun importProjectFromFolder(folder: File, context: Context? = null): Project? {
        if (!folder.exists() || !folder.isDirectory) return null
        return Project(
            name = folder.name,
            rootPath = folder.absolutePath,
            detectedType = ProjectDetector.detect(folder, context)
        )
    }

    fun importProjectFromTreeUri(context: Context, treeUri: Uri): Project? {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Throwable) {}

        // 1. Open folder directly without copying into app storage!
        val resolvedFolder = resolveFolderFromUri(treeUri)
        if (resolvedFolder != null && resolvedFolder.exists() && resolvedFolder.isDirectory) {
            registerExternalProject(context, resolvedFolder.absolutePath)
            return Project(
                name = resolvedFolder.name,
                rootPath = resolvedFolder.absolutePath,
                detectedType = ProjectDetector.detect(resolvedFolder, context)
            )
        }

        // 2. Fallback only if direct path couldn't be resolved (virtual provider)
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
            detectedType = ProjectDetector.detect(targetDir, context)
        )
    }

    fun resolveFolderFromUri(uri: Uri): File? {
        try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            if (docId != null) {
                val resolved = resolveFromDocId(docId)
                if (resolved != null && resolved.exists()) return resolved
            }
        } catch (_: Throwable) {}

        try {
            val docId = android.provider.DocumentsContract.getDocumentId(uri)
            if (docId != null) {
                val resolved = resolveFromDocId(docId)
                if (resolved != null && resolved.exists()) return resolved
            }
        } catch (_: Throwable) {}

        try {
            val path = uri.path
            if (path != null) {
                val decoded = Uri.decode(path)
                val markers = listOf("/tree/primary:", "/document/primary:", "primary:")
                for (marker in markers) {
                    if (decoded.contains(marker)) {
                        val sub = decoded.substringAfter(marker)
                        val f = File(android.os.Environment.getExternalStorageDirectory(), sub)
                        if (f.exists()) return f
                    }
                }
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: "")
                    if (f.exists()) return f
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun resolveFromDocId(docId: String): File? {
        val split = docId.split(":")
        if (split.size < 2) return null
        val type = split[0]
        val sub = split[1]

        return if ("primary".equals(type, ignoreCase = true)) {
            File(android.os.Environment.getExternalStorageDirectory(), sub)
        } else {
            val extStorage = File("/storage/$type")
            if (extStorage.exists()) {
                File(extStorage, sub)
            } else {
                File("/mnt/media_rw/$type", sub)
            }
        }
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

    fun deleteProject(context: Context, project: Project): Boolean {
        val root = getProjectsDir(context)
        val isInternal = try {
            project.rootDir.canonicalPath.startsWith(root.canonicalPath)
        } catch (_: Throwable) {
            false
        }
        unregisterExternalProject(context, project.rootPath)
        return if (isInternal) {
            project.rootDir.deleteRecursively()
        } else {
            true
        }
    }

    fun deleteProject(project: Project): Boolean {
        return project.rootDir.deleteRecursively()
    }
}
