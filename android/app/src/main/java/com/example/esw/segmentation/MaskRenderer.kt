package com.example.esw.segmentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlin.math.exp

/** Builds the overlay and mask images for a set of detections and measures what they cover. */
class MaskRenderer {

    class Rendered(
        val overlay: Bitmap,
        val maskOnly: Bitmap,
        val classPixels: Map<Int, Int>,
        val segmentedPixels: Int
    )

    fun render(
        image: Bitmap,
        detections: List<Detection>,
        prototypes: Array<Array<FloatArray>>,
        letterbox: Letterbox
    ): Rendered {
        val overlay = image.copy(Bitmap.Config.ARGB_8888, true)
        val maskOnly = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(overlay)
        val maskCanvas = Canvas(maskOnly).apply { drawColor(Color.BLACK) }

        val pixelCount = image.width * image.height
        val classMasks = SegmentClasses.all.associate { it.id to BooleanArray(pixelCount) }

        for (detection in detections) {
            val color = SegmentClasses.all.getOrNull(detection.classId)?.color ?: Color.YELLOW
            val mask = classMasks[detection.classId] ?: continue
            drawDetection(detection, color, prototypes, letterbox, image, overlayCanvas, maskCanvas, mask)
        }

        val classPixels = classMasks.mapValues { (_, mask) -> mask.count { it } }
        return Rendered(overlay, maskOnly, classPixels, countUnion(classMasks.values, pixelCount))
    }

    /** A pixel covered by two classes is one pixel, so the total must not add the classes together. */
    private fun countUnion(masks: Collection<BooleanArray>, pixelCount: Int): Int {
        var total = 0
        for (i in 0 until pixelCount) {
            if (masks.any { it[i] }) total++
        }
        return total
    }

    private fun drawDetection(
        detection: Detection,
        color: Int,
        prototypes: Array<Array<FloatArray>>,
        letterbox: Letterbox,
        image: Bitmap,
        overlayCanvas: Canvas,
        maskCanvas: Canvas,
        classMask: BooleanArray
    ) {
        val inputSize = ModelSpec.INPUT_SIZE
        val left = (detection.x - detection.width / 2).coerceIn(0f, inputSize - 1f)
        val top = (detection.y - detection.height / 2).coerceIn(0f, inputSize - 1f)
        val right = (detection.x + detection.width / 2).coerceIn(left + 1f, inputSize.toFloat())
        val bottom = (detection.y + detection.height / 2).coerceIn(top + 1f, inputSize.toFloat())
        val boxWidth = (right - left).toInt().coerceAtLeast(1)
        val boxHeight = (bottom - top).toInt().coerceAtLeast(1)

        val binary = buildBinaryMask(detection, prototypes)
        val prototypeSize = prototypes.size

        val full = Bitmap.createBitmap(binary.toPixels(color, OVERLAY_ALPHA), prototypeSize, prototypeSize, Bitmap.Config.ARGB_8888)
        val fullOpaque = Bitmap.createBitmap(binary.toPixels(color, 255), prototypeSize, prototypeSize, Bitmap.Config.ARGB_8888)
        val model = Bitmap.createScaledBitmap(full, inputSize, inputSize, true)
        val modelOpaque = Bitmap.createScaledBitmap(fullOpaque, inputSize, inputSize, true)

        try {
            val imageLeft = letterbox.toImageX(left).coerceIn(0f, image.width.toFloat()).toInt()
            val imageTop = letterbox.toImageY(top).coerceIn(0f, image.height.toFloat()).toInt()
            val imageWidth = letterbox.toImageLength(boxWidth.toFloat()).coerceIn(1f, image.width.toFloat()).toInt()
            val imageHeight = letterbox.toImageLength(boxHeight.toFloat()).coerceIn(1f, image.height.toFloat()).toInt()

            val cropped = Bitmap.createBitmap(model, left.toInt(), top.toInt(), boxWidth, boxHeight)
            val croppedOpaque = Bitmap.createBitmap(modelOpaque, left.toInt(), top.toInt(), boxWidth, boxHeight)
            val final = Bitmap.createScaledBitmap(cropped, imageWidth, imageHeight, true)
            val finalOpaque = Bitmap.createScaledBitmap(croppedOpaque, imageWidth, imageHeight, true)

            markPixels(final, imageLeft, imageTop, image.width, image.height, classMask)
            overlayCanvas.drawBitmap(final, imageLeft.toFloat(), imageTop.toFloat(), null)
            maskCanvas.drawBitmap(finalOpaque, imageLeft.toFloat(), imageTop.toFloat(), null)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipped a mask that fell outside the image", e)
        }
    }

    private fun buildBinaryMask(detection: Detection, prototypes: Array<Array<FloatArray>>): BooleanArray {
        val size = prototypes.size
        val mask = BooleanArray(size * size)
        val coefficients = detection.maskCoefficients
        for (y in 0 until size) {
            val row = prototypes[y]
            for (x in 0 until size) {
                var sum = 0f
                val prototype = row[x]
                for (k in coefficients.indices) sum += coefficients[k] * prototype[k]
                mask[y * size + x] = sigmoid(sum) > MASK_THRESHOLD
            }
        }
        return mask
    }

    private fun sigmoid(value: Float): Float = 1f / (1f + exp(-value))

    private fun BooleanArray.toPixels(color: Int, alpha: Int): IntArray {
        val on = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        return IntArray(size) { if (this[it]) on else Color.TRANSPARENT }
    }

    private fun markPixels(mask: Bitmap, left: Int, top: Int, imageWidth: Int, imageHeight: Int, target: BooleanArray) {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        for (y in 0 until mask.height) {
            val imageY = top + y
            if (imageY >= imageHeight) break
            for (x in 0 until mask.width) {
                val imageX = left + x
                if (imageX >= imageWidth) break
                if (pixels[y * mask.width + x] != Color.TRANSPARENT) {
                    target[imageY * imageWidth + imageX] = true
                }
            }
        }
    }

    private companion object {
        const val TAG = "MaskRenderer"
        const val OVERLAY_ALPHA = 120
        const val MASK_THRESHOLD = 0.5f
    }
}
