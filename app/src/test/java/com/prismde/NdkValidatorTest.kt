package com.prismde

import com.prismde.feature_ndk.engine.NdkValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidateAndroidNdkAideNestedLayout() {
        val root = tempFolder.newFolder("ndk_install_root")
        val aideFolder = File(root, "android-ndk-aide").also { it.mkdirs() }
        val binFolder = File(aideFolder, "toolchains/llvm/prebuilt/linux-arm64/bin").also { it.mkdirs() }
        File(binFolder, "clang").writeText("echo binary")
        File(aideFolder, "ndk-build").writeText("echo ndk-build")

        val result = NdkValidator.validate(root)
        assertTrue("Expected validation to succeed for android-ndk-aide layout", result.isValid)
        assertTrue("Found clang binary", result.foundClang)
        assertTrue("Found ndk-build", result.foundNdkBuild)
        assertEquals("Resolved directory should be android-ndk-aide", aideFolder.canonicalPath, result.actualNdkDir?.canonicalPath)
    }

    @Test
    fun testValidateStandardLayout() {
        val root = tempFolder.newFolder("ndk_standard_root")
        val binFolder = File(root, "toolchains/llvm/prebuilt/linux-aarch64/bin").also { it.mkdirs() }
        File(binFolder, "clang").writeText("echo binary")
        val cmakeFile = File(root, "build/cmake/android.toolchain.cmake").also {
            it.parentFile?.mkdirs()
            it.writeText("# cmake toolchain")
        }

        val result = NdkValidator.validate(root)
        assertTrue("Expected validation to succeed for standard layout", result.isValid)
        assertTrue("Found clang binary", result.foundClang)
        assertTrue("Found cmake toolchain", result.foundCMakeToolchain)
        assertEquals("Resolved directory should be root", root.canonicalPath, result.actualNdkDir?.canonicalPath)
    }

    @Test
    fun testValidateEmptyDirectoryFails() {
        val root = tempFolder.newFolder("ndk_empty_root")
        val result = NdkValidator.validate(root)
        assertFalse("Expected empty folder to be invalid", result.isValid)
    }

    @Test
    fun testEffectiveNdkDirDoesNotDoubleAide() {
        val root = tempFolder.newFolder("ndk_test_double")
        val aideFolder = File(root, "android-ndk-aide").also { it.mkdirs() }
        val binFolder = File(aideFolder, "toolchains/llvm/prebuilt/linux-arm64/bin").also { it.mkdirs() }
        val clang7File = File(binFolder, "clang-7").also {
            it.writeText("binary content with size > 10000 bytes: " + "A".repeat(15000))
        }
        // Simulate a corrupted/empty nested folder
        File(aideFolder, "android-ndk-aide").also { it.mkdirs() }

        val ndk = com.prismde.core.model.NdkVersion(
            versionTag = "r26c",
            displayName = "NDK r26c",
            llvmVersion = "Clang 17",
            downloadUrl = "",
            archiveSizeBytes = 100L,
            installPath = aideFolder.absolutePath
        )

        val effective = ndk.getEffectiveNdkDir()
        assertEquals("Effective dir must be the folder with toolchains, not the empty nested one", aideFolder.canonicalPath, effective?.canonicalPath)

        val clangPlus = ndk.clangPlusExecutable
        assertTrue("clangPlusExecutable must be resolved", clangPlus != null && clangPlus.exists())
    }
}
