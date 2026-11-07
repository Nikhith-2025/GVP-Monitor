package com.example.esw.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.Closeable

/**
 * Runs the whole pipeline: prepare the image, run the model, draw the result.
 *
 * Not thread safe. Everything here, including creating it, has to run on one background
 * thread (see SegmentationWorker). The last model output is kept so that changing the
 * confidence or hiding a class can redraw the result without running the model again.
 */
class SegmentationEngine(context: Context) : Closeable {

    private val pool = InterpreterPool(context)
    private val renderer = MaskRenderer()

    private class LastRun(
        val image: Bitmap,
        val letterbox: Letterbox,
        val outputs: ModelOutputs,
        val preprocessMs: Long,
        val inferenceMs: Long,
        val delegate: DelegateType
    )

    private var lastRun: LastRun? = null

    val currentDelegate: DelegateType get() = pool.current
    val availableDelegates: List<DelegateType> get() = pool.available
    val gpuUnavailableReason: String? get() = pool.gpuUnavailableReason

    fun selectDelegate(type: DelegateType): Boolean = pool.select(type)

    fun segment(image: Bitmap, options: RenderOptions): SegmentationResult {
        val software = toSoftwareBitmap(image)

        val preprocessStart = SystemClock.elapsedRealtime()
        val (input, letterbox) = ImagePreprocessor.process(software)
        val preprocessMs = SystemClock.elapsedRealtime() - preprocessStart

        val outputs = ModelOutputs()
        val inferenceStart = SystemClock.elapsedRealtime()
        pool.run(input, outputs)
        val inferenceMs = SystemClock.elapsedRealtime() - inferenceStart

        val run = LastRun(software, letterbox, outputs, preprocessMs, inferenceMs, pool.current)
        lastRun = run
        return render(run, options)
    }

    /** Redraws the last image with new options. Returns null if nothing has been processed yet. */
    fun redraw(options: RenderOptions): SegmentationResult? = lastRun?.let { render(it, options) }

    private fun render(run: LastRun, options: RenderOptions): SegmentationResult {
        val postprocessStart = SystemClock.elapsedRealtime()

        val candidates = YoloDecoder.decode(
            output = run.outputs.detections[0],
            classCount = SegmentClasses.all.size,
            inputSize = ModelSpec.INPUT_SIZE,
            confidenceThreshold = options.confidenceThreshold
        )
        val detections = YoloDecoder.nonMaxSuppression(candidates)
            .filter { it.classId !in options.hiddenClassIds }
        val rendered = renderer.render(run.image, detections, run.outputs.prototypes[0], run.letterbox)

        val timings = Timings(run.preprocessMs, run.inferenceMs, SystemClock.elapsedRealtime() - postprocessStart)
        val stats = buildStats(run, detections, rendered, timings)
        return SegmentationResult(run.image, rendered.overlay, rendered.maskOnly, stats)
    }

    private fun buildStats(
        run: LastRun,
        detections: List<Detection>,
        rendered: MaskRenderer.Rendered,
        timings: Timings
    ): SegmentationStats {
        val totalPixels = run.image.width * run.image.height
        val objectCounts = detections.groupingBy { it.classId }.eachCount()
        val classStats = SegmentClasses.all.mapNotNull { segmentClass ->
            val pixels = rendered.classPixels[segmentClass.id] ?: 0
            if (pixels == 0) return@mapNotNull null
            ClassStats(
                segmentClass = segmentClass,
                pixels = pixels,
                coverage = percent(pixels, totalPixels),
                objectCount = objectCounts[segmentClass.id] ?: 0
            )
        }
        return SegmentationStats(
            classStats = classStats,
            segmentedPixels = rendered.segmentedPixels,
            totalPixels = totalPixels,
            coverage = percent(rendered.segmentedPixels, totalPixels),
            timings = timings,
            delegate = run.delegate
        )
    }

    private fun percent(part: Int, whole: Int): Float = part.toFloat() / whole * 100f

    private fun toSoftwareBitmap(bitmap: Bitmap): Bitmap =
        if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)

    override fun close() {
        lastRun = null
        pool.close()
    }
}
