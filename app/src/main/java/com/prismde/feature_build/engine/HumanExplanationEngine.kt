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
     * Translates raw Clang / GCC compiler messages into clear, human-friendly Russian
     * explanations, providing actionable hints and auto-fix suggestions where applicable.
     */
    fun explain(rawMessage: String, severity: DiagnosticSeverity = DiagnosticSeverity.ERROR): ExplanationResult {
        val trimmed = rawMessage.trim()
        val lower = trimmed.lowercase()

        return when {
            // Unmatched brackets, parentheses, braces
            lower.contains("expected ')'") || lower.contains("to match this '('") -> {
                val isNote = severity == DiagnosticSeverity.NOTE || lower.startsWith("to match this")
                if (isNote) {
                    ExplanationResult(
                        title = "Парная открывающая скобка '('",
                        explanation = "Компилятор указывает на открывающую круглую скобку '(', для которой в коде не найдена закрывающая пара ')'.",
                        offlineHint = "• Проверьте эту строку: здесь открывается скобка, которую забыли закрыть.\n• Подсчитайте количество '(' и ')' в выражении или вызове функции."
                    )
                } else {
                    ExplanationResult(
                        title = "Пропущена закрывающая скобка ')'",
                        explanation = "В выражении или вызове функции отсутствует закрывающая круглая скобка ')'.",
                        offlineHint = "• Добавьте закрывающую скобку ')' в конце аргументов или условия.\n• Убедитесь, что внутри скобок нет пропущенных запятых или лишних символов.",
                        suggestedFix = ")"
                    )
                }
            }

            lower.contains("expected '}'") || lower.contains("to match this '{'") -> {
                val isNote = severity == DiagnosticSeverity.NOTE || lower.startsWith("to match this")
                if (isNote) {
                    ExplanationResult(
                        title = "Парная открывающая фигурная скобка '{'",
                        explanation = "Компилятор указывает на блок кода, начатый здесь фигурной скобкой '{', который не был закрыт.",
                        offlineHint = "• Убедитесь, что для каждой открывающей скобки '{' есть закрывающая '}' в конце блока."
                    )
                } else {
                    ExplanationResult(
                        title = "Пропущена закрывающая скобка '}'",
                        explanation = "Тело функции, цикла, условия или класса не закрыто фигурной скобкой '}'.",
                        offlineHint = "• Добавьте '}' в конце блока или структуры.\n• Если закрывающая скобка уже есть ниже, проверьте, не была ли случайно удалена скобка в промежуточном блоке.",
                        suggestedFix = "}"
                    )
                }
            }

            lower.contains("expected ']'") || lower.contains("to match this '['") -> {
                ExplanationResult(
                    title = "Пропущена закрывающая квадратная скобка ']'",
                    explanation = "Индекс массива или список захвата лямбда-выражения не закрыт квадратной скобкой ']'.",
                    offlineHint = "• Добавьте ']' после индекса элемента (например: array[index]).",
                    suggestedFix = "]"
                )
            }

            // Semicolon missing
            lower.contains("expected ';'") || lower.contains("expected ';' after") -> {
                ExplanationResult(
                    title = "Пропущена точка с запятой (;)",
                    explanation = "В языке C/C++ каждая инструкция или объявление переменной должны заканчиваться символом ';'.",
                    offlineHint = "• Добавьте ';' в конце указанной инструкции.\n• Если ошибка указывает на начало строки, точка с запятой скорее всего пропущена в конце ПРЕДЫДУЩЕЙ строки.\n• После объявления struct, class или enum перед закрывающей скобкой также обязательна точка с запятой: '};'.",
                    suggestedFix = ";"
                )
            }

            // Undeclared identifier
            lower.contains("use of undeclared identifier") -> {
                val identifier = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "переменная"
                val (hint, fix) = when (identifier) {
                    "cout", "cin", "endl" -> Pair(
                        "• Добавьте директиву '#include <iostream>' в начало файла.\n• Используйте префикс 'std::$identifier' или добавьте 'using namespace std;'.",
                        "std::$identifier"
                    )
                    "string" -> Pair(
                        "• Добавьте директиву '#include <string>' в начало файла.\n• Используйте 'std::string'.",
                        "std::string"
                    )
                    "vector" -> Pair(
                        "• Добавьте директиву '#include <vector>' в начало файла.\n• Используйте 'std::vector<Type>'.",
                        "std::vector"
                    )
                    "map", "unordered_map" -> Pair(
                        "• Добавьте директиву '#include <map>' (или '#include <unordered_map>').\n• Используйте 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "unique_ptr", "shared_ptr", "make_unique", "make_shared" -> Pair(
                        "• Добавьте '#include <memory>'.\n• Используйте 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "printf", "scanf", "sprintf", "snprintf" -> Pair(
                        "• Подключите заголовок '#include <cstdio>' или '#include <stdio.h>'.",
                        "#include <cstdio>"
                    )
                    "malloc", "free", "exit", "atoi" -> Pair(
                        "• Подключите заголовок '#include <cstdlib>' или '#include <stdlib.h>'.",
                        "#include <cstdlib>"
                    )
                    "memset", "memcpy", "strlen", "strcpy" -> Pair(
                        "• Подключите заголовок '#include <cstring>' или '#include <string.h>'.",
                        "#include <cstring>"
                    )
                    "sqrt", "pow", "abs", "sin", "cos", "floor", "ceil" -> Pair(
                        "• Подключите заголовок '#include <cmath>' или '#include <math.h>'.",
                        "#include <cmath>"
                    )
                    "min", "max", "sort", "find", "clamp" -> Pair(
                        "• Подключите заголовок '#include <algorithm>'.\n• Используйте 'std::$identifier'.",
                        "std::$identifier"
                    )
                    "uint8_t", "uint16_t", "uint32_t", "uint64_t", "int32_t", "int64_t", "size_t" -> Pair(
                        "• Подключите заголовок целых типов фиксированного размера: '#include <cstdint>'.",
                        "#include <cstdint>"
                    )
                    "JNIEXPORT", "JNICALL", "JNIEnv", "jobject", "jstring", "jclass", "jint", "jboolean" -> Pair(
                        "• Для нативных функций Android JNI подключите '#include <jni.h>'.",
                        "#include <jni.h>"
                    )
                    "__android_log_print", "ANDROID_LOG_INFO", "ANDROID_LOG_ERROR" -> Pair(
                        "• Подключите '#include <android/log.h>'.\n• Убедитесь, что в Android.mk добавлено 'LOCAL_LDLIBS := -llog' или в CMake: 'find_library(log-lib log)'.",
                        "#include <android/log.h>"
                    )
                    else -> Pair(
                        "• Проверьте правильность написания: C++ чувствителен к регистру букв (myVar != MyVar).\n• Убедитесь, что переменная объявлена до её вызова.\n• Проверьте область видимости (scope) переменной.",
                        null
                    )
                }
                ExplanationResult(
                    title = "Неизвестный идентификатор: '$identifier'",
                    explanation = "Компилятор не нашел объявления для '$identifier'. Имя не существует в текущей области видимости, либо забыт заголовочный файл.",
                    offlineHint = hint,
                    suggestedFix = fix
                )
            }

            // Typo suggestion
            lower.contains("did you mean") -> {
                val candidate = Regex("did you mean '(.+?)'").find(trimmed)?.groupValues?.get(1)
                ExplanationResult(
                    title = "Возможная опечатка в имени",
                    explanation = "Компилятор предполагает, что вы имели в виду '$candidate'.",
                    offlineHint = "• Замените ошибочное имя на предложенное: '$candidate'.",
                    suggestedFix = candidate
                )
            }

            // File not found
            lower.contains("file not found") || lower.contains("cannot open source file") || lower.contains("fatal error: '") -> {
                val headerName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "заголовок"
                ExplanationResult(
                    title = "Заголовочный файл не найден: '$headerName'",
                    explanation = "Компилятор не смог найти файл '$headerName' ни в папке проекта, ни в системных путях NDK.",
                    offlineHint = "• Проверьте регистр букв и расширение файла (например .h или .hpp).\n• Для локальных файлов проекта используйте кавычки: #include \"$headerName\".\n• Если файл находится в подпапке, укажите относительный путь: #include \"include/$headerName\".\n• Проверьте пути include_directories в CMakeLists.txt или LOCAL_C_INCLUDES в Android.mk."
                )
            }

            // Linker undefined reference
            lower.contains("undefined reference to") -> {
                val symbol = Regex("to '(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "символ"
                val hint = when {
                    symbol.contains("android_") || symbol.contains("__android_log") ->
                        "• Подключите библиотеку логирования Android: добавьте флаг -llog (или LOCAL_LDLIBS += -llog в Android.mk, target_link_libraries(target log) в CMake)."
                    symbol.contains("pthread_") ->
                        "• Для многопоточности POSIX добавьте флаг линковщика -pthread."
                    symbol.contains("vk") || symbol.contains("vulkan") ->
                        "• Для Vulkan API подключите библиотеку -lvulkan."
                    symbol.contains("gl") || symbol.contains("egl") ->
                        "• Для графики OpenGL ES подключите библиотеки -lGLESv2 -lGLESv3 -lEGL."
                    symbol.contains("ANativeActivity") || symbol.contains("AAssetManager") ->
                        "• Подключите библиотеку Android NDK: добавьте -landroid."
                    else ->
                        "• Функция объявлена в .h файле, но её реализация не скомпилирована.\n• Убедитесь, что соответствующий .c/.cpp файл добавлен в список исходников сборки.\n• Если функция из внешней библиотеки, проверьте, подключен ли соответствующий .so или .a файл."
                }
                ExplanationResult(
                    title = "Ошибка компоновщика (Undefined reference): '$symbol'",
                    explanation = "Компиляция прошла успешно, но на этапе линковки реализация функции '$symbol' не была найдена.",
                    offlineHint = hint
                )
            }

            // Type conversion / mismatch
            lower.contains("cannot initialize a variable of type") || lower.contains("cannot convert") || lower.contains("no viable conversion") -> {
                ExplanationResult(
                    title = "Несовместимость типов данных",
                    explanation = "Попытка присвоить значение одного типа переменной другого несовместимого типа без явного преобразования.",
                    offlineHint = "• Проверьте типы переменной и присваиваемого выражения.\n• Используйте явное приведение типов: static_cast<НужныйТип>(значение).\n• Если один из типов — указатель, убедитесь, не пропущено ли разыменование (*ptr) или взятие адреса (&var)."
                )
            }

            // Function call signature mismatch
            lower.contains("no matching function for call to") -> {
                ExplanationResult(
                    title = "Не найдена подходящая перегрузка функции",
                    explanation = "Вызов функции не совпадает ни с одной из её объявленных перегрузок (не совпадает количество аргументов или их типы).",
                    offlineHint = "• Проверьте количество переданных параметров.\n• Проверьте типы каждого аргумента (например, const char* вместо std::string, или float вместо int).\n• Сверьте вызов с сигнатурой функции в её объявлении."
                )
            }

            // Member access error
            lower.contains("member reference base type") || lower.contains("did you mean to use '->'") -> {
                ExplanationResult(
                    title = "Неверный оператор доступа к члену класса",
                    explanation = "Использован оператор точки '.' для указателя, либо оператор стрелки '->' для обычного объекта.",
                    offlineHint = "• Для указателей используйте стрелку: pointer->member.\n• Для обычных объектов и ссылок используйте точку: object.member.",
                    suggestedFix = "->"
                )
            }

            // Incomplete type
            lower.contains("incomplete type") || lower.contains("has incomplete type") -> {
                ExplanationResult(
                    title = "Использование неполного типа (Incomplete Type)",
                    explanation = "Класс или структура были объявлены только опережающим объявлением (forward declaration), но компилятору требуется полное определение (размер или вызов методов).",
                    offlineHint = "• Подключите заголовочный файл (#include) с полным определением этого класса или структуры."
                )
            }

            // Expression is not assignable / lvalue
            lower.contains("expression is not assignable") || lower.contains("cannot assign to return value") -> {
                ExplanationResult(
                    title = "Выражению нельзя присвоить значение",
                    explanation = "Слева от знака '=' находится константа, временный объект или результат функции (rvalue), в который нельзя производить запись.",
                    offlineHint = "• Присваивать значение можно только переменной (lvalue).\n• Убедитесь, что вы не спутали оператор сравнения '==' с оператором присваивания '='."
                )
            }

            // Expected expression
            lower.contains("expected expression") || lower.contains("expected unqualified-id") -> {
                ExplanationResult(
                    title = "Синтаксическая ошибка: ожидалось выражение",
                    explanation = "Компилятор ожидал увидеть переменную, константу или операцию, но встретил неожиданный символ.",
                    offlineHint = "• Проверьте синтаксис вокруг указанного символа.\n• Проверьте, нет ли лишней запятой, двойного оператора или незакрытой скобки."
                )
            }

            // Redefinition
            lower.contains("redefinition of") -> {
                val name = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "элемента"
                ExplanationResult(
                    title = "Повторное объявление: '$name'",
                    explanation = "Элемент '$name' уже был объявлен ранее в этом или подключенном файле.",
                    offlineHint = "• Добавьте директиву '#pragma once' в самую первую строчку заголовочного файла, чтобы избежать его повторного включения.\n• Убедитесь, что имя переменной или функции не дублируется.",
                    suggestedFix = "#pragma once"
                )
            }

            // Missing return
            lower.contains("control reaches end of non-void function") -> {
                ExplanationResult(
                    title = "Отсутствует return в функции",
                    explanation = "Функция объявлена с возвращаемым типом (не void), однако в одной или нескольких ветках выполнения отсутствует инструкция 'return'.",
                    offlineHint = "• Добавьте 'return <значение>;' в конце тела функции и внутри всех веток if/else.",
                    suggestedFix = "return 0;"
                )
            }

            // Unused variable warning
            lower.contains("unused variable") -> {
                val varName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "переменная"
                ExplanationResult(
                    title = "Неиспользуемая переменная: '$varName'",
                    explanation = "Переменная '$varName' была объявлена, но нигде не используется.",
                    offlineHint = "• Удалите объявление переменной, если она больше не нужна.\n• Либо добавьте '(void)$varName;' для подавления предупреждения."
                )
            }

            // Default fallback
            else -> {
                val defaultTitle = when (severity) {
                    DiagnosticSeverity.FATAL, DiagnosticSeverity.ERROR -> "Ошибка компилятора"
                    DiagnosticSeverity.WARNING -> "Предупреждение компилятора"
                    DiagnosticSeverity.NOTE -> "Пояснение компилятора"
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
                    offlineHint = "• Проверьте синтаксис строки вокруг указанной позиции.\n• Убедитесь, что все открытые скобки () {} [] и кавычки парные.\n• Проверьте наличие точки с запятой ';' в конце выражения."
                )
            }
        }
    }
}
