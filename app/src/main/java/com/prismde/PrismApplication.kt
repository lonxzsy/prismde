package com.prismde

import android.app.Application
import java.io.File

class PrismApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initSampleProject()
    }

    private fun initSampleProject() {
        val sampleDir = File(filesDir, "projects/sample_jni_project")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
            val jniDir = File(sampleDir, "jni").also { it.mkdirs() }

            val isRu = java.util.Locale.getDefault().language == "ru"
            val helloMsg = if (isRu) "Привет из нативной библиотеки PrismDE (.so)!" else "Hello from PrismDE native library (.so)!"
            val cppFile = File(jniDir, "native-lib.cpp")
            cppFile.writeText(
                """
                #include <jni.h>
                #include <string>

                extern "C" JNIEXPORT jstring JNICALL
                Java_com_example_app_MainActivity_stringFromJNI(
                        JNIEnv* env,
                        jobject /* this */) {
                    std::string hello = "$helloMsg";
                    return env->NewStringUTF(hello.c_str());
                }
                """.trimIndent()
            )

            val androidMk = File(jniDir, "Android.mk")
            androidMk.writeText(
                """
                LOCAL_PATH := ${'$'}(call my-dir)

                include ${'$'}(CLEAR_VARS)
                LOCAL_MODULE    := sample_jni
                LOCAL_SRC_FILES := native-lib.cpp
                LOCAL_LDLIBS    := -llog
                include ${'$'}(BUILD_SHARED_LIBRARY)
                """.trimIndent()
            )

            val appMk = File(jniDir, "Application.mk")
            appMk.writeText(
                """
                APP_ABI := arm64-v8a armeabi-v7a x86_64
                APP_PLATFORM := android-24
                APP_STL := c++_shared
                """.trimIndent()
            )
        }
    }
}
