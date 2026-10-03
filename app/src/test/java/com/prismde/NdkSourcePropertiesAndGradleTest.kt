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
        assertTrue("abis.json must NOT define obsolete armeabi", !text.contains("\"armeabi\":"))

        val platformsJson = File(aideFolder, "meta/platforms.json")
        assertTrue("meta/platforms.json must exist", platformsJson.exists())
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
}
