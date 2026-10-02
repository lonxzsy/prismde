package com.prismde.feature_editor.language

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File

enum class PrismLanguageType {
    CPP,
    CMAKE,
    MAKEFILE,
    JAVA,
    JSON,
    GENERIC
}

class PrismCodeLanguage(
    val type: PrismLanguageType,
    var diffGreenRange: IntRange? = null
) : EmptyLanguage() {

    private val analyzeManager = PrismAnalyzeManager(type) { diffGreenRange }

    fun updateDiffGreenRange(range: IntRange?) {
        diffGreenRange = range
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun useTab(): Boolean = false

    override fun getIndentAdvance(ref: ContentReference, line: Int, column: Int): Int {
        val lineStr = ref.getLine(line).toString().trimEnd()
        return if (lineStr.endsWith("{") || lineStr.endsWith("(") || lineStr.endsWith(":")) {
            4
        } else {
            0
        }
    }

    override fun getSymbolPairs(): SymbolPairMatch {
        val pairs = SymbolPairMatch()
        pairs.putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        pairs.putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        pairs.putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        pairs.putPair('"', SymbolPairMatch.SymbolPair("\"", "\""))
        pairs.putPair('\'', SymbolPairMatch.SymbolPair("'", "'"))
        return pairs
    }

    override fun requireAutoComplete(
        ref: ContentReference,
        pos: CharPosition,
        publisher: CompletionPublisher,
        extra: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(ref, pos) { c ->
            Character.isJavaIdentifierPart(c) || c == '#' || c == '$'
        }
        if (prefix.isBlank()) return

        val suggestions = when (type) {
            PrismLanguageType.CPP -> CPP_KEYWORDS + CPP_TYPES + CPP_PREPROCESSOR
            PrismLanguageType.CMAKE -> CMAKE_COMMANDS + CMAKE_VARIABLES
            PrismLanguageType.MAKEFILE -> MAKE_KEYWORDS + NDK_MAKE_VARS
            PrismLanguageType.JAVA -> JAVA_KEYWORDS
            else -> emptyList()
        }

        val lowerPrefix = prefix.lowercase()
        for (item in suggestions) {
            if (item.lowercase().startsWith(lowerPrefix)) {
                publisher.addItem(
                    SimpleCompletionItem(
                        item,
                        type.name,
                        prefix.length,
                        item
                    )
                )
            }
        }
    }

    override fun destroy() {
        super.destroy()
        analyzeManager.destroy()
    }

    companion object {
        fun forFile(file: File?): PrismCodeLanguage {
            if (file == null) return PrismCodeLanguage(PrismLanguageType.CPP)
            val name = file.name
            val ext = file.extension.lowercase()

            val type = when {
                name.equals("CMakeLists.txt", ignoreCase = true) || ext == "cmake" -> PrismLanguageType.CMAKE
                name.equals("Android.mk", ignoreCase = true) ||
                name.equals("Application.mk", ignoreCase = true) ||
                name.equals("Makefile", ignoreCase = true) ||
                ext == "mk" -> PrismLanguageType.MAKEFILE
                ext in listOf("cpp", "cxx", "cc", "c", "h", "hpp", "hxx") -> PrismLanguageType.CPP
                ext in listOf("java", "kt") -> PrismLanguageType.JAVA
                ext == "json" -> PrismLanguageType.JSON
                else -> PrismLanguageType.CPP
            }
            return PrismCodeLanguage(type)
        }

        internal val CPP_PREPROCESSOR = listOf(
            "#include", "#define", "#undef", "#ifdef", "#ifndef", "#if", "#elif",
            "#else", "#endif", "#pragma", "#error", "#warning"
        )

        internal val CPP_KEYWORDS = listOf(
            "alignas", "alignof", "asm", "auto", "bool", "break", "case", "catch",
            "class", "concept", "const", "consteval", "constexpr", "constinit",
            "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
            "default", "delete", "do", "dynamic_cast", "else", "enum", "explicit",
            "export", "extern", "false", "for", "friend", "goto", "if", "inline",
            "mutable", "namespace", "new", "noexcept", "nullptr", "operator", "private",
            "protected", "public", "reinterpret_cast", "requires", "return", "sizeof",
            "static", "static_assert", "static_cast", "struct", "switch", "template",
            "this", "thread_local", "throw", "true", "try", "typedef", "typeid",
            "typename", "union", "using", "virtual", "volatile", "while", "override", "final"
        )

        internal val CPP_TYPES = listOf(
            "char", "char8_t", "char16_t", "char32_t", "double", "float", "int", "long",
            "short", "signed", "unsigned", "void", "wchar_t", "size_t", "ssize_t",
            "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t",
            "uint64_t", "uintptr_t", "intptr_t", "JNIEXPORT", "JNICALL", "JNIEnv",
            "jobject", "jstring", "jclass", "jint", "jboolean", "jbyte", "jchar",
            "jshort", "jlong", "jfloat", "jdouble", "jbyteArray", "jintArray",
            "std", "string", "vector", "map", "unordered_map", "set", "unordered_set",
            "pair", "tuple", "unique_ptr", "shared_ptr", "weak_ptr", "make_unique",
            "make_shared", "cout", "cin", "endl", "cerr"
        )

        internal val CMAKE_COMMANDS = listOf(
            "cmake_minimum_required", "project", "add_executable", "add_library",
            "target_link_libraries", "target_include_directories", "target_compile_definitions",
            "target_compile_options", "target_sources", "set", "get_property", "set_property",
            "set_target_properties", "if", "else", "elseif", "endif", "foreach", "endforeach",
            "while", "endwhile", "return", "function", "endfunction", "macro", "endmacro",
            "find_package", "find_library", "find_path", "find_file", "find_program",
            "include", "include_directories", "link_directories", "add_subdirectory",
            "message", "option", "list", "file", "string", "math", "configure_file"
        )

        internal val CMAKE_VARIABLES = listOf(
            "CMAKE_CXX_STANDARD", "CMAKE_C_STANDARD", "CMAKE_BUILD_TYPE",
            "CMAKE_CURRENT_SOURCE_DIR", "CMAKE_CURRENT_BINARY_DIR", "CMAKE_SOURCE_DIR",
            "CMAKE_BINARY_DIR", "ANDROID_ABI", "ANDROID_PLATFORM", "ANDROID_NDK"
        )

        internal val MAKE_KEYWORDS = listOf(
            "include", "ifeq", "ifneq", "ifdef", "ifndef", "else", "endif",
            "define", "endef", "export", "unexport", "override", "vpath"
        )

        internal val NDK_MAKE_VARS = listOf(
            "LOCAL_PATH", "LOCAL_MODULE", "LOCAL_SRC_FILES", "LOCAL_C_INCLUDES",
            "LOCAL_CFLAGS", "LOCAL_CPPFLAGS", "LOCAL_CXXFLAGS", "LOCAL_LDLIBS",
            "LOCAL_LDFLAGS", "LOCAL_STATIC_LIBRARIES", "LOCAL_SHARED_LIBRARIES",
            "LOCAL_ALLOW_UNDEFINED_SYMBOLS", "LOCAL_ARM_MODE", "LOCAL_ARM_NEON",
            "BUILD_SHARED_LIBRARY", "BUILD_STATIC_LIBRARY", "BUILD_EXECUTABLE",
            "CLEAR_VARS", "TARGET_ARCH", "TARGET_ARCH_ABI", "TARGET_PLATFORM",
            "APP_ABI", "APP_PLATFORM", "APP_STL", "APP_CPPFLAGS", "APP_CFLAGS",
            "my-dir", "all-subdir-makefiles"
        )

        internal val JAVA_KEYWORDS = listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null",
            "fun", "val", "var", "data", "object", "companion", "override"
        )
    }
}

