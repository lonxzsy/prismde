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
}
