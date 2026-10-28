package com.prismde.feature_preview

import android.content.Context
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.feature_build.engine.BuildProcessRunner
import com.prismde.feature_build.engine.BuildToolInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PreviewBuildResult(
    val success: Boolean,
    val soFile: File? = null,
    val logs: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Partial Build Engine for In-App ImGui Preview.
 * Compiles ONLY user GUI files (menu.cpp, style.cpp, imgui) with safe mock stubs,
 * ignoring heavy game hooks, network, and memory scanners.
 * Produces a lightweight libpreview_gui.so in ~1.5 - 2.5 seconds.
 */
object ImGuiPreviewEngine {

    fun isGuiFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val name = file.name.lowercase()
        val path = file.path.lowercase().replace('\\', '/')
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".c")) {
            val isExcluded = name.contains("_functions") || name.contains("_classes") ||
                             name.contains("_structs") || path.contains("/sdk/") ||
                             path.contains("/engine/") || path.contains("/game/") ||
                             name.contains("main") || name.contains("hook")
            if (isExcluded) return false

            if (name.contains("menu") || (name.contains("gui") && !name.startsWith("imgui")) ||
                name.contains("style") || path.contains("/ui/") || name.startsWith("ui_") || name.endsWith("_ui.cpp")) {
                return true
            }
            try {
                val header = file.readLines().take(30).joinToString("\n").lowercase()
                if (header.contains("imgui.h") || header.contains("imnodes.h") || header.contains("implot.h")) {
                    return true
                }
            } catch (_: Throwable) {}
        }
        return false
    }

    suspend fun buildPreviewSo(
        project: Project,
        ndk: NdkVersion,
        context: Context,
        activeFile: File?,
        onProgress: (fileName: String) -> Unit = {}
    ): PreviewBuildResult = withContext(Dispatchers.IO) {
        val previewDir = File(context.cacheDir, "prism_preview").also { it.mkdirs() }
        val targetSo = File(previewDir, "libpreview_gui.so")
        val logs = mutableListOf<String>()

        try {
            val initialFileName = activeFile?.name ?: "menu.cpp"
            onProgress(initialFileName)
            if (targetSo.exists()) targetSo.delete()

            val ndkDir = ndk.getEffectiveNdkDir()
                ?: return@withContext PreviewBuildResult(false, null, logs, "NDK directory not found")

            val compilerFile = ndk.clangPlusExecutable ?: ndk.clangExecutable
            if (compilerFile == null || !compilerFile.exists()) {
                return@withContext PreviewBuildResult(false, null, logs, "Clang compiler not found in NDK")
            }

            // Locate sysroot and platform CRT directories
            val sysrootDir = listOf(
                File(ndkDir, "sysroot"),
                File(ndkDir, "android-ndk-aide/sysroot")
            ).firstOrNull { it.exists() }

            val platformDir = listOf(
                File(ndkDir, "platforms/android-24/arch-arm64"),
                File(ndkDir, "android-ndk-aide/platforms/android-24/arch-arm64"),
                File(ndkDir, "platforms/android-21/arch-arm64"),
                File(ndkDir, "android-ndk-aide/platforms/android-21/arch-arm64"),
                File(ndkDir, "platforms/android-28/arch-arm64")
            ).firstOrNull { it.exists() }

            val stlInc = listOf(
                File(ndkDir, "sources/cxx-stl/llvm-libc++/include"),
                File(ndkDir, "android-ndk-aide/sources/cxx-stl/llvm-libc++/include")
            ).firstOrNull { it.exists() }

            val stlAbiInc = listOf(
                File(ndkDir, "sources/cxx-stl/llvm-libc++abi/include"),
                File(ndkDir, "android-ndk-aide/sources/cxx-stl/llvm-libc++abi/include")
            ).firstOrNull { it.exists() }

            val stlLibDir = listOf(
                File(ndkDir, "sources/cxx-stl/llvm-libc++/libs/arm64-v8a"),
                File(ndkDir, "android-ndk-aide/sources/cxx-stl/llvm-libc++/libs/arm64-v8a")
            ).firstOrNull { it.exists() }

            // Copy libc++_shared.so into previewDir so the dynamic linker finds it at runtime
            val libcxxSrc = listOfNotNull(
                stlLibDir?.let { File(it, "libc++_shared.so") },
                File(context.filesDir, "tools/jdk/lib/libc++_shared.so")
            ).firstOrNull { it.exists() && it.length() > 1000L }

            if (libcxxSrc != null) {
                try {
                    libcxxSrc.copyTo(File(previewDir, "libc++_shared.so"), overwrite = true)
                } catch (_: Throwable) {}
            }

            // 1. Universally locate Dear ImGui sources across the entire project tree
            val coreImGuiNames = setOf(
                "imgui.cpp",
                "imgui_draw.cpp",
                "imgui_tables.cpp",
                "imgui_widgets.cpp",
                "imgui_demo.cpp"
            )
            val backendImGuiNames = setOf(
                "imgui_impl_opengl3.cpp",
                "imgui_impl_android.cpp"
            )

            val imguiSources = mutableListOf<File>()
            val imguiIncludeDirs = mutableSetOf<File>()

            project.rootDir.walkTopDown().maxDepth(8).forEach { f ->
                if (f.isFile) {
                    val name = f.name.lowercase()
                    if (name in coreImGuiNames || name in backendImGuiNames) {
                        imguiSources.add(f)
                        f.parentFile?.let { p ->
                            imguiIncludeDirs.add(p)
                            p.parentFile?.let { imguiIncludeDirs.add(it) }
                        }
                    } else if (name == "imgui.h" || name == "imgui_impl_opengl3.h") {
                        f.parentFile?.let { p ->
                            imguiIncludeDirs.add(p)
                            p.parentFile?.let { imguiIncludeDirs.add(it) }
                        }
                    }
                }
            }

            val hasCoreImGui = imguiSources.any { it.name.equals("imgui.cpp", ignoreCase = true) }
            val hasImGuiHeader = imguiIncludeDirs.any { File(it, "imgui.h").exists() }

            if (!hasCoreImGui || !hasImGuiHeader) {
                val msg = "В проекте не найдены обязательные файлы Dear ImGui (imgui.cpp / imgui.h). Превью невозможно без исходников ImGui."
                logs.add("✖ $msg")
                return@withContext PreviewBuildResult(false, null, logs, msg)
            }

            // 2. Locate GUI source files (menu.cpp, style.cpp, font_loader.cpp or active file)
            val guiSources = mutableListOf<File>()
            if (activeFile != null && activeFile.exists() && activeFile.extension in setOf("cpp", "cc", "c")) {
                val activeName = activeFile.name.lowercase()
                val activePath = activeFile.path.lowercase().replace('\\', '/')
                val isExcluded = activeName.contains("main") || activeName.contains("hook") ||
                                 activeName.contains("memory") || activeName.contains("finder") ||
                                 activeName.contains("freetype") || activeName.contains("_functions") ||
                                 activeName.contains("_classes") || activeName.contains("_structs") ||
                                 activePath.contains("misc/") || activePath.contains("/sdk/") ||
                                 activePath.contains("/engine/") || activePath.contains("/game/")
                if (!isExcluded) {
                    guiSources.add(activeFile)
                }
            }
            project.rootDir.walkTopDown().maxDepth(6).forEach { f ->
                if (f.isFile && f.extension in setOf("cpp", "cc") && !guiSources.contains(f) && !imguiSources.contains(f)) {
                    val name = f.name.lowercase()
                    val path = f.path.lowercase().replace('\\', '/')
                    val isNonGui = name.contains("main") || name.contains("hook") ||
                                   name.contains("memory") || name.contains("finder") ||
                                   name.contains("patch") || name.contains("overlay") ||
                                   name.contains("freetype") || name.contains("_functions") ||
                                   name.contains("_classes") || name.contains("_structs") ||
                                   path.contains("misc/") || path.contains("freetype") ||
                                   path.contains("/sdk/") || path.contains("/engine/") ||
                                   path.contains("/game/") || path.contains("/dumper") ||
                                   path.contains("test")
                    val isGuiTarget = name.contains("menu") || name.contains("style") ||
                                      name.contains("font") || (name.contains("gui") && !name.startsWith("imgui")) ||
                                      path.contains("/ui/") || name.startsWith("ui_") || name.endsWith("_ui.cpp")
                    if (!isNonGui && isGuiTarget) {
                        guiSources.add(f)
                    }
                }
            }

            if (guiSources.isEmpty()) {
                return@withContext PreviewBuildResult(false, null, logs, "No GUI source files (menu.cpp/style.cpp) found for preview")
            }

            // 3. Generate mock stubs and JNI preview harness files
            val mockStubsFile = File(previewDir, "mock_stubs.cpp")
            mockStubsFile.writeText(generateMockStubsCode())

            val previewHarnessFile = File(previewDir, "preview_harness.cpp")
            previewHarnessFile.writeText(generatePreviewHarnessCode())

            // 4. Collect include directories universally
            val includeDirs = mutableSetOf<File>()
            includeDirs.add(project.rootDir)

            // Standard candidate subdirectories
            val standardDirs = listOf(
                "jni", "src", "cpp", "include", "includes", "vendor", "libs",
                "app/src/main/jni", "app/src/main/cpp", "app/src/main/jni/include",
                "jni/include", "jni/src", "src/main/cpp", "src/main/jni"
            )
            for (sub in standardDirs) {
                val dir = File(project.rootDir, sub)
                if (dir.exists() && dir.isDirectory) {
                    includeDirs.add(dir)
                }
            }

            includeDirs.addAll(imguiIncludeDirs)

            // Universal deep scan: every directory containing headers (.h, .hpp, .inl)
            // and all its ancestor directories up to project root.
            // This ensures relative paths like "Android_draw/draw.h", "ui/widgets.h", etc. always resolve.
            // NOTE: To prevent C library header shadowing (e.g. project having src/other/string.h which hijacks <string.h>),
            // any folder containing a shadowed C library string.h is NOT added directly to -I (its parent is added instead).
            val shadowedCHeaders = setOf("string.h", "strings.h")

            fun canIncludeDirectly(dir: File): Boolean {
                if (dir == project.rootDir) return true
                val files = dir.listFiles() ?: return true
                return files.none { it.isFile && it.name.lowercase() in shadowedCHeaders }
            }

            project.rootDir.walkTopDown().maxDepth(8).forEach { f ->
                if (f.isDirectory) {
                    val name = f.name.lowercase()
                    if (name in setOf("include", "includes", "vendor", "imgui", "src", "cpp", "jni", "ui", "common") && canIncludeDirectly(f)) {
                        includeDirs.add(f)
                    }
                } else if (f.isFile && f.extension.lowercase() in setOf("h", "hpp", "inl")) {
                    f.parentFile?.let { p ->
                        if (canIncludeDirectly(p)) {
                            includeDirs.add(p)
                        }
                        var ancestor: File? = p.parentFile
                        while (ancestor != null && ancestor != project.rootDir && ancestor.canonicalPath.startsWith(project.rootDir.canonicalPath)) {
                            if (canIncludeDirectly(ancestor)) {
                                includeDirs.add(ancestor)
                            }
                            ancestor = ancestor.parentFile
                        }
                    }
                }
            }

            // Also add parent directories of all GUI sources
            for (src in guiSources) {
                src.parentFile?.let { p ->
                    if (canIncludeDirectly(p)) {
                        includeDirs.add(p)
                    }
                    var ancestor: File? = p.parentFile
                    while (ancestor != null && ancestor != project.rootDir && ancestor.canonicalPath.startsWith(project.rootDir.canonicalPath)) {
                        if (canIncludeDirectly(ancestor)) {
                            includeDirs.add(ancestor)
                        }
                        ancestor = ancestor.parentFile
                    }
                }
            }

            // Parse LOCAL_C_INCLUDES from Android.mk files (with multiline backslash continuation support)
            project.rootDir.walkTopDown().maxDepth(5).filter { it.isFile && it.name.equals("Android.mk", ignoreCase = true) }.forEach { mkFile ->
                val mkDir = mkFile.parentFile ?: project.rootDir
                includeDirs.add(mkDir)
                try {
                    val content = mkFile.readText().replace("\\\r\n", " ").replace("\\\n", " ")
                    for (line in content.lines()) {
                        val trimmed = line.trim()
                        if ((trimmed.startsWith("LOCAL_C_INCLUDES") || trimmed.startsWith("LOCAL_EXPORT_C_INCLUDES")) && (trimmed.contains(":=") || trimmed.contains("+=") || trimmed.contains("="))) {
                            val parts = trimmed.substringAfter("=").substringBefore("#").trim().split("\\s+".toRegex())
                            for (part in parts) {
                                val clean = part.replace("$(LOCAL_PATH)", mkDir.absolutePath).trim()
                                if (clean.isNotEmpty() && !clean.startsWith("$")) {
                                    val resolved = if (clean.startsWith("/")) File(clean) else File(mkDir, clean)
                                    if (resolved.exists() && resolved.isDirectory) {
                                        includeDirs.add(resolved)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Parse include_directories and target_include_directories from CMakeLists.txt files
            project.rootDir.walkTopDown().maxDepth(5).filter { it.isFile && it.name.equals("CMakeLists.txt", ignoreCase = true) }.forEach { cmakeFile ->
                val cmakeDir = cmakeFile.parentFile ?: project.rootDir
                includeDirs.add(cmakeDir)
                try {
                    val content = cmakeFile.readText()
                    val regex = Regex("""(?:include_directories|target_include_directories)\s*\([^)]+\)""", RegexOption.IGNORE_CASE)
                    regex.findAll(content).forEach { match ->
                        val block = match.value.substringAfter("(").substringBeforeLast(")")
                        val tokens = block.split("\\s+".toRegex())
                        for (token in tokens) {
                            val clean = token.replace("\${CMAKE_CURRENT_SOURCE_DIR}", cmakeDir.absolutePath)
                                .replace("\${CMAKE_SOURCE_DIR}", project.rootDir.absolutePath)
                                .replace("\"", "").trim()
                            if (clean.isNotEmpty() && !clean.startsWith("$") && clean !in setOf("PRIVATE", "PUBLIC", "INTERFACE")) {
                                val resolved = if (clean.startsWith("/")) File(clean) else File(cmakeDir, clean)
                                if (resolved.exists() && resolved.isDirectory) {
                                    includeDirs.add(resolved)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Also check for optional freetype directory if project has it
            val ftCandidate = project.rootDir.walkTopDown().maxDepth(6).firstOrNull { it.isFile && it.name == "ft2build.h" }
            if (ftCandidate != null) {
                ftCandidate.parentFile?.let { p ->
                    includeDirs.add(p)
                    val ft2Dir = File(p, "freetype2")
                    if (ft2Dir.exists()) includeDirs.add(ft2Dir)
                }
            }

            // 5. Build system: Compile each file individually with real-time per-file progress, then link
            val allSources = mutableListOf<File>()
            allSources.addAll(guiSources)
            allSources.addAll(imguiSources)
            allSources.add(mockStubsFile)
            allSources.add(previewHarnessFile)

            val targetTriple = "aarch64-linux-android24"
            // Isolate object cache per project to avoid symbol contamination across different projects
            val projectKey = (project.rootDir.canonicalPath + "_" + project.name).hashCode().toUInt().toString(16)
            val objsDir = File(previewDir, "objs_$projectKey").also { it.mkdirs() }
            val tmpDir = File(context.cacheDir, "prism_tmp").also { it.mkdirs() }
            val objectFiles = mutableListOf<File>()

            // Common include arguments for compiler
            val includeArgs = mutableListOf<String>()
            if (platformDir != null) {
                includeArgs.add("--sysroot=${platformDir.absolutePath}")
            }
            if (stlInc != null) {
                includeArgs.add("-isystem")
                includeArgs.add(stlInc.absolutePath)
            }
            if (stlAbiInc != null) {
                includeArgs.add("-isystem")
                includeArgs.add(stlAbiInc.absolutePath)
            }
            if (sysrootDir != null) {
                includeArgs.add("-isystem")
                includeArgs.add("${sysrootDir.absolutePath}/usr/include")
                val archInc = File(sysrootDir, "usr/include/aarch64-linux-android")
                if (archInc.exists()) {
                    includeArgs.add("-isystem")
                    includeArgs.add(archInc.absolutePath)
                }
            }
            for (inc in includeDirs) {
                includeArgs.add("-I${inc.absolutePath}")
            }

            // Step 1: Compile each source file individually with live progress reporting
            val currentObjNames = mutableSetOf<String>()
            for (src in allSources) {
                val relPath = src.relativeToOrNull(project.rootDir)?.path?.replace(File.separatorChar, '_') ?: src.name
                val objName = "${src.nameWithoutExtension}_${relPath.hashCode().toUInt().toString(16)}.o"
                currentObjNames.add(objName)
                val objFile = File(objsDir, objName)
                objectFiles.add(objFile)

                val needsCompile = !objFile.exists() || src.lastModified() > objFile.lastModified()
                if (needsCompile) {
                    onProgress("Compile++ : ${src.name}")
                    logs.add("[arm64-v8a] Compile++      : ${src.name}")

                    val compileCmd = mutableListOf(
                        compilerFile.absolutePath,
                        "-target", targetTriple,
                        "-c", src.absolutePath,
                        "-o", objFile.absolutePath,
                        "-fPIC",
                        "-fvisibility=default",
                        "-std=c++17",
                        "-O0", // Fast interactive preview compilation without heavy optimizer passes
                        "-g0",
                        "-fno-exceptions",
                        "-fno-rtti",
                        "-fms-extensions",
                        "-D__forceinline=inline __attribute__((always_inline))",
                        "-D_USE_MATH_DEFINES",
                        "-DPRISM_PREVIEW=1",
                        "-DIMGUI_DISABLE_FILE_FUNCTIONS=1",
                        "-Wno-everything"
                    )
                    compileCmd.addAll(includeArgs)

                    val pb = ProcessBuilder(compileCmd)
                        .directory(previewDir)
                        .redirectErrorStream(true)
                    pb.environment()["TMPDIR"] = tmpDir.absolutePath

                    val proc = pb.start()
                    val result = kotlinx.coroutines.withTimeoutOrNull(45_000L) {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val out = proc.inputStream.bufferedReader().use { it.readText() }
                            val code = proc.waitFor()
                            Pair(code, out)
                        }
                    }

                    if (result == null) {
                        try { proc.destroyForcibly() } catch (_: Throwable) {}
                        logs.add("✖ [arm64-v8a] Timeout compiling ${src.name} (>45s)")
                        return@withContext PreviewBuildResult(false, null, logs, "Превышено время компиляции ${src.name} (>45с)")
                    }

                    val (code, out) = result
                    if (code != 0) {
                        logs.addAll(out.lines().filter { it.isNotBlank() })
                        return@withContext PreviewBuildResult(false, null, logs, "Error compiling ${src.name}:\n$out")
                    }
                }
            }

            // Step 2: Link all compiled object files into target libpreview_gui.so
            onProgress("SharedLibrary : libpreview_gui.so")
            logs.add("[arm64-v8a] SharedLibrary  : libpreview_gui.so")

            val linkCmd = mutableListOf(
                compilerFile.absolutePath,
                "-target", targetTriple,
                "-shared",
                "-fPIC",
                "-fvisibility=default",
                "-fms-extensions",
                "-o", targetSo.absolutePath,
                "-Wl,-soname,libpreview_gui.so",
                "-Wl,--export-dynamic",
                "-Wl,--unresolved-symbols=ignore-in-object-files"
            )
            if (platformDir != null) {
                linkCmd.add("--sysroot=${platformDir.absolutePath}")
            }

            objectFiles.forEach { linkCmd.add(it.absolutePath) }

            // Library search paths
            if (stlLibDir != null) {
                linkCmd.add("-L${stlLibDir.absolutePath}")
            }
            if (platformDir != null) {
                val platUsrLib = File(platformDir, "usr/lib")
                if (platUsrLib.exists()) linkCmd.add("-L${platUsrLib.absolutePath}")
            }
            linkCmd.add("-L/system/lib64")
            linkCmd.add("-L/system/lib")

            // Graphics, logging and system native libraries
            linkCmd.add("-lGLESv3")
            linkCmd.add("-lEGL")
            linkCmd.add("-llog")
            linkCmd.add("-landroid")
            linkCmd.add("-lc++_shared")
            linkCmd.add("-lm")
            linkCmd.add("-lc")

            val linkPb = ProcessBuilder(linkCmd)
                .directory(previewDir)
                .redirectErrorStream(true)
            linkPb.environment()["TMPDIR"] = tmpDir.absolutePath

            val linkProc = linkPb.start()
            val linkOut = linkProc.inputStream.bufferedReader().use { it.readText() }
            val linkCode = linkProc.waitFor()

            if (linkOut.isNotBlank()) {
                logs.addAll(linkOut.lines().filter { it.isNotBlank() })
            }

            if (linkCode == 0 && targetSo.exists() && targetSo.length() > 0L) {
                targetSo.setReadable(true, false)
                targetSo.setExecutable(true, false)
                logs.add("✔ [arm64-v8a] Install        : libpreview_gui.so (${targetSo.length() / 1024} KB)")
                PreviewBuildResult(true, targetSo, logs)
            } else {
                PreviewBuildResult(false, null, logs, linkOut.ifBlank { "Linker failed with exit code $linkCode" })
            }
        } catch (e: Exception) {
            logs.add("Error: ${e.message}")
            PreviewBuildResult(false, null, logs, e.message)
        }
    }

    private fun findImGuiDirectory(rootDir: File): File? {
        val candidates = listOf(
            File(rootDir, "vendor/imgui"),
            File(rootDir, "jni/vendor/imgui"),
            File(rootDir, "imgui"),
            File(rootDir, "jni/imgui"),
            File(rootDir, "app/src/main/jni/vendor/imgui")
        )
        return candidates.firstOrNull { File(it, "imgui.h").exists() }
            ?: rootDir.walkTopDown().maxDepth(5).firstOrNull { it.isDirectory && File(it, "imgui.h").exists() }
    }

    private fun generateMockStubsCode(): String = """
        #include <cstdint>
        #include <cstddef>
        #include <cstring>
        #include <sys/types.h>

        #define PRISM_EXPORT __attribute__((visibility("default")))

        extern "C" {

        // Dobby Hook Stubs
        PRISM_EXPORT int DobbyHook(void *target, void *replace, void **origin) {
            if (origin) *origin = target;
            return 0;
        }
        PRISM_EXPORT int DobbyDestroy(void *target) { return 0; }

        // A64 Hook Stubs
        PRISM_EXPORT void A64HookFunction(void *const symbol, const void *const replace, void **result) {
            if (result) *result = symbol;
        }
        PRISM_EXPORT void MSHookFunction(void *symbol, void *replace, void **result) {
            if (result) *result = symbol;
        }

        // Memory Protection / Inspection Stubs
        PRISM_EXPORT bool KittyMemory_protectAddr(void *addr, size_t length, int prot) { return true; }
        PRISM_EXPORT uintptr_t KittyMemory_getAbsoluteAddress(const char *lib, uintptr_t offset) {
            return 0x7F00000000ULL + offset;
        }

        // Linux Kernel Process VM Stubs
        PRISM_EXPORT ssize_t process_vm_readv(pid_t pid, const void *local_iov,
                                              unsigned long liovcnt, const void *remote_iov,
                                              unsigned long riovcnt, unsigned long flags) {
            return 0;
        }
        PRISM_EXPORT ssize_t process_vm_writev(pid_t pid, const void *local_iov,
                                               unsigned long liovcnt, const void *remote_iov,
                                               unsigned long riovcnt, unsigned long flags) {
            return 0;
        }

        // Dummy mock values
        PRISM_EXPORT float mock_health = 100.0f;
        PRISM_EXPORT float mock_distance = 50.0f;
        PRISM_EXPORT bool mock_enabled = true;

        }
    """.trimIndent()

    private fun generatePreviewHarnessCode(): String = """
        #include <jni.h>
        #include <dlfcn.h>
        #include <android/log.h>
        #include <EGL/egl.h>
        #include <GLES3/gl3.h>
        #include "imgui.h"
        #include "backends/imgui_impl_opengl3.h"

        #define PRISM_EXPORT __attribute__((visibility("default")))

        typedef void (*MenuFn)();
        static MenuFn g_userMenuFn = nullptr;

        static float g_TouchX = -1.0f;
        static float g_TouchY = -1.0f;
        static bool g_TouchDown = false;
        static bool g_JustPressed = false;
        static bool g_JustReleased = false;

        namespace UI {
            class Menu { public: static void Draw() __attribute__((weak)); static void Render() __attribute__((weak)); static void SetOpen(bool) __attribute__((weak)); };
            class Style { public: static void ApplyDefaultTheme(float = 1.0f) __attribute__((weak)); };
        }
        namespace ui {
            namespace menu {
                void render() __attribute__((weak));
                void set_menu_open(bool) __attribute__((weak));
            }
        }
        namespace Login {
            void SetLoggedIn(bool) __attribute__((weak));
        }
        extern "C" {
            void DrawMenu() __attribute__((weak));
            void RenderMenu() __attribute__((weak));
            void ShowMenu() __attribute__((weak));
            void DrawUI() __attribute__((weak));
        }

        extern "C" {

        PRISM_EXPORT jboolean JNICALL
        Java_com_prismde_feature_1preview_ImGuiPreviewBridge_nativeInit(
            JNIEnv* env, jclass, jint width, jint height) {

            IMGUI_CHECKVERSION();
            ImGui::CreateContext();
            ImGuiIO& io = ImGui::GetIO();
            io.DisplaySize = ImVec2((float)width, (float)height);
            io.IniFilename = nullptr;
            io.FontGlobalScale = 1.0f; // Use true 1:1 pixel scale matching real project

            g_TouchX = -1.0f;
            g_TouchY = -1.0f;
            g_TouchDown = false;
            g_JustPressed = false;
            g_JustReleased = false;

            // Auto-load project fonts & icons with their native proportions
            bool fontsLoaded = false;

            // 1. If project uses namespace Fonts (like hygen): pixel size is 17.0f
            void* fontsNsSym = dlsym(RTLD_DEFAULT, "_ZN5Fonts9LoadFontsER7ImGuiIOf");
            if (!fontsNsSym) fontsNsSym = dlsym(RTLD_DEFAULT, "_ZN5Fonts9LoadFontsERN5ImGui7ImGuiIOEf");
            if (fontsNsSym) {
                typedef bool (*LoadFontsPxFn)(ImGuiIO&, float);
                fontsLoaded = ((LoadFontsPxFn)fontsNsSym)(io, 17.0f);
            }

            // 2. If project uses UI::FontLoader (like hygen-pubgm): scale factor is 1.0f
            if (!fontsLoaded) {
                void* uiFontLoaderSym = dlsym(RTLD_DEFAULT, "_ZN2UI10FontLoader9LoadFontsERN5ImGui7ImGuiIOEf");
                if (!uiFontLoaderSym) uiFontLoaderSym = dlsym(RTLD_DEFAULT, "_ZN2UI10FontLoader9LoadFontsER7ImGuiIOf");
                if (!uiFontLoaderSym) uiFontLoaderSym = dlsym(RTLD_DEFAULT, "_ZN10FontLoader9LoadFontsERN5ImGui7ImGuiIOEf");
                if (uiFontLoaderSym) {
                    typedef bool (*LoadFontsScaleFn)(ImGuiIO&, float);
                    fontsLoaded = ((LoadFontsScaleFn)uiFontLoaderSym)(io, 1.0f);
                }
            }

            // 3. Fallback generic font loaders
            if (!fontsLoaded) {
                const char* fallbackCandidates[] = {
                    "_Z9LoadFontsRN5ImGui7ImGuiIOE",
                    "_Z9LoadFontsER7ImGuiIO",
                    "_Z9LoadFontsv",
                    "_Z9InitFontsv",
                    "LoadFonts",
                    "InitFonts",
                    nullptr
                };
                for (int i = 0; fallbackCandidates[i] != nullptr; i++) {
                    void* sym = dlsym(RTLD_DEFAULT, fallbackCandidates[i]);
                    if (sym) {
                        typedef bool (*LoadFn)(ImGuiIO&);
                        fontsLoaded = ((LoadFn)sym)(io);
                        if (fontsLoaded) break;
                    }
                }
            }

            // Run Menu::Initialize if present in project
            const char* menuInitCandidates[] = {
                "_ZN2UI4Menu10InitializeEv",  // UI::Menu::Initialize()
                "_ZN4Menu10InitializeEv",     // Menu::Initialize()
                "_ZN2ui4menu10initializeEv",  // ui::menu::initialize()
                "_Z10InitMenuv",
                "InitMenu",
                "InitializeMenu",
                nullptr
            };
            for (int i = 0; menuInitCandidates[i] != nullptr; i++) {
                void* sym = dlsym(RTLD_DEFAULT, menuInitCandidates[i]);
                if (sym) {
                    typedef void (*InitFn)();
                    ((InitFn)sym)();
                    break;
                }
            }

            // Auto-login (for projects with login screen like hygen so main menu tabs are shown)
            if (&Login::SetLoggedIn) {
                Login::SetLoggedIn(true);
            } else {
                void* loginSym = dlsym(RTLD_DEFAULT, "_ZN5Login11SetLoggedInEb");
                if (loginSym) {
                    typedef void (*LoginFn)(bool);
                    ((LoginFn)loginSym)(true);
                }
            }

            // Auto-open menu flags (so menu doesn't start in hidden state)
            if (&UI::Menu::SetOpen) {
                UI::Menu::SetOpen(true);
            } else {
                void* setUiOpenSym = dlsym(RTLD_DEFAULT, "_ZN2UI4Menu7SetOpenEb");
                if (setUiOpenSym) {
                    typedef void (*SetOpenFn)(bool);
                    ((SetOpenFn)setUiOpenSym)(true);
                }
            }
            if (&ui::menu::set_menu_open) {
                ui::menu::set_menu_open(true);
            } else {
                void* setMenuOpenSym = dlsym(RTLD_DEFAULT, "_ZN2ui4menu13set_menu_openEb");
                if (setMenuOpenSym) {
                    typedef void (*SetOpenFn)(bool);
                    ((SetOpenFn)setMenuOpenSym)(true);
                }
            }

            // Global screen dimensions (used by Wolsaw, hygen and overlay menus to center windows)
            void* swSym = dlsym(RTLD_DEFAULT, "g_sw");
            if (swSym) *(float*)swSym = (float)width;
            void* shSym = dlsym(RTLD_DEFAULT, "g_sh");
            if (shSym) *(float*)shSym = (float)height;
            void* swTheme = dlsym(RTLD_DEFAULT, "_ZN2ui5theme4g_swE");
            if (swTheme) *(float*)swTheme = (float)width;
            void* shTheme = dlsym(RTLD_DEFAULT, "_ZN2ui5theme4g_shE");
            if (shTheme) *(float*)shTheme = (float)height;

            // Load project specific font atlas (e.g. Wolsaw AntiloseLoadFonts)
            void* antiloseLoadFontsSym = dlsym(RTLD_DEFAULT, "_Z17AntiloseLoadFontsv");
            if (antiloseLoadFontsSym) {
                typedef void (*AntiloseLoadFontsFn)();
                ((AntiloseLoadFontsFn)antiloseLoadFontsSym)();
            }

            // Ensure Wolsaw bar and alpha stay open
            void* barOpenSym = dlsym(RTLD_DEFAULT, "_ZN2ui3bar6g_openE");
            if (barOpenSym) *(bool*)barOpenSym = true;
            void* barAlphaSym = dlsym(RTLD_DEFAULT, "_ZN2ui3bar7g_alphaE");
            if (barAlphaSym) *(float*)barAlphaSym = 1.0f;

            // Ensure font atlas with icons is built cleanly
            if (!io.Fonts->IsBuilt()) {
                unsigned char* pixels = nullptr;
                int tw = 0, th = 0;
                io.Fonts->GetTexDataAsRGBA32(&pixels, &tw, &th);
            }

            const char* candidates[] = {
                "_ZN2ui4menu6renderEv",        // ui::menu::render() (Wolsaw)
                "_ZN2UI4Menu4DrawEv",         // UI::Menu::Draw() (hygen)
                "_ZN2UI4Menu6RenderEv",       // UI::Menu::Render()
                "_ZN2UI4Menu4ShowEv",         // UI::Menu::Show()
                "_ZN4Menu4DrawEv",            // Menu::Draw()
                "_ZN4Menu6RenderEv",          // Menu::Render()
                "_ZN4Menu4ShowEv",            // Menu::Show()
                "_ZN2UI4DrawEv",              // UI::Draw()
                "_ZN2UI6RenderEv",            // UI::Render()
                "_ZN7Widgets4DrawEv",         // Widgets::Draw()
                "_ZN7Widgets6RenderEv",       // Widgets::Render()
                "_Z8DrawMenuv",               // DrawMenu()
                "_Z10RenderMenuv",            // RenderMenu()
                "_Z8ShowMenuv",               // ShowMenu()
                "_Z6DrawUIv",                 // DrawUI()
                "_Z7DrawGUIv",                // DrawGUI()
                "DrawMenu",
                "RenderMenu",
                "ShowMenu",
                "DrawUI",
                "DrawGUI",
                "Draw",
                "Render",
                "menu_draw",
                "draw_menu",
                nullptr
            };

            for (int i = 0; candidates[i] != nullptr; i++) {
                void* sym = dlsym(RTLD_DEFAULT, candidates[i]);
                if (sym) {
                    g_userMenuFn = (MenuFn)sym;
                    break;
                }
            }

            if (&UI::Style::ApplyDefaultTheme) {
                UI::Style::ApplyDefaultTheme(1.0f);
            } else {
                void* styleSym = dlsym(RTLD_DEFAULT, "_ZN2UI5Style17ApplyDefaultThemeEf");
                if (styleSym) {
                    typedef void (*StyleFn)(float);
                    ((StyleFn)styleSym)(1.0f);
                }
            }

            return ImGui_ImplOpenGL3_Init("#version 300 es") ? JNI_TRUE : JNI_FALSE;
        }

        PRISM_EXPORT void JNICALL
        Java_com_prismde_feature_1preview_ImGuiPreviewBridge_nativeResize(
            JNIEnv* env, jclass, jint width, jint height) {
            if (ImGui::GetCurrentContext()) {
                ImGuiIO& io = ImGui::GetIO();
                io.DisplaySize = ImVec2((float)width, (float)height);
            }
            void* swSym = dlsym(RTLD_DEFAULT, "g_sw");
            if (swSym) *(float*)swSym = (float)width;
            void* shSym = dlsym(RTLD_DEFAULT, "g_sh");
            if (shSym) *(float*)shSym = (float)height;
            void* swTheme = dlsym(RTLD_DEFAULT, "_ZN2ui5theme4g_swE");
            if (swTheme) *(float*)swTheme = (float)width;
            void* shTheme = dlsym(RTLD_DEFAULT, "_ZN2ui5theme4g_shE");
            if (shTheme) *(float*)shTheme = (float)height;
        }

        PRISM_EXPORT void JNICALL
        Java_com_prismde_feature_1preview_ImGuiPreviewBridge_nativeTouch(
            JNIEnv* env, jclass, jint action, jfloat x, jfloat y) {
            g_TouchX = x;
            g_TouchY = y;
            if (action == 0) { // ACTION_DOWN
                g_TouchDown = true;
                g_JustPressed = true;
                g_JustReleased = false;
            } else if (action == 1) { // ACTION_UP or ACTION_CANCEL
                g_TouchDown = false;
                g_JustReleased = true;
            } else if (action == 2) { // ACTION_MOVE
                // Keep touch down state active during drag
            }
        }

        PRISM_EXPORT void JNICALL
        Java_com_prismde_feature_1preview_ImGuiPreviewBridge_nativeRender(
            JNIEnv* env, jclass, jfloat deltaTime) {
            if (!ImGui::GetCurrentContext()) return;

            ImGuiIO& io = ImGui::GetIO();
            io.DeltaTime = deltaTime > 0.0f ? deltaTime : (1.0f / 60.0f);

            // Forward touch coordinates to ImGui
            if (g_TouchX >= 0.0f && g_TouchY >= 0.0f) {
                io.MousePos = ImVec2(g_TouchX, g_TouchY);
            }

            // Keep Wolsaw bar and alpha open every frame
            void* barOpen = dlsym(RTLD_DEFAULT, "_ZN2ui3bar6g_openE");
            if (barOpen) *(bool*)barOpen = true;
            void* barAlpha = dlsym(RTLD_DEFAULT, "_ZN2ui3bar7g_alphaE");
            if (barAlpha) *(float*)barAlpha = 1.0f;

            // Latch button press for at least one full ImGui frame so fast taps register as clicks
            if (g_JustPressed) {
                io.MouseDown[0] = true;
                g_JustPressed = false;
            } else if (g_JustReleased) {
                io.MouseDown[0] = false;
                g_JustReleased = false;
            } else {
                io.MouseDown[0] = g_TouchDown;
            }

            ImGui_ImplOpenGL3_NewFrame();
            ImGui::NewFrame();

            ImGui::SetNextWindowPos(ImVec2(40.0f, 60.0f), ImGuiCond_FirstUseEver);

            bool rendered = false;

            // 1. Wolsaw: ui::menu::render()
            if (!rendered && &ui::menu::render) {
                ui::menu::render();
                rendered = true;
            }

            // 2. hygen / hygen-pubgm: UI::Menu::Draw()
            if (!rendered && &UI::Menu::Draw) {
                UI::Menu::Draw();
                rendered = true;
            }

            // 3. UI::Menu::Render()
            if (!rendered && &UI::Menu::Render) {
                UI::Menu::Render();
                rendered = true;
            }

            // 4. C-style weak entry points
            if (!rendered && &DrawMenu) {
                DrawMenu();
                rendered = true;
            }
            if (!rendered && &RenderMenu) {
                RenderMenu();
                rendered = true;
            }
            if (!rendered && &ShowMenu) {
                ShowMenu();
                rendered = true;
            }
            if (!rendered && &DrawUI) {
                DrawUI();
                rendered = true;
            }

            // 5. Dynamic dlsym menu function (pass pointer to bool for functions like Menu::Render(&open))
            static bool s_alwaysOpen = true;
            if (!rendered && g_userMenuFn) {
                typedef void (*MenuWithBoolFn)(bool*);
                ((MenuWithBoolFn)g_userMenuFn)(&s_alwaysOpen);
                rendered = true;
            }

            if (!rendered) {
                ImGui::SetNextWindowSize(ImVec2(340.0f, 220.0f), ImGuiCond_FirstUseEver);
                if (ImGui::Begin("PrismDE Preview")) {
                    ImGui::Text("Dear ImGui Preview Running Live!");
                    ImGui::Separator();
                    ImGui::Text("Menu compiled and running live in PrismDE!");
                    static float f = 0.5f;
                    ImGui::SliderFloat("Sample Slider", &f, 0.0f, 1.0f);
                    ImGui::End();
                }
            }

            ImGui::Render();
            ImDrawData* dd = ImGui::GetDrawData();
            if (!dd || dd->TotalVtxCount == 0) {
                // If the user menu produced 0 draw primitives (for example closed state), show a helper window
                ImGui_ImplOpenGL3_NewFrame();
                ImGui::NewFrame();
                ImGui::SetNextWindowSize(ImVec2(340.0f, 180.0f), ImGuiCond_Always);
                ImGui::SetNextWindowPos(ImVec2(40.0f, 60.0f), ImGuiCond_Always);
                if (ImGui::Begin("PrismDE Preview")) {
                    ImGui::Text("Dear ImGui Preview Active");
                    ImGui::Separator();
                    ImGui::Text("Current menu returned 0 visible draw items.");
                    if (ImGui::Button("Open / Toggle Menu")) {
                        void* setOpenSym = dlsym(RTLD_DEFAULT, "_ZN2ui4menu13set_menu_openEb");
                        if (setOpenSym) ((void(*)(bool))setOpenSym)(true);
                        void* setUiOpenSym = dlsym(RTLD_DEFAULT, "_ZN2UI4Menu7SetOpenEb");
                        if (setUiOpenSym) ((void(*)(bool))setUiOpenSym)(true);
                        void* bo = dlsym(RTLD_DEFAULT, "_ZN2ui3bar6g_openE");
                        if (bo) *(bool*)bo = true;
                    }
                    ImGui::End();
                }
                ImGui::Render();
                dd = ImGui::GetDrawData();
            }
            if (dd) {
                ImGui_ImplOpenGL3_RenderDrawData(dd);
            }
        }

        PRISM_EXPORT void JNICALL
        Java_com_prismde_feature_1preview_ImGuiPreviewBridge_nativeShutdown(
            JNIEnv* env, jclass) {
            if (ImGui::GetCurrentContext()) {
                ImGui_ImplOpenGL3_Shutdown();
                ImGui::DestroyContext();
            }
            g_userMenuFn = nullptr;
        }

        }
    """.trimIndent()
}
