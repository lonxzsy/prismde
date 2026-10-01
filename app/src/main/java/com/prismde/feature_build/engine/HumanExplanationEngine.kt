package com.prismde.feature_build.engine

object HumanExplanationEngine {

    /**
     * Translates raw Clang / GCC compiler messages into clear, human-friendly Russian
     * explanations, providing actionable hints and auto-fix suggestions where applicable.
     */
    fun explain(rawMessage: String): Triple<String, String, String?> {
        val trimmed = rawMessage.trim()

        return when {
            trimmed.contains("expected ';' after") || trimmed.contains("expected ';'") -> Triple(
                "Пропущена точка с запятой (;)",
                "В языке C/C++ каждая инструкция или объявление выражения должны заканчиваться символом ';'. Проверьте конец указанной или предыдущей строки.",
                ";"
            )

            trimmed.contains("use of undeclared identifier") -> {
                val identifier = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "переменная"
                val hint = when (identifier) {
                    "cout", "cin", "endl" -> "Для использования '$identifier' добавьте директиву '#include <iostream>' и пространство имен 'std::$identifier'."
                    "string" -> "Для строкового типа добавьте '#include <string>' и используйте 'std::string'."
                    "vector" -> "Для динамических массивов добавьте '#include <vector>' и используйте 'std::vector'."
                    "printf", "scanf" -> "Для ввода/вывода в стиле Си подключите заголовок '#include <cstdio>' или '#include <stdio.h>'."
                    "malloc", "free" -> "Для ручного управления памятью подключите '#include <cstdlib>'."
                    "sqrt", "pow", "abs" -> "Для математических функций подключите '#include <cmath>'."
                    else -> "Идентификатор '$identifier' не был объявлен в этой области видимости. Проверьте правильность написания имени или добавьте объявление переменной."
                }
                Triple("Неизвестный идентификатор: $identifier", hint, null)
            }

            trimmed.contains("did you mean") -> {
                val candidate = Regex("did you mean '(.+?)'").find(trimmed)?.groupValues?.get(1)
                Triple(
                    "Возможная опечатка в имени",
                    "Компилятор обнаружил похожее имя: '$candidate'. Вероятно, вы допустили опечатку.",
                    candidate
                )
            }

            trimmed.contains("file not found") || trimmed.contains("cannot open source file") || trimmed.contains("fatal error: '") -> {
                val headerName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "заголовок"
                Triple(
                    "Заголовочный файл не найден: $headerName",
                    "Компилятор не смог найти файл '$headerName'. Убедитесь, что файл существует или путь к нему добавлен в include_directories / LOCAL_C_INCLUDES.",
                    null
                )
            }

            trimmed.contains("undefined reference to") -> {
                val symbol = Regex("to '(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "символ"
                val hint = when {
                    symbol.contains("android_") || symbol.contains("__android_log") ->
                        "Для логирования Android подключите библиотеку -llog (или LOCAL_LDLIBS := -llog в Android.mk)."
                    symbol.contains("pthread_") ->
                        "Для потоков POSIX подключите флаг -pthread."
                    symbol.contains("vk") ->
                        "Для Vulkan API добавьте -lvulkan."
                    symbol.contains("gl") ->
                        "Для OpenGL ES добавьте -lGLESv2 или -lGLESv3."
                    else ->
                        "Символ объявлен в заголовке, но его реализация не скомпилирована. Добавьте соответствующий .cpp файл в сборку или подключите внешнюю библиотеку (.so/.a)."
                }
                Triple("Ошибка линковки (Undefined reference): $symbol", hint, null)
            }

            trimmed.contains("cannot initialize a variable of type") || trimmed.contains("cannot convert") -> Triple(
                "Несовместимость типов данных",
                "Попытка присвоить значение одного типа переменной другого несовместимого типа без явного приведения (cast).",
                null
            )

            trimmed.contains("no matching function for call to") -> Triple(
                "Функция вызвана с неверными аргументами",
                "Сигнатура вызываемой функции не совпадает ни с одной из перегрузок (не совпадает количество или типы переданных параметров).",
                null
            )

            trimmed.contains("redefinition of") -> {
                val name = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "элемента"
                Triple(
                    "Повторное объявление: $name",
                    "Элемент '$name' уже был объявлен ранее. Возможно, в заголовочном файле пропущен '#pragma once' или include guard.",
                    "#pragma once"
                )
            }

            trimmed.contains("control reaches end of non-void function") -> Triple(
                "Отсутствует return в функции",
                "Функция объявлена с возвращаемым типом (не void), однако в одной из веток выполнения инструкция 'return' отсутствует.",
                "return 0;"
            )

            trimmed.contains("unused variable") -> {
                val varName = Regex("'(.+?)'").find(trimmed)?.groupValues?.get(1) ?: "переменная"
                Triple(
                    "Неиспользуемая переменная: $varName",
                    "Переменная '$varName' объявлена, но нигде в коде не используется.",
                    null
                )
            }

            else -> Triple(
                "Сообщение компилятора",
                rawMessage,
                null
            )
        }
    }
}
