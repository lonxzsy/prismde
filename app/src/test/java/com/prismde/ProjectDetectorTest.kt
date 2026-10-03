package com.prismde

import com.prismde.core.model.ProjectType
import com.prismde.feature_build.engine.ProjectDetector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDetectPureJniProject() {
        val root = tempFolder.newFolder("test_jni_project")
        val jni = File(root, "jni").also { it.mkdirs() }
        File(jni, "native-lib.cpp").writeText("#include <jni.h>")
        File(jni, "Android.mk").writeText("LOCAL_MODULE := test")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.PURE_JNI_SO, detected)
    }

    @Test
    fun testDetectCMakeProject() {
        val root = tempFolder.newFolder("test_cmake_project")
        File(root, "CMakeLists.txt").writeText("project(test)")
        File(root, "main.cpp").writeText("int main() {}")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.CMAKE, detected)
    }

    @Test
    fun testDetectSingleFileProject() {
        val root = tempFolder.newFolder("test_single_file")
        File(root, "hello.cpp").writeText("int main() {}")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.SINGLE_FILE_EXECUTABLE, detected)
    }

    @Test
    fun testDetectPureJniWhenRootIsNamedJni() {
        val root = tempFolder.newFolder("jni")
        File(root, "GPlugin.cpp").writeText("#include <jni.h>")
        File(root, "obfuscate.h").writeText("#pragma once")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.PURE_JNI_SO, detected)
    }

    @Test
    fun testFindSourceFilesExcludesHeaders() {
        val root = tempFolder.newFolder("source_test")
        val cppFile = File(root, "main.cpp").also { it.writeText("int main() {}") }
        val headerFile = File(root, "header.h").also { it.writeText("#pragma once") }
        val hppFile = File(root, "util.hpp").also { it.writeText("#pragma once") }

        val proj = com.prismde.core.model.Project("test", root.absolutePath)
        val sources = ProjectDetector.findSourceFiles(proj)

        assertEquals(1, sources.size)
        assertEquals(cppFile.absolutePath, sources[0].absolutePath)

        val includeDirs = ProjectDetector.findIncludeDirectories(proj)
        org.junit.Assert.assertTrue(includeDirs.contains(root))
    }

    @Test
    fun testDetectMavenProject() {
        val root = tempFolder.newFolder("test_maven_project")
        File(root, "pom.xml").writeText("<project><modelVersion>4.0.0</modelVersion></project>")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.MAVEN, detected)
    }

    @Test
    fun testDetectMavenWrapperProject() {
        val root = tempFolder.newFolder("test_mvnw_project")
        File(root, "mvnw").writeText("#!/bin/sh\n")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.MAVEN, detected)
    }

    @Test
    fun testProjectSkillsLoading() {
        val root = tempFolder.newFolder("test_skills_project")
        val skillsDir = File(root, ".prismde/skills").also { it.mkdirs() }
        File(skillsDir, "ndk_rules.md").writeText("# NDK Rules\nAlways check JNIEnv before calling native methods.")

        val skills = com.prismde.feature_build.engine.ProjectSkillManager.loadProjectSkills(root)
        assertEquals(1, skills.size)
        assertEquals("ndk_rules", skills[0].name)
        org.junit.Assert.assertTrue(skills[0].content.contains("JNIEnv"))
    }

    @Test
    fun testDetectGradleProject() {
        val root = tempFolder.newFolder("test_gradle_project")
        File(root, "build.gradle").writeText("plugins { id 'com.android.application' }")
        File(root, "gradlew").writeText("#!/bin/sh")

        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.GRADLE, detected)
    }

    @Test
    fun testDetectNestedJniProject() {
        val root = tempFolder.newFolder("test_nested_jni_project")
        File(root, "build.gradle").writeText("apply plugin: 'com.android.application'")
        val jniDir = File(root, "app/src/main/jni").also { it.mkdirs() }
        File(jniDir, "Android.mk").writeText("LOCAL_MODULE := AimCheatPro")
        File(jniDir, "main.cpp").writeText("#include <jni.h>")

        val proj = com.prismde.core.model.Project("Injector", root.absolutePath)
        val detected = ProjectDetector.detect(root)
        assertEquals(ProjectType.GRADLE, detected)
        org.junit.Assert.assertTrue(proj.hasJniDir)
        org.junit.Assert.assertTrue(proj.hasAndroidMk)
        assertEquals("jni", proj.jniDir.name)
    }

    @Test
    fun testMavenDiagnosticParsing() {
        val parser = com.prismde.feature_build.engine.ClangDiagnosticParser()
        val line = "[ERROR] /src/main/java/Main.java:[15,8] cannot find symbol"
        val diag = parser.parseLine(line)

        org.junit.Assert.assertNotNull(diag)
        assertEquals(15, diag?.line)
        assertEquals(8, diag?.column)
        assertEquals(com.prismde.core.model.DiagnosticSeverity.ERROR, diag?.severity)
        assertEquals("cannot find symbol", diag?.rawMessage)
    }
}
