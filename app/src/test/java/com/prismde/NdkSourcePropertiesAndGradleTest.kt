package com.prismde

import com.prismde.core.model.NdkVersion
import com.prismde.feature_build.engine.BuildToolInstaller
import org.junit.Assert.assertEquals
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
    fun testNormalizeSdkPlatformResolvesDivertedFoldersAndPreservesMetadata() {
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
        assertTrue("ExtensionLevel from the official platform metadata must be preserved", propText.contains("ExtensionLevel=7"))

        val packageXml = File(targetDir, "package.xml")
        packageXml.writeText("official metadata")
        assertTrue("Official package metadata must not be removed", packageXml.exists())
        assertTrue("The diverted folder must be moved to the canonical path", !divertedDir.exists())
    }
}
