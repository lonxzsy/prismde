package com.prismde.feature_preview

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * JNI Native Bridge to dynamically compiled libpreview_gui.so.
 * Routes OpenGL ES3 lifecycle and touch gestures into ImGui.
 */
object ImGuiPreviewBridge {
    @Volatile var isLoaded = false
        private set

    fun load(soFile: File, context: Context? = null): Boolean {
        return try {
            // Pre-load libc++_shared.so so Bionic linker namespace resolves it
            val libcxxFile = listOfNotNull(
                File(soFile.parentFile, "libc++_shared.so"),
                context?.let { File(it.filesDir, "ndk/r26c/android-ndk-aide/sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so") },
                context?.let { File(it.filesDir, "ndk/r26c/sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so") },
                context?.let { File(it.filesDir, "tools/jdk/lib/libc++_shared.so") },
                context?.let { File(it.applicationInfo.nativeLibraryDir, "libc++_shared.so") }
            ).firstOrNull { it.exists() && it.length() > 1000L }

            if (libcxxFile != null) {
                try {
                    System.load(libcxxFile.absolutePath)
                } catch (_: Throwable) {}
            }

            System.load(soFile.absolutePath)
            isLoaded = true
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            isLoaded = false
            false
        }
    }

    external fun nativeInit(width: Int, height: Int): Boolean
    external fun nativeResize(width: Int, height: Int)
    external fun nativeRender(deltaTime: Float)
    external fun nativeTouch(action: Int, x: Float, y: Float)
    external fun nativeShutdown()
}

/**
 * Interactive GLSurfaceView with continuous rendering and touch event forwarding to ImGui.
 */
class ImGuiGLSurfaceView(
    context: Context,
    private val soFile: File
) : GLSurfaceView(context) {

    private var isRendererInitialized = false

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setZOrderMediaOverlay(true)

        setRenderer(object : Renderer {
            private var lastTime = System.nanoTime()

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                if (!ImGuiPreviewBridge.isLoaded) {
                    ImGuiPreviewBridge.load(soFile, context)
                }
            }

            override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
                GLES30.glViewport(0, 0, w, h)
                if (!ImGuiPreviewBridge.isLoaded) {
                    ImGuiPreviewBridge.load(soFile, context)
                }
                if (ImGuiPreviewBridge.isLoaded) {
                    if (!isRendererInitialized) {
                        ImGuiPreviewBridge.nativeInit(w.coerceAtLeast(300), h.coerceAtLeast(300))
                        isRendererInitialized = true
                    } else {
                        ImGuiPreviewBridge.nativeResize(w, h)
                    }
                }
            }

            override fun onDrawFrame(gl: GL10?) {
                val now = System.nanoTime()
                val dt = ((now - lastTime) / 1_000_000_000.0f).coerceIn(0.001f, 0.1f)
                lastTime = now

                GLES30.glClearColor(0.12f, 0.12f, 0.14f, 1.0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

                if (ImGuiPreviewBridge.isLoaded && isRendererInitialized) {
                    ImGuiPreviewBridge.nativeRender(dt)
                }
            }
        })

        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Disallow parent Compose ModalBottomSheet from stealing touch gestures
        parent?.requestDisallowInterceptTouchEvent(true)

        if (!ImGuiPreviewBridge.isLoaded || !isRendererInitialized) {
            return true
        }

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1
            MotionEvent.ACTION_MOVE -> 2
            else -> -1
        }
        if (action != -1) {
            val touchX = event.x
            val touchY = event.y
            queueEvent {
                ImGuiPreviewBridge.nativeTouch(action, touchX, touchY)
            }
        }
        return true
    }

    fun shutdown() {
        queueEvent {
            if (ImGuiPreviewBridge.isLoaded && isRendererInitialized) {
                ImGuiPreviewBridge.nativeShutdown()
                isRendererInitialized = false
            }
        }
    }
}

/**
 * Jetpack Compose wrapper for live interactive ImGui preview.
 */
@Composable
fun ImGuiPreviewView(
    soFile: File,
    modifier: Modifier = Modifier
) {
    var glView: ImGuiGLSurfaceView? = null

    DisposableEffect(soFile) {
        onDispose {
            glView?.shutdown()
            glView = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            ImGuiGLSurfaceView(context, soFile).also { glView = it }
        }
    )
}
