package com.prismde.feature_files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prismde.R

enum class FileTemplateType(
    val labelRes: Int?,
    val displayName: String,
    val extension: String,
    val defaultContent: String
) {
    CPP_SOURCE(R.string.template_cpp_source, "C++ Source File (.cpp)", "cpp", """
        #include <iostream>

        int main() {
            std::cout << "Hello from PrismDE NDK!" << std::endl;
            return 0;
        }
    """.trimIndent()),

    JNI_CPP(R.string.template_jni_cpp, "JNI C++ for Android", "cpp", """
        #include <jni.h>
        #include <android/log.h>

        #define LOG_TAG "PrismNDK"
        #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

        extern "C" JNIEXPORT jstring JNICALL
        Java_com_example_app_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
            LOGI("JNI function called from Android");
            return env->NewStringUTF("Hello from PrismDE native .so library!");
        }
    """.trimIndent()),

    C_SOURCE(R.string.template_c_source, "C Source File (.c)", "c", """
        #include <stdio.h>

        int main(void) {
            printf("Hello from C in PrismDE!\n");
            return 0;
        }
    """.trimIndent()),

    HEADER(R.string.template_header, "Header File (.h)", "h", """
        #pragma once

        #ifdef __cplusplus
        extern "C" {
        #endif

        void prism_native_init();

        #ifdef __cplusplus
        }
        #endif
    """.trimIndent()),

    ANDROID_MK(R.string.template_android_mk, "Android.mk (ndk-build)", "mk", """
        LOCAL_PATH := $(call my-dir)

        include $(CLEAR_VARS)
        LOCAL_MODULE    := native-lib
        LOCAL_SRC_FILES := native-lib.cpp
        LOCAL_LDLIBS    := -llog -landroid
        include $(BUILD_SHARED_LIBRARY)
    """.trimIndent()),

    APPLICATION_MK(null, "Application.mk", "mk", """
        APP_ABI := arm64-v8a armeabi-v7a x86_64
        APP_PLATFORM := android-24
        APP_STL := c++_shared
    """.trimIndent()),

    CMAKE(null, "CMakeLists.txt", "txt", """
        cmake_minimum_required(VERSION 3.22.1)
        project("native-lib")

        add_library(${'$'}{PROJECT_NAME} SHARED
            native-lib.cpp
        )

        find_library(log-lib log)
        target_link_libraries(${'$'}{PROJECT_NAME} ${'$'}{log-lib})
    """.trimIndent()),

    DIRECTORY(R.string.template_folder, "New Folder", "", "")
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, content: String, isFolder: Boolean) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf(FileTemplateType.CPP_SOURCE) }
    var fileName by remember { mutableStateOf("main.cpp") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selectedTemplate == FileTemplateType.DIRECTORY) Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_file_folder_title))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.file_folder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.file_type_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))

                FileTemplateType.values().forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTemplate = template
                                fileName = when (template) {
                                    FileTemplateType.CPP_SOURCE -> "main.cpp"
                                    FileTemplateType.JNI_CPP -> "native-lib.cpp"
                                    FileTemplateType.C_SOURCE -> "main.c"
                                    FileTemplateType.HEADER -> "native-lib.h"
                                    FileTemplateType.ANDROID_MK -> "Android.mk"
                                    FileTemplateType.APPLICATION_MK -> "Application.mk"
                                    FileTemplateType.CMAKE -> "CMakeLists.txt"
                                    FileTemplateType.DIRECTORY -> "jni"
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTemplate == template,
                            onClick = { selectedTemplate = template }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = template.labelRes?.let { stringResource(it) } ?: template.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fileName.isNotBlank()) {
                        val isFolder = selectedTemplate == FileTemplateType.DIRECTORY
                        onCreate(fileName.trim(), selectedTemplate.defaultContent, isFolder)
                    }
                }
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
