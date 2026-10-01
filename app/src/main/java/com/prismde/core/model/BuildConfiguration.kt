package com.prismde.core.model

enum class AndroidAbi(val abiString: String, val triple: String) {
    ARM64_V8A("arm64-v8a", "aarch64-linux-android"),
    ARMEABI_V7A("armeabi-v7a", "armv7a-linux-androideabi"),
    X86_64("x86_64", "x86_64-linux-android"),
    X86("x86", "i686-linux-android")
}

enum class CppStandard(val flag: String) {
    CPP11("-std=c++11"),
    CPP14("-std=c++14"),
    CPP17("-std=c++17"),
    CPP20("-std=c++20"),
    CPP23("-std=c++23")
}

enum class OptimizationLevel(val flag: String) {
    DEBUG("-O0"),
    FAST("-O2"),
    MAXIMUM("-O3"),
    SIZE("-Os")
}

enum class ProjectType {
    AUTO_DETECT,
    PURE_JNI_SO,             // Project with jni/ folder building .so only
    CMAKE,                   // CMakeLists.txt project
    SINGLE_FILE_EXECUTABLE   // Single .cpp or .c file executable
}

data class BuildConfiguration(
    val selectedAbi: AndroidAbi = AndroidAbi.ARM64_V8A,
    val minApiLevel: Int = 24,
    val cppStandard: CppStandard = CppStandard.CPP17,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.FAST,
    val projectType: ProjectType = ProjectType.AUTO_DETECT,
    val customCFlags: String = "-Wall -fexceptions -frtti",
    val customLdFlags: String = "-llog",
    val activeNdkTag: String = "r26c"
)