class PrismAnalyzeManager(
    private val type: PrismLanguageType,
    private val diffRangeProvider: () -> IntRange? = { null }
) : SimpleAnalyzeManager<Unit>() {

    private val cppKeywordsSet = (
        PrismCodeLanguage.CPP_KEYWORDS +
        PrismCodeLanguage.CPP_TYPES
    ).toHashSet()

    private val cmakeCommandsSet = PrismCodeLanguage.CMAKE_COMMANDS.map { it.lowercase() }.toHashSet()
    private val makeKeywordsSet = (PrismCodeLanguage.MAKE_KEYWORDS + PrismCodeLanguage.NDK_MAKE_VARS).toHashSet()
    private val javaKeywordsSet = PrismCodeLanguage.JAVA_KEYWORDS.toHashSet()

    override fun analyze(content: StringBuilder, delegate: Delegate<Unit>): Styles {
        val lines = content.toString().split('\n')
        val totalLines = lines.size
        val builder = MappedSpans.Builder()
        val currentDiff = diffRangeProvider()

        var inBlockComment = false

        fun makeSpanStyle(colorId: Int, isDiff: Boolean): Long {
            return if (isDiff) {
                TextStyle.makeStyle(colorId, EditorColorScheme.STATIC_SPAN_BACKGROUND, false, false, false)
            } else {
                TextStyle.makeStyle(colorId)
            }
        }

        for (lineIndex in 0 until totalLines) {
            if (delegate.isCancelled) {
                return Styles()
            }

            val isDiffLine = currentDiff != null && lineIndex in currentDiff
            val line = lines[lineIndex]
            val len = line.length
            var col = 0

            if (isDiffLine) {
                builder.addIfNeeded(lineIndex, 0, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, true))
            }

            // If we carried over a block comment from the previous line
            if (inBlockComment) {
                builder.addIfNeeded(lineIndex, 0, makeSpanStyle(EditorColorScheme.COMMENT, isDiffLine))
                val endIdx = line.indexOf("*/")
                if (endIdx != -1) {
                    inBlockComment = false
                    col = endIdx + 2
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                } else {
                    continue
                }
            }

            while (col < len) {
                val c = line[col]

                // Whitespace
                if (c == ' ' || c == '\t' || c == '\r') {
                    col++
                    continue
                }

                // Block comment /* ... */
                if (c == '/' && col + 1 < len && line[col + 1] == '*') {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.COMMENT, isDiffLine))
                    val endIdx = line.indexOf("*/", col + 2)
                    if (endIdx != -1) {
                        col = endIdx + 2
                        builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    } else {
                        inBlockComment = true
                        break
                    }
                    continue
                }

                // Line comment // ... (C/C++, Java)
                if (c == '/' && col + 1 < len && line[col + 1] == '/' && type != PrismLanguageType.CMAKE && type != PrismLanguageType.MAKEFILE) {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.COMMENT, isDiffLine))
                    col = len
                    break
                }

                // Hash comment # ... (CMake, Makefile, Shell)
                if (c == '#' && (type == PrismLanguageType.CMAKE || type == PrismLanguageType.MAKEFILE)) {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.COMMENT, isDiffLine))
                    col = len
                    break
                }

                // Preprocessor directive #include, #define (C/C++)
                if (c == '#' && (type == PrismLanguageType.CPP || type == PrismLanguageType.GENERIC)) {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.ANNOTATION, isDiffLine))
                    while (col < len && (Character.isLetterOrDigit(line[col]) || line[col] == '#' || line[col] == '_')) {
                        col++
                    }
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    continue
                }

                // Variable expansion in Makefile $(VAR) or CMake ${VAR}
                if (c == '$' && col + 1 < len && (line[col + 1] == '(' || line[col + 1] == '{')) {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.ANNOTATION, isDiffLine))
                    val closeChar = if (line[col + 1] == '(') ')' else '}'
                    val endIdx = line.indexOf(closeChar, col + 2)
                    if (endIdx != -1) {
                        col = endIdx + 1
                        builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    } else {
                        col = len
                    }
                    continue
                }

                // String literal "..."
                if (c == '"') {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.LITERAL, isDiffLine))
                    col++
                    while (col < len) {
                        if (line[col] == '\\') {
                            col += 2 // skip escaped character
                        } else if (line[col] == '"') {
                            col++
                            break
                        } else {
                            col++
                        }
                    }
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    continue
                }

                // Character literal '...'
                if (c == '\'') {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.LITERAL, isDiffLine))
                    col++
                    while (col < len) {
                        if (line[col] == '\\') {
                            col += 2
                        } else if (line[col] == '\'') {
                            col++
                            break
                        } else {
                            col++
                        }
                    }
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    continue
                }

                // Numbers (hex, dec, float)
                if (Character.isDigit(c)) {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.LITERAL, isDiffLine))
                    while (col < len && (Character.isLetterOrDigit(line[col]) || line[col] == '.')) {
                        col++
                    }
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    continue
                }

                // Identifier or Keyword
                if (Character.isJavaIdentifierStart(c) || (c == '-' && (type == PrismLanguageType.MAKEFILE))) {
                    val startCol = col
                    while (col < len && (Character.isJavaIdentifierPart(line[col]) || (line[col] == '-' && type == PrismLanguageType.MAKEFILE))) {
                        col++
                    }
                    val word = line.substring(startCol, col)

                    val isKeyword = when (type) {
                        PrismLanguageType.CPP -> cppKeywordsSet.contains(word)
                        PrismLanguageType.CMAKE -> cmakeCommandsSet.contains(word.lowercase())
                        PrismLanguageType.MAKEFILE -> makeKeywordsSet.contains(word)
                        PrismLanguageType.JAVA -> javaKeywordsSet.contains(word)
                        else -> cppKeywordsSet.contains(word)
                    }

                    if (isKeyword) {
                        builder.addIfNeeded(lineIndex, startCol, makeSpanStyle(EditorColorScheme.KEYWORD, isDiffLine))
                        builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    } else {
                        // Check if function call e.g. functionName(...)
                        var lookAhead = col
                        while (lookAhead < len && (line[lookAhead] == ' ' || line[lookAhead] == '\t')) {
                            lookAhead++
                        }
                        if (lookAhead < len && line[lookAhead] == '(') {
                            builder.addIfNeeded(lineIndex, startCol, makeSpanStyle(EditorColorScheme.FUNCTION_NAME, isDiffLine))
                            builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                        }
                    }
                    continue
                }

                // Operators
                if (c in "+-*/%=<>!&|^~?:") {
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.OPERATOR, isDiffLine))
                    col++
                    if (col < len && line[col] in "=+-&|<>") {
                        col++
                    }
                    builder.addIfNeeded(lineIndex, col, makeSpanStyle(EditorColorScheme.TEXT_NORMAL, isDiffLine))
                    continue
                }

                // Standard characters
                col++
            }
        }

        builder.determine(totalLines)
        return Styles(builder.build())
    }
}
