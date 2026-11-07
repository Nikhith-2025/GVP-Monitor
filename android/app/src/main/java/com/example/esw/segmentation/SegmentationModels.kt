package com.example.esw.segmentation

import android.graphics.Bitmap
import android.graphics.Color

/** The hardware a model can run on. NNAPI is shown as NPU because that is where it lands on the QIDK. */
enum class DelegateType(val label: String) {
    GPU("GPU"),
    NNAPI("NPU"),
    CPU("CPU")
}

/** One class the model can detect. The list order must match the order the model was trained with. */
data class SegmentClass(val id: Int, val label: String, val color: Int, val isCounted: Boolean)

object SegmentClasses {
    val all = listOf(
        SegmentClass(0, "Animal", Color.rgb(255, 82, 82), isCounted = true),
        SegmentClass(1, "Waste", Color.rgb(105, 240, 174), isCounted = false),
        SegmentClass(2, "Person", Color.rgb(68, 138, 255), isCounted = true)
    )
}

/** A single detection in the 640x640 model input space. x and y are the centre of the box. */
data class Detection(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val classId: Int,
    val maskCoefficients: FloatArray
)

data class ClassStats(
    val segmentClass: SegmentClass,
    val pixels: Int,
    val coverage: Float,
    val objectCount: Int
)

data class Timings(val preprocessMs: Long, val inferenceMs: Long, val postprocessMs: Long) {
    val totalMs: Long get() = preprocessMs + inferenceMs + postprocessMs
}

data class SegmentationStats(
    val classStats: List<ClassStats>,
    val segmentedPixels: Int,
    val totalPixels: Int,
    val coverage: Float,
    val timings: Timings,
    val delegate: DelegateType
)

/** What the user can change after an image has been processed, without running the model again. */
data class RenderOptions(
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE,
    val hiddenClassIds: Set<Int> = emptySet()
) {
    companion object {
        const val DEFAULT_CONFIDENCE = 0.05f
    }
}

data class SegmentationResult(
    val original: Bitmap,
    val overlay: Bitmap,
    val maskOnly: Bitmap,
    val stats: SegmentationStats
)
