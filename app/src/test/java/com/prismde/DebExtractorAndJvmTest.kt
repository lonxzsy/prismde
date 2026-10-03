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
}
