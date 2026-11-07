package com.example.esw.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the engine and its background thread. Callers use this from the main thread and
 * get their results back on the main thread, so the UI never touches the model directly.
 */
class SegmentationWorker(context: Context) {

    data class Processors(
        val current: DelegateType,
        val available: List<DelegateType>,
        val gpuUnavailableReason: String?
    )

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRedraw = AtomicReference<RenderOptions?>(null)

    private var engine: SegmentationEngine? = null
    private var closed = false

    fun start(onReady: (Processors) -> Unit, onError: (Throwable) -> Unit) {
        executor.execute {
            try {
                engine = SegmentationEngine(appContext)
                post { onReady(processors()) }
            } catch (e: Throwable) {
                Log.e(TAG, "Could not load the model", e)
                post { onError(e) }
            }
        }
    }

    fun segment(load: () -> Bitmap, options: RenderOptions, onResult: (Result<SegmentationResult>) -> Unit) {
        executor.execute {
            val result = runCatching { requireEngine().segment(load(), options) }
            result.exceptionOrNull()?.let { Log.e(TAG, "Segmentation failed", it) }
            post { onResult(result) }
        }
    }

    /** Only the newest request is drawn, so dragging the slider does not build up a queue. */
    fun redraw(options: RenderOptions, onResult: (SegmentationResult) -> Unit) {
        if (pendingRedraw.getAndSet(options) != null) return
        executor.execute {
            val latest = pendingRedraw.getAndSet(null) ?: return@execute
            val result = runCatching { engine?.redraw(latest) }.getOrNull()
            if (result != null) post { onResult(result) }
        }
    }

    fun selectProcessor(type: DelegateType, onDone: (Boolean, Processors) -> Unit) {
        executor.execute {
            val selected = requireEngine().selectDelegate(type)
            val processors = processors()
            post { onDone(selected, processors) }
        }
    }

    /** Runs after any work already queued, so the model is never closed in the middle of a run. */
    fun close() {
        closed = true
        executor.execute {
            engine?.close()
            engine = null
        }
        executor.shutdown()
    }

    private fun processors(): Processors {
        val engine = requireEngine()
        return Processors(engine.currentDelegate, engine.availableDelegates, engine.gpuUnavailableReason)
    }

    private fun requireEngine(): SegmentationEngine =
        engine ?: throw IllegalStateException("The model is not loaded")

    private fun post(action: () -> Unit) {
        mainHandler.post { if (!closed) action() }
    }

    private companion object {
        const val TAG = "SegmentationWorker"
    }
}
