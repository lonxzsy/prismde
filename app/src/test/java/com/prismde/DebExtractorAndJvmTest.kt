package com.prismde

import com.prismde.feature_ndk.engine.NdkExtractor
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

class DebExtractorAndJvmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDebArchiveExtractionAndHierarchyFlattening() = runBlocking {
        val workDir = tempFolder.newFolder("deb_test")
        val debFile = File(workDir, "test-openjdk_17.0.20_aarch64.deb")
        val targetDir = File(workDir, "extracted_jdk")

        // 1. Build an in-memory tar.gz containing nested JVM structure
        val tarGzBytes = ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzos ->
                TarArchiveOutputStream(gzos).use { tos ->
                    // Entry 1: bin/java
                    val javaPath = "data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/bin/java"
                    val content = "#!/system/bin/sh\necho Java 17".toByteArray()
                    val entry = TarArchiveEntry(javaPath).apply {
                        size = content.size.toLong()
                        mode = 493 // 0755
                    }
                    tos.putArchiveEntry(entry)
                    tos.write(content)
                    tos.closeArchiveEntry()

                    // Entry 2: lib/libjvm.so
                    val libPath = "data/data/com.termux/files/usr/lib/jvm/java-17-openjdk/lib/libjvm.so"
                    val libContent = "mock-so".toByteArray()
                    val libEntry = TarArchiveEntry(libPath).apply {
                        size = libContent.size.toLong()
                    }
                    tos.putArchiveEntry(libEntry)
                    tos.write(libContent)
                    tos.closeArchiveEntry()
                }
            }
            baos.toByteArray()
        }

        // 2. Build .deb (ar archive) containing:
        //    - debian-binary
        //    - control.tar.gz
        //    - data.tar.gz
        FileOutputStream(debFile).use { fos ->
            ArArchiveOutputStream(fos).use { aos ->
                // debian-binary
                val debBin = "2.0\n".toByteArray()
                aos.putArchiveEntry(ArArchiveEntry("debian-binary", debBin.size.toLong()))
                aos.write(debBin)
                aos.closeArchiveEntry()

                // control.tar.gz
                val control = "dummy".toByteArray()
                aos.putArchiveEntry(ArArchiveEntry("control.tar.gz", control.size.toLong()))
                aos.write(control)
                aos.closeArchiveEntry()

                // data.tar.gz
                aos.putArchiveEntry(ArArchiveEntry("data.tar.gz", tarGzBytes.size.toLong()))
                aos.write(tarGzBytes)
                aos.closeArchiveEntry()
            }
        }

        // 3. Extract with NdkExtractor
        val extractor = NdkExtractor()
        val success = extractor.extract(debFile, targetDir) { /* progress */ }
        assertTrue("Extraction should succeed", success)

        // 4. Verify that hierarchy flattening worked:
        //    targetDir/bin/java should exist directly
        val javaBin = File(targetDir, "bin/java")
        assertTrue("targetDir/bin/java must exist directly after flattening", javaBin.exists())
        assertEquals("#!/system/bin/sh\necho Java 17", javaBin.readText().trim())

        val jvmSo = File(targetDir, "lib/libjvm.so")
        assertTrue("targetDir/lib/libjvm.so must exist directly after flattening", jvmSo.exists())

        // The nested 'data' folder should be cleaned up
        val oldDataDir = File(targetDir, "data")
        assertFalse("old 'data' directory should have been cleaned up", oldDataDir.exists())
    }

    @Test
    fun testMvnScriptJansiPatching() {
        val workDir = tempFolder.newFolder("mvn_test")
        val mvnScript = File(workDir, "mvn")
        mvnScript.writeText(
            """
            exec "${'$'}JAVACMD" \
              ${'$'}MAVEN_OPTS \
              -classpath "${'$'}LAUNCHER_JAR" \
              "-Dclassworlds.conf=${'$'}M2_HOME/bin/m2.conf" \
              "-Dmaven.home=${'$'}M2_HOME" \
              "-Dlibrary.jansi.path=${'$'}{MAVEN_HOME}/lib/jansi-native" \
              "-Dmaven.multiModuleProjectDirectory=${'$'}MAVEN_PROJECTBASEDIR" \
              ${'$'}LAUNCHER_CLASS \
              "${'$'}@"
            """.trimIndent()
        )

        // Apply our patching logic
        var text = mvnScript.readText()
        if (text.contains("jansi-native")) {
            text = text.replace("\"\${MAVEN_HOME}/lib/jansi-native\"", "\"\"")
                       .replace("\${MAVEN_HOME}/lib/jansi-native", "")
            mvnScript.writeText(text)
        }

        val patchedText = mvnScript.readText()
        assertFalse("Script should no longer contain jansi-native", patchedText.contains("jansi-native"))
        assertTrue("Script should contain empty library.jansi.path", patchedText.contains("\"-Dlibrary.jansi.path=\""))
    }

    @Test
    fun testLibzCopyLogic() {
        val workDir = tempFolder.newFolder("libz_test")
        val libDir = File(workDir, "lib").also { it.mkdirs() }
        val libzReal = File(libDir, "libz.so.1.3.2").also { it.writeText("fake-zlib") }

        val libz1 = File(libDir, "libz.so.1")
        val libz = File(libDir, "libz.so")
        if (!libz1.exists()) {
            if (libzReal.exists()) {
                libzReal.copyTo(libz1, overwrite = true)
                libzReal.copyTo(libz, overwrite = true)
            }
        }

        assertTrue("libz.so.1 should have been copied from libz.so.1.3.2", libz1.exists())
        assertEquals("fake-zlib", libz1.readText())
        assertTrue("libz.so should have been copied from libz.so.1.3.2", libz.exists())
    }

    @Test
    fun testLibcxxAndVersionMarkerLogic() {
        val workDir = tempFolder.newFolder("libcxx_test")
        val libDir = File(workDir, "lib").also { it.mkdirs() }
        val marker = File(libDir, ".prism_libs_version")
        val currentVersion = "v2-libcxx"

        assertTrue("Marker should not exist initially", !marker.exists())
        val needUpdateInitial = !marker.exists() || marker.readText().trim() != currentVersion
        assertTrue("Should need update initially", needUpdateInitial)

        marker.writeText(currentVersion)
        val needUpdateAfter = !marker.exists() || marker.readText().trim() != currentVersion
        assertFalse("Should not need update once marker is set", needUpdateAfter)
    }

    @Test
    fun testMavenLocalRepositoryConfiguration() {
        val homeDir = tempFolder.newFolder("home_test")
        val m2Dir = File(homeDir, ".m2").also { it.mkdirs() }
        val m2RepoDir = File(m2Dir, "repository").also { it.mkdirs() }

        val settingsXml = File(m2Dir, "settings.xml")
        settingsXml.writeText(
            "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\">\n" +
            "  <localRepository>${m2RepoDir.absolutePath}</localRepository>\n" +
            "</settings>"
        )

        assertTrue(settingsXml.exists())
        assertTrue(settingsXml.readText().contains(m2RepoDir.absolutePath))
        assertTrue(m2RepoDir.exists() && m2RepoDir.isDirectory)
    }

    @Test
    fun testProjectDetectorGradleWithJniDoesNotFallbackWhenJavaExists() {
        val projectDir = tempFolder.newFolder("gradle_jni_project")
        File(projectDir, "build.gradle").writeText("apply plugin: 'com.android.application'")
        File(projectDir, "jni").mkdirs()
        File(projectDir, "jni/native.cpp").writeText("void foo() {}")

        // When local.properties defines java.home
        val javaDir = tempFolder.newFolder("fake_jdk")
        File(javaDir, "bin").mkdirs()
        File(javaDir, "bin/java").createNewFile()
        File(projectDir, "local.properties").writeText("org.gradle.java.home=${javaDir.absolutePath}")

        val detected = com.prismde.feature_build.engine.ProjectDetector.detect(projectDir)
        assertEquals(com.prismde.core.model.ProjectType.GRADLE, detected)
    }
}
