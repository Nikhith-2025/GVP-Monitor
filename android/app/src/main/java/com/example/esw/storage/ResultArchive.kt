package com.example.esw.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.esw.segmentation.SegmentationResult
import java.util.concurrent.Executors

/** Saves results away from the main thread: on the device, then to the gallery and Firebase. */
class ResultArchive(context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val localStore = LocalResultStore(context)
    private val gallery = GalleryExporter(context.contentResolver)
    private val firebase = FirebaseUploader(context)

    fun save(
        result: SegmentationResult,
        captureCount: Int,
        source: String,
        exportToGallery: Boolean,
        onUploaded: (Boolean) -> Unit
    ) {
        val record = CaptureRecord.from(result.stats, captureCount, source, System.currentTimeMillis())
        executor.execute {
            try {
                val file = localStore.save(record, result.overlay)
                if (exportToGallery) gallery.export(result.overlay)
                firebase.upload(record, result.overlay, file.absolutePath) { uploaded ->
                    mainHandler.post { onUploaded(uploaded) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the result", e)
            }
        }
    }

    fun markAutoCaptureStarted() = firebase.markAutoCaptureStarted()

    fun close() = executor.shutdown()

    private companion object {
        const val TAG = "ResultArchive"
    }
}
