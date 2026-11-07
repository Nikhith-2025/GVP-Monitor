package com.example.esw.segmentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloDecoderTest {

    private fun detection(x: Float, y: Float, size: Float, confidence: Float, classId: Int = 0) =
        Detection(x, y, size, size, confidence, classId, FloatArray(0))

    /** Rows are: x, y, w, h, score of class 0, score of class 1, two mask coefficients. */
    private fun output(vararg candidates: FloatArray): Array<FloatArray> =
        Array(candidates[0].size) { row -> FloatArray(candidates.size) { column -> candidates[column][row] } }

    @Test
    fun decodeScalesBoxesAndPicksTheBestClass() {
        val raw = output(floatArrayOf(0.5f, 0.25f, 0.1f, 0.2f, 0.3f, 0.9f, 1f, 2f))
        val result = YoloDecoder.decode(raw, classCount = 2, inputSize = 640, confidenceThreshold = 0.5f)

        assertEquals(1, result.size)
        val detection = result[0]
        assertEquals(320f, detection.x, 0.001f)
        assertEquals(160f, detection.y, 0.001f)
        assertEquals(64f, detection.width, 0.001f)
        assertEquals(128f, detection.height, 0.001f)
        assertEquals(1, detection.classId)
        assertEquals(0.9f, detection.confidence, 0.001f)
        assertArrayEquals(floatArrayOf(1f, 2f), detection.maskCoefficients, 0f)
    }

    @Test
    fun decodeDropsCandidatesAtOrBelowTheThreshold() {
        val raw = output(
            floatArrayOf(0f, 0f, 0f, 0f, 0.5f, 0.1f, 0f, 0f),
            floatArrayOf(0f, 0f, 0f, 0f, 0.6f, 0.1f, 0f, 0f)
        )
        val result = YoloDecoder.decode(raw, classCount = 2, inputSize = 640, confidenceThreshold = 0.5f)
        assertEquals(1, result.size)
        assertEquals(0.6f, result[0].confidence, 0.001f)
    }

    @Test
    fun iouOfIdenticalBoxesIsOne() {
        val box = detection(50f, 50f, 20f, 0.9f)
        assertEquals(1f, YoloDecoder.iou(box, box), 0.0001f)
    }

    @Test
    fun iouOfSeparateBoxesIsZero() {
        assertEquals(0f, YoloDecoder.iou(detection(10f, 10f, 10f, 0.9f), detection(100f, 100f, 10f, 0.9f)), 0f)
    }

    @Test
    fun iouOfHalfOverlappingBoxes() {
        // Overlap is 5 x 10 = 50, union is 100 + 100 - 50 = 150.
        val iou = YoloDecoder.iou(detection(10f, 10f, 10f, 0.9f), detection(15f, 10f, 10f, 0.9f))
        assertEquals(1f / 3f, iou, 0.0001f)
    }

    @Test
    fun nmsKeepsTheMostConfidentOfOverlappingBoxes() {
        val weak = detection(10f, 10f, 10f, 0.5f)
        val strong = detection(11f, 10f, 10f, 0.9f)
        val apart = detection(100f, 100f, 10f, 0.7f)

        val kept = YoloDecoder.nonMaxSuppression(listOf(weak, strong, apart))

        assertEquals(listOf(strong, apart), kept)
    }

    @Test
    fun nmsHandlesAnEmptyList() {
        assertTrue(YoloDecoder.nonMaxSuppression(emptyList()).isEmpty())
    }
}
