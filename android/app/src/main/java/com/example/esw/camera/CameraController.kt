package com.example.esw.camera

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors

/** Shows the camera preview and takes one photo at a time. */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    @Volatile
    private var capturing = false

    val isReady: Boolean get() = imageCapture != null

    fun start(onError: (Exception) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                bind(future.get())
            } catch (e: Exception) {
                Log.e(TAG, "Could not start the camera", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        imageCapture = capture
    }

    /**
     * Saves a photo to a temporary file and passes it on. The callback runs on a background
     * thread and owns the file. Returns false if the camera is busy or not started.
     */
    fun capture(onSaved: (File) -> Unit, onError: (Exception) -> Unit): Boolean {
        val capture = imageCapture ?: return false
        if (capturing) return false
        capturing = true

        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                capturing = false
                onSaved(file)
            }

            override fun onError(exception: ImageCaptureException) {
                capturing = false
                Log.e(TAG, "Photo failed", exception)
                onError(exception)
            }
        })
        return true
    }

    fun close() = executor.shutdown()

    private companion object {
        const val TAG = "CameraController"
    }
}
