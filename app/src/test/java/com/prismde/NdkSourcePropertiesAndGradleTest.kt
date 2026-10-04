package com.prismde

import com.prismde.core.model.NdkVersion
import com.prismde.feature_build.engine.BuildProcessRunner
import com.prismde.feature_build.engine.BuildToolInstaller
import com.prismde.feature_build.engine.BuildToolInstaller.ElfArchitecture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NdkSourcePropertiesAndGradleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEnsureSourcePropertiesCreated() {
        val root = tempFolder.newFolder("ndk_r26c")
        val aideFolder = File(root, "android-ndk-aide").also { it.mkdirs() }

        NdkVersion.ensureNdkSourceProperties(aideFolder, "26.2.11394342")

        val propFile = File(aideFolder, "source.properties")
        assertTrue("source.properties must exist in effective NDK directory", propFile.exists())

        val text = propFile.readText()
        assertTrue("Must contain Pkg.Desc", text.contains("Pkg.Desc = Android NDK"))
        assertTrue("Must contain Pkg.Revision", text.contains("Pkg.Revision = 26.2.11394342"))
    }

    @Test
    fun testEnsureLocalPropertiesProvisionsSourceProperties() {
        val projectDir = tempFolder.newFolder("test_project")
        val sdkDir = tempFolder.newFolder("test_sdk")
        val ndkDir = tempFolder.newFolder("test_ndk")

        BuildToolInstaller.ensureLocalProperties(projectDir, sdkDir, ndkDir)

        val localProps = File(projectDir, "local.properties")
        assertTrue("local.properties must be generated", localProps.exists())
        val propsText = localProps.readText()
        assertTrue("local.properties must have sdk.dir", propsText.contains("sdk.dir="))
        assertTrue("local.properties must have ndk.dir", propsText.contains("ndk.dir="))

        val propFile = File(ndkDir, "source.properties")
        assertTrue("source.properties must be auto-provisioned in ndkDir", propFile.exists())
        val text = propFile.readText()
        assertTrue("Must contain Pkg.Revision", text.contains("Pkg.Revision = 26.2.11394342"))
    }

    @Test
    fun testEnsureLocalPropertiesMatchesBuildGradleNdkVersion() {
        val projectDir = tempFolder.newFolder("test_custom_ndk_project")
        val appDir = File(projectDir, "app").also { it.mkdirs() }
        File(appDir, "build.gradle").writeText(
            """
            android {
                compileSdk 34
                ndkVersion '25.1.8937393'
            }
            """.trimIndent()
        )

        val sdkDir = tempFolder.newFolder("custom_sdk")
        val ndkDir = tempFolder.newFolder("custom_ndk")

        BuildToolInstaller.ensureLocalProperties(projectDir, sdkDir, ndkDir)

        val propFile = File(ndkDir, "source.properties")
        assertTrue("source.properties must exist", propFile.exists())
        val text = propFile.readText()
        assertTrue("Must match requested ndkVersion from build.gradle", text.contains("Pkg.Revision = 25.1.8937393"))
    }

    @Test
    fun testEnsureNdkPermissionsNormalizesShebangAndAliases() {
        val ndkDir = tempFolder.newFolder("ndk_shebang_test")
        val ndkBuildAndroid = File(ndkDir, "ndk-build-android")
        ndkBuildAndroid.writeText("#!/bin/sh\r\necho running ndk-build\r\n")

        NdkVersion.ensureNdkPermissions(ndkDir)

        val ndkBuild = File(ndkDir, "ndk-build")
        assertTrue("ndk-build alias must exist when ndk-build-android is present", ndkBuild.exists())

        val content = ndkBuild.readText()
        assertTrue("Shebang must be normalized to /system/bin/sh for Android compatibility", content.startsWith("#!/system/bin/sh"))
        assertTrue("Line endings must not contain CRLF", !content.contains("\r\n"))
    }

    @Test
    fun testEnsureNdkMetadataCreatesAbisJson() {
        val root = tempFolder.newFolder("ndk_metadata_test")
        val aideFolder = File(root, "android-ndk-aide").also { it.mkdirs() }

        NdkVersion.ensureNdkMetadata(aideFolder, "26.2.11394342")

        val abisJson = File(aideFolder, "meta/abis.json")
        assertTrue("meta/abis.json must exist in NDK folder to prevent AGP Unsupported ABI fallback", abisJson.exists())
        val text = abisJson.readText()
        assertTrue("abis.json must define arm64-v8a", text.contains("\"arm64-v8a\""))
        assertTrue("abis.json must define armeabi-v7a", text.contains("\"armeabi-v7a\""))
        assertTrue("abis.json must define x86", text.contains("\"x86\""))
        assertTrue("abis.json must define x86_64", text.contains("\"x86_64\""))
        assertTrue("abis.json must NOT define obsolete armeabi", !text.contains("\"armeabi\":"))

        val platformsJson = File(aideFolder, "meta/platforms.json")
        assertTrue("meta/platforms.json must exist", platformsJson.exists())
        val platformsText = platformsJson.readText()
        assertTrue("platforms.json must support min 16", platformsText.contains("\"min\": 16"))

        val platformsDir = File(aideFolder, "platforms/android-24/arch-arm64/usr/lib")
        assertTrue("platforms/android-24/arch-arm64/usr/lib must exist", platformsDir.exists())
    }

    @Test
    fun testEnsureProjectAbiFiltersSanitizesBuildGradleAndApplicationMk() {
        val projectDir = tempFolder.newFolder("abi_filters_test")
        val appDir = File(projectDir, "app").also { it.mkdirs() }
        val jniDir = File(appDir, "src/main/jni").also { it.mkdirs() }

        val buildGradle = File(appDir, "build.gradle").also {
            it.writeText(
                """
                android {
                    defaultConfig {
                        applicationId "com.example.test"
                        externalNativeBuild {
                            ndkBuild {
                                abiFilters 'armeabi'
                            }
                        }
                    }
                    externalNativeBuild {
                        ndkBuild {
                            path "src/main/jni/Android.mk"
                        }
                    }
                }
                """.trimIndent()
            )
        }

        val appMk = File(jniDir, "Application.mk").also {
            it.writeText("APP_ABI := armeabi arm64-v8a\n")
        }

        BuildToolInstaller.ensureProjectAbiFilters(projectDir, "arm64-v8a")

        val bgText = buildGradle.readText()
        assertTrue("Must inject ndk { abiFilters 'arm64-v8a' }", bgText.contains("abiFilters 'arm64-v8a'"))
        assertTrue("Must not contain standalone 'armeabi'", !bgText.contains("'armeabi'"))

        val mkText = appMk.readText()
        assertTrue("Application.mk must have arm64-v8a", mkText.contains("arm64-v8a"))
        assertTrue("Application.mk must not contain standalone armeabi", !mkText.contains("armeabi "))
    }

    @Test
    fun testEnsureHostArchitectureCompatibilityCreatesLinuxX86Dir() {
        val root = tempFolder.newFolder("ndk_host_arch_test")
        val aideFolder = File(root, "android-ndk-aide").also { it.mkdirs() }
        val arm64Prebuilt = File(aideFolder, "prebuilt/linux-arm64").also { it.mkdirs() }
        File(arm64Prebuilt, "bin").also { it.mkdirs() }

        val arm64Llvm = File(aideFolder, "toolchains/llvm/prebuilt/linux-arm64").also { it.mkdirs() }
        File(arm64Llvm, "bin").also { it.mkdirs() }

        NdkVersion.ensureHostArchitectureCompatibility(aideFolder)

        val x86Prebuilt = File(aideFolder, "prebuilt/linux-x86_64")
        assertTrue("prebuilt/linux-x86_64 must exist for AGP host architecture detection", x86Prebuilt.exists())

        val x86Llvm = File(aideFolder, "toolchains/llvm/prebuilt/linux-x86_64")
        assertTrue("toolchains/llvm/prebuilt/linux-x86_64 must exist for AGP LLVM toolchain detection", x86Llvm.exists())
    }

    @Test
    fun testEnsureNdkStlLibrariesProvisionsLibcxxSharedAndStaticStubs() {
        val root = tempFolder.newFolder("ndk_stl_test")
        val llvmArm64Sysroot = File(root, "toolchains/llvm/prebuilt/linux-arm64/sysroot").also { it.mkdirs() }

        // Mock an available libc++_shared.so in sources/cxx-stl
        val srcStlDir = File(root, "sources/cxx-stl/llvm-libc++/libs/arm64-v8a").also { it.mkdirs() }
        val dummyStl = File(srcStlDir, "libc++_shared.so").also {
            it.writeBytes(ByteArray(2048) { 0x7F.toByte() })
        }

        NdkVersion.ensureHostArchitectureCompatibility(root)
        NdkVersion.ensureNdkStlLibraries(root)

        // Check aarch64-linux-android
        val aarch64Stl = File(llvmArm64Sysroot, "usr/lib/aarch64-linux-android/libc++_shared.so")
        assertTrue("libc++_shared.so must be provisioned for aarch64-linux-android", aarch64Stl.exists())
        assertEquals("File size should match mock STL", dummyStl.length(), aarch64Stl.length())

        val aarch64Static = File(llvmArm64Sysroot, "usr/lib/aarch64-linux-android/libc++_static.a")
        assertTrue("libc++_static.a must exist for aarch64-linux-android", aarch64Static.exists())
        assertEquals("Static stub must start with ar magic", "!<arch>\n", aarch64Static.readText())

        // Check arm-linux-androideabi (preventing the crash reported by AGP)
        val armStl = File(llvmArm64Sysroot, "usr/lib/arm-linux-androideabi/libc++_shared.so")
        assertTrue("libc++_shared.so must be provisioned for arm-linux-androideabi", armStl.exists())

        // Check linux-x86_64 alias sysroot
        val x86SysrootStl = File(root, "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so")
        assertTrue("libc++_shared.so must be reachable from linux-x86_64 sysroot", x86SysrootStl.exists())
    }

    @Test
    fun testEnsureProjectAbiFiltersInjectsNdkVersion() {
        val projectDir = tempFolder.newFolder("ndk_version_inject_test")
        val appDir = File(projectDir, "app").also { it.mkdirs() }
        val buildGradle = File(appDir, "build.gradle").also {
            it.writeText(
                """
                android {
                    compileSdk 34
                    defaultConfig {
                        applicationId "com.example.test"
                    }
                }
                """.trimIndent()
            )
        }

        BuildToolInstaller.ensureProjectAbiFilters(projectDir, "arm64-v8a", "26.2.11394342")

        val content = buildGradle.readText()
        assertTrue("ndkVersion '26.2.11394342' must be injected into android { }", content.contains("ndkVersion '26.2.11394342'"))
    }

    @Test
    fun testNormalizeSdkPlatformResolvesExtensionPlatformForAgp() {
        val sdkDir = tempFolder.newFolder("normalize_platform_test")
        val platformsDir = File(sdkDir, "platforms").also { it.mkdirs() }
        val divertedDir = File(platformsDir, "android-34-2").also { it.mkdirs() }
        File(divertedDir, "android.jar").writeBytes(ByteArray(2048) { 1 })
        File(divertedDir, "source.properties").writeText("Pkg.Desc=Android SDK Platform 34-ext7\nAndroidVersion.ApiLevel=34\nAndroidVersion.ExtensionLevel=7\n")

        BuildToolInstaller.normalizeSdkPlatform(platformsDir, 34)

        val targetDir = File(platformsDir, "android-34")
        assertTrue("platforms/android-34 must exist after normalization", targetDir.exists())
        assertTrue("android.jar must exist in android-34", File(targetDir, "android.jar").exists())

        val propText = File(targetDir, "source.properties").readText()
        assertTrue("source.properties must have AndroidVersion.ApiLevel=34", propText.contains("AndroidVersion.ApiLevel=34"))
        assertTrue("ExtensionLevel must be removed from source.properties for legacy AGP", !propText.contains("ExtensionLevel"))

        val packageXml = File(targetDir, "package.xml")
        packageXml.writeText("<extension-level>7</extension-level><base-extension>true</base-extension>")
        BuildToolInstaller.normalizeSdkPlatform(platformsDir, 34)
        assertTrue("package.xml must be retained", packageXml.exists())
        assertTrue("Extension level must be removed from package metadata", !packageXml.readText().contains("extension-level"))
        assertTrue("The diverted folder must be moved to the canonical path", !divertedDir.exists())
    }

    @Test
    fun testNormalizeSdkPlatformReplacesStaleCanonicalWithFreshDivertedInstall() {
        val sdkDir = tempFolder.newFolder("normalize_stale_platform_test")
        val platformsDir = File(sdkDir, "platforms").also { it.mkdirs() }
        val canonicalDir = File(platformsDir, "android-34").also { it.mkdirs() }
        File(canonicalDir, "android.jar").writeBytes(ByteArray(2048) { 1 })
        File(canonicalDir, "source.properties").writeText(
            "Pkg.Desc=Android SDK Platform 34\nAndroidVersion.ApiLevel=34\n"
        )

        val divertedDir = File(platformsDir, "android-34-2").also { it.mkdirs() }
        File(divertedDir, "android.jar").writeBytes(ByteArray(4096) { 2 })
        File(divertedDir, "source.properties").writeText(
            "Pkg.Desc=Android SDK Platform 34-ext7\nAndroidVersion.ApiLevel=34\nAndroidVersion.ExtensionLevel=7\n"
        )

        BuildToolInstaller.normalizeSdkPlatform(platformsDir, 34)

        assertEquals(4096L, File(canonicalDir, "android.jar").length())
        assertTrue("Fresh diverted install must be moved to the canonical path", !divertedDir.exists())
        assertTrue("Extension metadata must be normalized", !File(canonicalDir, "source.properties").readText().contains("ExtensionLevel"))
    }

    @Test
    fun testDetectProjectBuildToolsVersion() {
        val projectDir = tempFolder.newFolder("build_tools_detection_test")
        val appDir = File(projectDir, "app").also { it.mkdirs() }
        File(appDir, "build.gradle").writeText(
            """
            android {
                buildToolsVersion "33.0.1"
            }
            """.trimIndent()
        )

        assertEquals("33.0.1", BuildToolInstaller.detectProjectBuildToolsVersion(projectDir))
    }

    @Test
    fun testNdkSourcePropertiesDoesNotPolluteParentDir() {
        val sdkDir = tempFolder.newFolder("sdk_ndk_pollution_test")
        val ndkRootDir = File(sdkDir, "ndk").also { it.mkdirs() }
        val ndkDir = File(ndkRootDir, "26.2.11394342").also { it.mkdirs() }

        NdkVersion.ensureNdkSourceProperties(ndkDir, "26.2.11394342")
        NdkVersion.ensureNdkMetadata(ndkDir, "26.2.11394342")
        NdkVersion.ensureNdkPlatforms(ndkDir)

        // NDK dir itself must have source.properties
        assertTrue(File(ndkDir, "source.properties").exists())

        // SDK root and ndk parent must NOT have been polluted
        assertFalse("sdkDir must not contain source.properties", File(sdkDir, "source.properties").exists())
        assertFalse("sdkDir must not contain meta", File(sdkDir, "meta").exists())
        assertFalse("sdkDir must not contain platforms", File(sdkDir, "platforms").exists())
        assertFalse("ndkRootDir must not contain source.properties", File(ndkRootDir, "source.properties").exists())
    }

    @Test
    fun testCleanExtraneousSdkFiles() {
        val sdkDir = tempFolder.newFolder("sdk_clean_test")
        val toolsDir = tempFolder.newFolder("tools_clean_test")

        // Seed pollution in sdkDir, build-tools, platforms, tools
        File(sdkDir, "source.properties").writeText("Pkg.Desc = Android NDK\n")
        File(sdkDir, "meta").mkdirs()
        File(sdkDir, "sysroot").mkdirs()

        val btDir = File(sdkDir, "build-tools").also { it.mkdirs() }
        File(btDir, "source.properties").writeText("Pkg.Desc = Android NDK\n")
        File(btDir, "tmp").mkdirs()

        val platformsDir = File(sdkDir, "platforms").also { it.mkdirs() }
        File(platformsDir, "source.properties").writeText("Pkg.Desc = Android NDK\n")
        // Bogus platform dir with only arch-*
        val fakePlatform = File(platformsDir, "android-21").also { it.mkdirs() }
        File(fakePlatform, "arch-arm64").mkdirs()
        // Legitimate platform
        val realPlatform = File(platformsDir, "android-34").also { it.mkdirs() }
        File(realPlatform, "android.jar").writeBytes(ByteArray(1024) { 1 })

        File(toolsDir, "source.properties").writeText("Pkg.Desc = Android NDK\n")

        BuildToolInstaller.cleanExtraneousSdkFiles(sdkDir, toolsDir)

        assertFalse("sdkDir/source.properties must be deleted", File(sdkDir, "source.properties").exists())
        assertFalse("sdkDir/meta must be deleted", File(sdkDir, "meta").exists())
        assertFalse("sdkDir/sysroot must be deleted", File(sdkDir, "sysroot").exists())
        assertFalse("btDir/source.properties must be deleted", File(btDir, "source.properties").exists())
        assertFalse("btDir/tmp must be deleted", File(btDir, "tmp").exists())
        assertFalse("platforms/source.properties must be deleted", File(platformsDir, "source.properties").exists())
        assertFalse("Fake platform android-21 must be deleted", fakePlatform.exists())
        assertTrue("Real platform android-34 must be preserved", realPlatform.exists())
        assertFalse("toolsDir/source.properties must be deleted", File(toolsDir, "source.properties").exists())
    }

    @Test
    fun testReadElfArchitectureAndPlatformExecutable() {
        val dir = tempFolder.newFolder("elf_test")

        fun createMockElf(file: File, eMachineLow: Byte, eMachineHigh: Byte) {
            val b = ByteArray(24)
            b[0] = 0x7F.toByte()
            b[1] = 'E'.code.toByte()
            b[2] = 'L'.code.toByte()
            b[3] = 'F'.code.toByte()
            b[4] = 2 // 64-bit
            b[5] = 1 // Little-endian
            b[6] = 1 // Version
            b[18] = eMachineLow
            b[19] = eMachineHigh
            file.writeBytes(b)
        }

        val arm64File = File(dir, "aapt2_arm64")
        createMockElf(arm64File, 0xB7.toByte(), 0x00)
        assertEquals(ElfArchitecture.AARCH64, BuildToolInstaller.readElfArchitecture(arm64File))

        val x8664File = File(dir, "aapt2_x86_64")
        createMockElf(x8664File, 0x3E.toByte(), 0x00)
        assertEquals(ElfArchitecture.X86_64, BuildToolInstaller.readElfArchitecture(x8664File))

        val textFile = File(dir, "plain.txt").also { it.writeText("Not an elf file") }
        assertEquals(ElfArchitecture.NOT_ELF, BuildToolInstaller.readElfArchitecture(textFile))
    }

    @Test
    fun testExtractGradleError() {
        val lines1 = listOf(
            "> Task :app:checkDebugAarMetadata FAILED",
            "FAILURE: Build failed with an exception.",
            "",
            "* What went wrong:",
            "Execution failed for task ':app:checkDebugAarMetadata'.",
            "> One or more issues found when checking AAR metadata values:",
            "  The library has specified that it requires android.useAndroidX=true",
            "",
            "* Try:",
            "> Run with --stacktrace option to get the stack trace."
        )

        val err1 = BuildProcessRunner.extractGradleError(lines1)
        assertNotNull(err1)
        assertTrue(err1!!.first.contains("checkDebugAarMetadata"))
        assertTrue(err1.second.contains("android.useAndroidX=true"))

        val lines2 = listOf(
            "FAILURE: Build failed with an exception.",
            "",
            "* What went wrong:",
            "A problem occurred evaluating project ':app'.",
            "> Failed to find target with hash string 'android-34' in: /data/android-sdk",
            "",
            "* Try:"
        )

        val err2 = BuildProcessRunner.extractGradleError(lines2)
        assertNotNull(err2)
        assertTrue(err2!!.first.contains("Failed to find target with hash string 'android-34'"))

        val lines3 = listOf(
            "AAPT2 error unexpected '('",
            "Execution failed for task ':app:processDebugResources'."
        )
        val err3 = BuildProcessRunner.extractGradleError(lines3)
        assertNotNull(err3)
        assertTrue(err3!!.first.contains("Execution failed for task ':app:processDebugResources'"))
    }

    @Test
    fun testEnsureGradleWrapperInjectsAndroidX() {
        val projectDir = tempFolder.newFolder("gradle_wrapper_test")
        val propsFile = File(projectDir, "gradle.properties").also {
            it.writeText("org.gradle.jvmargs=-Xmx1024m\n")
        }

        BuildToolInstaller.ensureGradleWrapper(null, projectDir)

        val text = propsFile.readText()
        assertTrue("gradle.properties must contain android.useAndroidX=true", text.contains("android.useAndroidX=true"))
    }

    @Test
    fun testIsAllowedNdkDirectory() {
        val root = tempFolder.newFolder("allowed_test")
        val sdkDir = File(root, "android-sdk").also { it.mkdirs() }
        val platformsDir = File(sdkDir, "platforms").also { it.mkdirs() }
        val p34 = File(platformsDir, "android-34").also { it.mkdirs() }
        val btDir = File(sdkDir, "build-tools").also { it.mkdirs() }
        val bt34 = File(btDir, "34.0.0").also { it.mkdirs() }

        val validNdk = File(root, "ndk/26.2.11394342").also { it.mkdirs() }
        val aideNdk = File(validNdk, "android-ndk-aide").also { it.mkdirs() }

        assertFalse(NdkVersion.isAllowedNdkDirectory(sdkDir))
        assertFalse(NdkVersion.isAllowedNdkDirectory(platformsDir))
        assertFalse(NdkVersion.isAllowedNdkDirectory(p34))
        assertFalse(NdkVersion.isAllowedNdkDirectory(btDir))
        assertFalse(NdkVersion.isAllowedNdkDirectory(bt34))

        assertTrue(NdkVersion.isAllowedNdkDirectory(validNdk))
        assertTrue(NdkVersion.isAllowedNdkDirectory(aideNdk))
    }

    @Test
    fun testIsBuildToolsDirectoryReadyRequiresAapt2() {
        val dir = tempFolder.newFolder("bt_ready_test")
        File(dir, "source.properties").writeText("Pkg.Revision = 34.0.0\n")

        // Without aapt2: not ready
        assertFalse(BuildToolInstaller.isBuildToolsDirectoryReady(dir))

        // Mock x86_64 aapt2: not ready on ARM64 platform
        val aapt2File = File(dir, "aapt2")
        val b = ByteArray(24)
        b[0] = 0x7F.toByte()
        b[1] = 'E'.code.toByte()
        b[2] = 'L'.code.toByte()
        b[3] = 'F'.code.toByte()
        b[4] = 2
        b[5] = 1
        b[18] = 0x3E.toByte() // x86_64
        aapt2File.writeBytes(b)

        assertFalse(BuildToolInstaller.isExecutableOnCurrentPlatform(aapt2File))
        assertFalse(BuildToolInstaller.isBuildToolsDirectoryReady(dir))

        // Mock AARCH64 aapt2: ready!
        b[18] = 0xB7.toByte() // aarch64
        aapt2File.writeBytes(b)
        assertTrue(BuildToolInstaller.isExecutableOnCurrentPlatform(aapt2File))
        assertTrue(BuildToolInstaller.isBuildToolsDirectoryReady(dir))
    }

    @Test
    fun testShebangRecursiveNormalization() {
        val ndkDir = tempFolder.newFolder("shebang_test")
        val buildDir = File(ndkDir, "build").also { it.mkdirs() }
        val rootScript = File(ndkDir, "ndk-build").also {
            it.writeText("#!/bin/sh\r\nDIR=\"\$(cd \"\$(dirname \"\$0\")\" && pwd)\"\r\n\"\$DIR/build/ndk-build\" \"\$@\"\r\n")
        }
        val innerScript = File(buildDir, "ndk-build").also {
            it.writeText("#!/bin/bash\r\necho running inner ndk-build\r\n")
        }

        NdkVersion.ensureNdkPermissions(ndkDir)

        val rootText = rootScript.readText()
        assertTrue("Shebang must be /system/bin/sh", rootText.startsWith("#!/system/bin/sh"))
        assertFalse("Line endings must not have CRLF", rootText.contains("\r\n"))

        val innerText = innerScript.readText()
        assertTrue("Inner shebang must be /system/bin/sh", innerText.startsWith("#!/system/bin/sh"))
        assertFalse("Inner line endings must not have CRLF", innerText.contains("\r\n"))
    }

    @Test
    fun testShebangPreservesFlags() {
        val ndkDir = tempFolder.newFolder("shebang_flags_test")
        val script = File(ndkDir, "ndk-build").also {
            it.writeText("#!/bin/sh -e\r\necho Hello\r\n")
        }

        NdkVersion.ensureNdkPermissions(ndkDir)

        val text = script.readText()
        assertTrue("Shebang must preserve -e flag", text.startsWith("#!/system/bin/sh -e"))
        assertFalse("Must not have CRLF", text.contains("\r\n"))
    }

    @Test
    fun testMakeNotReplacedByBusybox() {
        val ndkDir = tempFolder.newFolder("make_test")
        val binDir = File(ndkDir, "prebuilt/linux-arm64/bin").also { it.mkdirs() }
        val busyboxFile = File(binDir, "busybox")

        // Create mock ARM64 ELF header helper
        fun writeArm64Elf(f: File) {
            val b = ByteArray(24)
            b[0] = 0x7F.toByte()
            b[1] = 'E'.code.toByte()
            b[2] = 'L'.code.toByte()
            b[3] = 'F'.code.toByte()
            b[4] = 2
            b[5] = 1
            b[18] = 0xB7.toByte() // aarch64
            f.writeBytes(b)
        }

        writeArm64Elf(busyboxFile)
        val realMake = File(binDir, "make")
        writeArm64Elf(realMake)

        NdkVersion.ensureNdkPermissions(ndkDir)

        // Real make must not be deleted or replaced by busybox
        assertTrue("make must exist", realMake.exists())
        assertTrue("make must remain executable", BuildToolInstaller.isExecutableOnCurrentPlatform(realMake))

        // Check essential tools (mkdir, etc.) got linked/copied from busybox, but make was NOT touched
        val mkdirFile = File(binDir, "mkdir")
        assertTrue("mkdir should be provisioned from busybox", mkdirFile.exists())
    }

    @Test
    fun testBinaryToolNonElfRejected() {
        val dir = tempFolder.newFolder("non_elf_test")
        val corruptAapt2 = File(dir, "aapt2").also { it.writeText("Corrupted or HTML 404 text") }
        val corruptMake = File(dir, "make").also { it.writeText("Not an elf binary") }

        assertFalse("Corrupted aapt2 must be rejected", BuildToolInstaller.isExecutableOnCurrentPlatform(corruptAapt2))
        assertFalse("Corrupted make must be rejected", BuildToolInstaller.isExecutableOnCurrentPlatform(corruptMake))
    }
}

