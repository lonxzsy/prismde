package com.prismde.feature_build.engine

import com.prismde.core.model.DiagnosticSeverity

data class ExplanationResult(
    val title: String,
    val explanation: String,
    val offlineHint: String? = null,
    val suggestedFix: String? = null
)

object HumanExplanationEngine {

    /**
     * Translates raw Clang / GCC compiler messages into clear, human-friendly
     * explanations in English or Russian, providing actionable hints and auto-fix suggestions where applicable.
     */
    fun explain(rawMessage: String, severity: DiagnosticSeverity = DiagnosticSeverity.ERROR): ExplanationResult {
        val trimmed = rawMessage.trim()
        val lower = trimmed.lowercase()
        val isRu = java.util.Locale.getDefault().language == "ru"

        return when {
            // Unmatched brackets, parentheses, braces
            lower.contains("expected ')'") || lower.contains("to match this '('") -> {
                val isNote = severity == DiagnosticSeverity.NOTE || lower.startsWith("to match this")
                if (isNote) {
                    ExplanationResult(
                        title = if (isRu) "Парная открывающая скобка '('" else "Matching opening parenthesis '('",
                        explanation = if (isRu)
                            "Компилятор указывает на открывающую круглую скобку '(', для которой в коде не найдена закрывающая пара ')'."
                        else
                            "The compiler points to an opening parenthesis '(' that lacks a closing pair ')' in code.",
                        offlineHint = if (isRu)
                            "• Проверьте эту строку: здесь открывается скобка, которую забыли закрыть.\n• Подсчитайте количество '(' и ')' в выражении или вызове функции."
                        else
                            "• Check this line: a parenthesis opened here was not closed.\n• Count '(' and ')' in the expression or function call."
                    )
                } else {
                    ExplanationResult(
                        title = if (isRu) "Пропущена закрывающая скобка ')'" else "Missing closing parenthesis ')'",
                        explanation = if (isRu)
                            "В выражении или вызове функции отсутствует закрывающая круглая скобка ')'."
                        else
                            "A closing parenthesis ')' is missing in the expression or function call.",
                        offlineHint = if (isRu)
                            "• Добавьте закрывающую скобку ')' в конце аргументов или условия.\n• Убедитесь, что внутри скобок нет пропущенных запятых или лишних символов."
                        else
                            "• Add a closing ')' at the end of arguments or condition.\n• Ensure no commas or brackets are missing inside.",
                        suggestedFix = ")"
                    )
                }
            }

            lower.contains("expected '}'") || lower.contains("to match this '{'") -> {
                val isNote = severity == DiagnosticSeverity.NOTE || lower.startsWith("to match this")
                if (isNote) {
                    ExplanationResult(
                        title = if (isRu) "Парная открывающая фигурная скобка '{'" else "Matching opening brace '{'",
                        explanation = if (isRu)
                            "Компилятор указывает на блок кода, начатый здесь фигурной скобкой '{', который не был закрыт."
                        else
                            "The compiler points to a code block started here with '{' that was not closed.",
                        offlineHint = if (isRu)
                            "• Убедитесь, что для каждой открывающей скобки '{' есть закрывающая '}' в конце блока."
                        else
                            "• Ensure every opening brace '{' has a corresponding closing brace '}' at the end of the block."
                    )
                } else {
                    ExplanationResult(
                        title = if (isRu) "Пропущена закрывающая скобка '}'" else "Missing closing brace '}'",
                        explanation = if (isRu)
                            "Тело функции, цикла, условия или класса не закрыто фигурной скобкой '}'."
                        else
                            "The body of a function, loop, conditional, or class is not closed with '}'.",
                        offlineHint = if (isRu)
                            "• Добавьте '}' в конце блока или структуры.\n• Если закрывающая скобка уже есть ниже, проверьте, не была ли случайно удалена скобка в промежуточном блоке."
                        else
                            "• Add '}' at the end of the block or structure.\n• If a closing brace is already present below, check for an unclosed inner block.",
                        suggestedFix = "}"
                    )
                }
            }

            lower.contains("expected ']'") || lower.contains("to match this '['") -> {
                ExplanationResult(
                    title = if (isRu) "Пропущена закрывающая квадратная скобка ']'" else "Missing closing bracket ']'",
                    explanation = if (isRu)
                        "Индекс массива или список захвата лямбда-выражения не закрыт квадратной скобкой ']'."
                    else
                        "Array subscript or lambda capture list is missing a closing bracket ']'.",
                    offlineHint = if (isRu)
                        "• Добавьте ']' после индекса элемента (например: array[index])."
                    else
                        "• Add ']' after the subscript expression (e.g.: array[index]).",
                    suggestedFix = "]"
                )
            }

            // Semicolon missing
            lower.contains("expected ';'") || lower.contains("expected ';' after") -> {
                ExplanationResult(
                    title = if (isRu) "Пропущена точка с запятой (;)" else "Missing semicolon (;)",
                    explanation = if (isRu)
                        "В языке C/C++ каждая инструкция или объявление переменной должны заканчиваться символом ';'."
                    else
                        "In C/C++, every statement or variable declaration must terminate with a semicolon ';'.",
                    offlineHint = if (isRu)
                        "• Добавьте ';' в конце указанной инструкции.\n• Если ошибка указывает на начало строки, точка с запятой скорее всего пропущена в конце ПРЕДЫДУЩЕЙ строки.\n• После объявления struct, class или enum перед закрывающей скобкой также обязательна точка с запятой: '};'."
                    else
                        "• Add ';' at the end of the statement.\n• If the error points to the beginning of a line, the semicolon is likely missing at the end of the PREVIOUS line.\n• Struct, class, and enum declarations also require a trailing semicolon: '};'.",
                    suggestedFix = ";"
                )
            }

            // Undeclared identifier
            lower.contains("use of undeclared identifier") -> {
                val identifier = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: (if (isRu) "переменная" else "variable")
                val (hint, fix) = when (identifier) {
                    "cout", "cin", "endl" -> Pair(
                        if (isRu) "• Добавьте директиву '#include <iostream>' в начало файла.\n• Используйте префикс 'std::$identifier' или добавьте 'using namespace std;'."
                        else "• Add '#include <iostream>' at the top of the file.\n• Use 'std::$identifier' prefix or add 'using namespace std;'.",
                        "std::$identifier"
                    )
                    "string" -> Pair(
                        if (isRu) "• Добавьте директиву '#include <string>' в начало файла.\n• Используйте 'std::string'."
                        else "• Add '#include <string>' at the top of the file.\n• Use 'std::string'.",
                        "std::string"
                    )
                    "vector" -> Pair(
                        if (isRu) "• Добавьте директиву '#include <vector>' в начало файла.\n• Используйте 'std::vector<Type>'."
                        else "• Add '#include <vector>' at the top of the file.\n• Use 'std::vector<Type>'.",
                        "std::vector"
                    )
                    "map", "unordered_map" -> Pair(
                        if (isRu) "• Добавьте директиву '#include <map>' (или '#include <unordered_map>').\n• Используйте 'std::$identifier'."
                        else "• Add '#include <map>' (or '#include <unordered_map>').\n• Use 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "unique_ptr", "shared_ptr", "make_unique", "make_shared" -> Pair(
                        if (isRu) "• Добавьте '#include <memory>'.\n• Используйте 'std::$identifier'."
                        else "• Add '#include <memory>'.\n• Use 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "printf", "scanf", "sprintf", "snprintf" -> Pair(
                        if (isRu) "• Подключите заголовок '#include <cstdio>' или '#include <stdio.h>'."
                        else "• Include header '#include <cstdio>' or '#include <stdio.h>'.",
                        "#include <cstdio>"
                    )
                    "malloc", "free", "exit", "atoi" -> Pair(
                        if (isRu) "• Подключите заголовок '#include <cstdlib>' или '#include <stdlib.h>'."
                        else "• Include header '#include <cstdlib>' or '#include <stdlib.h>'.",
                        "#include <cstdlib>"
                    )
                    "memset", "memcpy", "strlen", "strcpy" -> Pair(
                        if (isRu) "• Подключите заголовок '#include <cstring>' или '#include <string.h>'."
                        else "• Include header '#include <cstring>' or '#include <string.h>'.",
                        "#include <cstring>"
                    )
                    "sqrt", "pow", "abs", "sin", "cos", "floor", "ceil" -> Pair(
                        if (isRu) "• Подключите заголовок '#include <cmath>' или '#include <math.h>'."
                        else "• Include header '#include <cmath>' or '#include <math.h>'.",
                        "#include <cmath>"
                    )
                    "min", "max", "sort", "find", "clamp" -> Pair(
                        if (isRu) "• Подключите заголовок '#include <algorithm>'.\n• Используйте 'std::$identifier'."
                        else "• Include header '#include <algorithm>'.\n• Use 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int32_t", "int64_t", "size_t" -> Pair(
                        if (isRu) "• Подключите заголовок целых типов фиксированного размера: '#include <cstdint>'."
                        else "• Include fixed-width integer header: '#include <cstdint>'.",
                        "#include <cstdint>"
                    )
                    "JNIEXPORT", "JNICALL", "JNIEnv", "jobject", "jstring", "jclass", "jint", "jboolean" -> Pair(
                        if (isRu) "• Для нативных функций Android JNI подключите '#include <jni.h>'."
                        else "• For Android JNI native functions, include '#include <jni.h>'.",
                        "#include <jni.h>"
                    )
                    "__android_log_print", "ANDROID_LOG_INFO", "ANDROID_LOG_ERROR" -> Pair(
                        if (isRu) "• Подключите '#include <android/log.h>'.\n• Убедитесь, что в Android.mk добавлено 'LOCAL_LDLIBS := -llog' или в CMake: 'find_library(log-lib log)'."
                        else "• Include '#include <android/log.h>'.\n• Ensure 'LOCAL_LDLIBS := -llog' in Android.mk or 'target_link_libraries(... log)' in CMake.",
                        "#include <android/log.h>"
                    )
                    else -> Pair(
                        if (isRu) "• Проверьте правильность написания: C++ чувствителен к регистру букв (myVar != MyVar).\n• Убедитесь, что переменная объявлена до её вызова.\n• Проверьте область видимости (scope) переменной."
                        else "• Check spelling: C++ is case-sensitive (myVar != MyVar).\n• Ensure the variable is declared before use.\n• Check variable scope.",
                        null
                    )
                }
                ExplanationResult(
                    title = if (isRu) "Неизвестный идентификатор: '$identifier'" else "Undeclared identifier: '$identifier'",
                    explanation = if (isRu)
                        "Компилятор не нашел объявления для '$identifier'. Имя не существует в текущей области видимости, либо забыт заголовочный файл."
                    else
                        "The compiler could not find a declaration for '$identifier'. It either does not exist in scope or a required header was omitted.",
                    offlineHint = hint,
                    suggestedFix = fix
                )
            }

            // Typo suggestion
            lower.contains("did you mean") -> {
                val candidate = Regex("did you mean '(.+?)'").find(trimmed)?.groupValues?.get(1)
                ExplanationResult(
                    title = if (isRu) "Возможная опечатка в имени" else "Possible typo in symbol name",
                    explanation = if (isRu)
                        "Компилятор предполагает, что вы имели в виду '$candidate'."
                    else
                        "The compiler suggests you might have meant '$candidate'.",
                    offlineHint = if (isRu)
                        "• Замените ошибочное имя на предложенное: '$candidate'."
                    else
                        "• Replace the mistaken identifier with the suggested: '$candidate'.",
                    suggestedFix = candidate
                )
            }

            // File not found
            lower.contains("file not found") || lower.contains("cannot open source file") || lower.contains("fatal error: '") -> {
                val headerName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: (if (isRu) "заголовок" else "header")
                ExplanationResult(
                    title = if (isRu) "Заголовочный файл не найден: '$headerName'" else "Header file not found: '$headerName'",
                    explanation = if (isRu)
                        "Компилятор не смог найти файл '$headerName' ни в папке проекта, ни в системных путях NDK."
                    else
                        "The compiler could not find '$headerName' in the project directory or NDK include paths.",
                    offlineHint = if (isRu)
                        "• Проверьте регистр букв и расширение файла (например .h или .hpp).\n• Для локальных файлов проекта используйте кавычки: #include \"$headerName\".\n• Если файл находится в подпапке, укажите относительный путь: #include \"include/$headerName\".\n• Проверьте пути include_directories в CMakeLists.txt или LOCAL_C_INCLUDES в Android.mk."
                    else
                        "• Check file case and extension (e.g. .h or .hpp).\n• For local project files use double quotes: #include \"$headerName\".\n• If the file is in a subdirectory, specify the relative path: #include \"include/$headerName\".\n• Verify include directories in CMakeLists.txt or Android.mk."
                )
            }

            // Linker undefined reference
            lower.contains("undefined reference to") -> {
                val symbol = Regex("to '(.+?)'").find(trimmed)?.groupValues?.get(1) ?: (if (isRu) "символ" else "symbol")
                val hint = when {
                    symbol.contains("android_") || symbol.contains("__android_log") ->
                        if (isRu) "• Подключите библиотеку логирования Android: добавьте флаг -llog (или LOCAL_LDLIBS += -llog в Android.mk, target_link_libraries(target log) в CMake)."
                        else "• Link Android logging library: add -llog flag (or LOCAL_LDLIBS += -llog in Android.mk, target_link_libraries(target log) in CMake)."
                    symbol.contains("pthread_") ->
                        if (isRu) "• Для многопоточности POSIX добавьте флаг линковщика -pthread."
                        else "• For POSIX multithreading, add linker flag -pthread."
                    symbol.contains("vk") || symbol.contains("vulkan") ->
                        if (isRu) "• Для Vulkan API подключите библиотеку -lvulkan."
                        else "• For Vulkan API, link library -lvulkan."
                    symbol.contains("gl") || symbol.contains("egl") ->
                        if (isRu) "• Для графики OpenGL ES подключите библиотеки -lGLESv2 -lGLESv3 -lEGL."
                        else "• For OpenGL ES graphics, link libraries -lGLESv2 -lGLESv3 -lEGL."
                    symbol.contains("ANativeActivity") || symbol.contains("AAssetManager") ->
                        if (isRu) "• Подключите библиотеку Android NDK: добавьте -landroid."
                        else "• Link Android NDK platform library: add -landroid."
                    else ->
                        if (isRu) "• Функция объявлена в .h файле, но её реализация не скомпилирована.\n• Убедитесь, что соответствующий .c/.cpp файл добавлен в список исходников сборки.\n• Если функция из внешней библиотеки, проверьте, подключен ли соответствующий .so или .a файл."
                        else "• The function is declared in a header, but its definition was not compiled.\n• Ensure the .c/.cpp file is included in project sources.\n• If from an external library, ensure the corresponding .so or .a library is linked."
                }
                ExplanationResult(
                    title = if (isRu) "Ошибка компоновщика (Undefined reference): '$symbol'" else "Linker error (Undefined reference): '$symbol'",
                    explanation = if (isRu)
                        "Компиляция прошла успешно, но на этапе линковки реализация функции '$symbol' не была найдена."
                    else
                        "Compilation succeeded, but the linker could not find the definition of '$symbol'.",
                    offlineHint = hint
                )
            }

            // Type conversion / mismatch
            lower.contains("cannot initialize a variable of type") || lower.contains("cannot convert") || lower.contains("no viable conversion") -> {
                ExplanationResult(
                    title = if (isRu) "Несовместимость типов данных" else "Type conversion mismatch",
                    explanation = if (isRu)
                        "Попытка присвоить значение одного типа переменной другого несовместимого типа без явного преобразования."
                    else
                        "Attempting to assign a value to an incompatible type without explicit conversion.",
                    offlineHint = if (isRu)
                        "• Проверьте типы переменной и присваиваемого выражения.\n• Используйте явное приведение типов: static_cast<НужныйТип>(значение).\n• Если один из типов — указатель, убедитесь, не пропущено ли разыменование (*ptr) или взятие адреса (&var)."
                    else
                        "• Check variable and expression types.\n• Use explicit type casting: static_cast<TargetType>(value).\n• If dealing with pointers, ensure dereferencing (*ptr) or address-of (&var) is not missing."
                )
            }

            // Function call signature mismatch
            lower.contains("no matching function for call to") -> {
                ExplanationResult(
                    title = if (isRu) "Не найдена подходящая перегрузка функции" else "No matching function overload found",
                    explanation = if (isRu)
                        "Вызов функции не совпадает ни с одной из её объявленных перегрузок (не совпадает количество аргументов или их типы)."
                    else
                        "The function call does not match any declared overload (argument count or parameter types differ).",
                    offlineHint = if (isRu)
                        "• Проверьте количество переданных параметров.\n• Проверьте типы каждого аргумента (например, const char* вместо std::string, или float вместо int).\n• Сверьте вызов с сигнатурой функции в её объявлении."
                    else
                        "• Verify the number of arguments passed.\n• Check parameter types (e.g., const char* vs std::string, or float vs int).\n• Compare call with the function declaration signature."
                )
            }

            // Member access error
            lower.contains("member reference base type") || lower.contains("did you mean to use '->'") -> {
                ExplanationResult(
                    title = if (isRu) "Неверный оператор доступа к члену класса" else "Invalid member access operator",
                    explanation = if (isRu)
                        "Использован оператор точки '.' для указателя, либо оператор стрелки '->' для обычного объекта."
                    else
                        "Used dot operator '.' on a pointer or arrow operator '->' on a value/reference object.",
                    offlineHint = if (isRu)
                        "• Для указателей используйте стрелку: pointer->member.\n• Для обычных объектов и ссылок используйте точку: object.member."
                    else
                        "• Use arrow operator for pointers: pointer->member.\n• Use dot operator for direct objects/references: object.member.",
                    suggestedFix = "->"
                )
            }

            // Incomplete type
            lower.contains("incomplete type") || lower.contains("has incomplete type") -> {
                ExplanationResult(
                    title = if (isRu) "Использование неполного типа (Incomplete Type)" else "Incomplete type usage",
                    explanation = if (isRu)
                        "Класс или структура были объявлены только опережающим объявлением (forward declaration), но компилятору требуется полное определение (размер или вызов методов)."
                    else
                        "Class or struct was forward declared, but the compiler requires complete definition (for object size or method calls).",
                    offlineHint = if (isRu)
                        "• Подключите заголовочный файл (#include) с полным определением этого класса или структуры."
                    else
                        "• Include the header file (#include) containing the full class or struct definition."
                )
            }

            // Expression is not assignable / lvalue
            lower.contains("expression is not assignable") || lower.contains("cannot assign to return value") -> {
                ExplanationResult(
                    title = if (isRu) "Выражению нельзя присвоить значение" else "Expression is not assignable",
                    explanation = if (isRu)
                        "Слева от знака '=' находится константа, временный объект или результат функции (rvalue), в который нельзя производить запись."
                    else
                        "Left side of '=' is a constant, temporary, or rvalue function return that cannot be assigned to.",
                    offlineHint = if (isRu)
                        "• Присваивать значение можно только переменной (lvalue).\n• Убедитесь, что вы не спутали оператор сравнения '==' с оператором присваивания '='."
                    else
                        "• Values can only be assigned to writable lvalues.\n• Check if equality comparison '==' was intended instead of '='."
                )
            }

            // Expected expression
            lower.contains("expected expression") || lower.contains("expected unqualified-id") -> {
                ExplanationResult(
                    title = if (isRu) "Синтаксическая ошибка: ожидалось выражение" else "Syntax error: expected expression",
                    explanation = if (isRu)
                        "Компилятор ожидал увидеть переменную, константу или операцию, но встретил неожиданный символ."
                    else
                        "The compiler expected a variable, constant, or operation, but encountered an unexpected token.",
                    offlineHint = if (isRu)
                        "• Проверьте синтаксис вокруг указанного символа.\n• Проверьте, нет ли лишней запятой, двойного оператора или незакрытой скобки."
                    else
                        "• Check syntax around the indicated token.\n• Check for trailing commas, duplicate operators, or unclosed parentheses."
                )
            }

            // Redefinition
            lower.contains("redefinition of") -> {
                val name = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: (if (isRu) "элемента" else "element")
                ExplanationResult(
                    title = if (isRu) "Повторное объявление: '$name'" else "Redefinition: '$name'",
                    explanation = if (isRu)
                        "Элемент '$name' уже был объявлен ранее в этом или подключенном файле."
                    else
                        "The symbol '$name' was already defined in this or an included file.",
                    offlineHint = if (isRu)
                        "• Добавьте директиву '#pragma once' в самую первую строчку заголовочного файла, чтобы избежать его повторного включения.\n• Убедитесь, что имя переменной или функции не дублируется."
                    else
                        "• Add '#pragma once' at the top of the header file to prevent multiple inclusions.\n• Ensure variable or function names are not duplicated.",
                    suggestedFix = "#pragma once"
                )
            }

            // Missing return
            lower.contains("control reaches end of non-void function") -> {
                ExplanationResult(
                    title = if (isRu) "Отсутствует return в функции" else "Missing return statement",
                    explanation = if (isRu)
                        "Функция объявлена с возвращаемым типом (не void), однако в одной или нескольких ветках выполнения отсутствует инструкция 'return'."
                    else
                        "Non-void function is missing a 'return' statement in one or more code paths.",
                    offlineHint = if (isRu)
                        "• Добавьте 'return <значение>;' в конце тела функции и внутри всех веток if/else."
                    else
                        "• Add 'return <value>;' at the end of the function body and in every if/else branch.",
                    suggestedFix = "return 0;"
                )
            }

            // Unused variable warning
            lower.contains("unused variable") -> {
                val varName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: (if (isRu) "переменная" else "variable")
                ExplanationResult(
                    title = if (isRu) "Неиспользуемая переменная: '$varName'" else "Unused variable: '$varName'",
                    explanation = if (isRu)
                        "Переменная '$varName' была объявлена, но нигде не используется."
                    else
                        "Variable '$varName' is declared but never read or referenced.",
                    offlineHint = if (isRu)
                        "• Удалите объявление переменной, если она больше не нужна.\n• Либо добавьте '(void)$varName;' для подавления предупреждения."
                    else
                        "• Remove the variable declaration if it is unneeded.\n• Or add '(void)$varName;' to suppress the warning."
                )
            }

            // Default fallback
            else -> {
                val defaultTitle = when (severity) {
                    DiagnosticSeverity.FATAL, DiagnosticSeverity.ERROR -> if (isRu) "Ошибка компилятора" else "Compiler Error"
                    DiagnosticSeverity.WARNING -> if (isRu) "Предупреждение компилятора" else "Compiler Warning"
                    DiagnosticSeverity.NOTE -> if (isRu) "Пояснение компилятора" else "Compiler Note"
                }

                // If rawMessage contains clean descriptive text, format it nicely
                val humanClean = trimmed
                    .replace("error: ", "")
                    .replace("warning: ", "")
                    .replace("note: ", "")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                ExplanationResult(
                    title = defaultTitle,
                    explanation = humanClean,
                    offlineHint = if (isRu)
                        "• Проверьте синтаксис строки вокруг указанной позиции.\n• Убедитесь, что все открытые скобки () {} [] и кавычки парные.\n• Проверьте наличие точки с запятой ';' в конце выражения."
                    else
                        "• Check syntax around the reported position.\n• Ensure matching brackets () {} [] and quotes.\n• Check for missing semicolon ';' at the end of statements."
                )
            }
        }
    }
}
