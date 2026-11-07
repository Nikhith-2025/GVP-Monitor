package com.example.esw.segmentation

import kotlin.math.max
import kotlin.math.min

/**
 * Turns the raw YOLO output into a clean list of detections. This has no Android
 * dependencies, so it is covered by plain unit tests.
 *
 * The output has one row per value and one column per candidate:
 * rows 0-3 are the box, the next rows are class scores, the rest are mask coefficients.
 */
object YoloDecoder {
    private const val BOX_ROWS = 4
    const val IOU_THRESHOLD = 0.45f

    fun decode(
        output: Array<FloatArray>,
        classCount: Int,
        inputSize: Int,
        confidenceThreshold: Float
    ): List<Detection> {
        val coefficientCount = output.size - BOX_ROWS - classCount
        val candidates = output[0].size
        val detections = ArrayList<Detection>()

        for (i in 0 until candidates) {
            var bestScore = -1f
            var bestClass = -1
            for (c in 0 until classCount) {
                val score = output[BOX_ROWS + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestScore <= confidenceThreshold) continue

            detections += Detection(
                x = output[0][i] * inputSize,
                y = output[1][i] * inputSize,
                width = output[2][i] * inputSize,
                height = output[3][i] * inputSize,
                confidence = bestScore,
                classId = bestClass,
                maskCoefficients = FloatArray(coefficientCount) { k -> output[BOX_ROWS + classCount + k][i] }
            )
        }
        return detections
    }

    /** Keeps the most confident box and drops any box that overlaps it too much. */
    fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float = IOU_THRESHOLD): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }
        val suppressed = BooleanArray(sorted.size)
        val kept = ArrayList<Detection>()

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept += sorted[i]
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(sorted[i], sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    fun iou(a: Detection, b: Detection): Float {
        val overlapWidth = min(a.x + a.width / 2, b.x + b.width / 2) - max(a.x - a.width / 2, b.x - b.width / 2)
        val overlapHeight = min(a.y + a.height / 2, b.y + b.height / 2) - max(a.y - a.height / 2, b.y - b.height / 2)
        val intersection = max(0f, overlapWidth) * max(0f, overlapHeight)
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
