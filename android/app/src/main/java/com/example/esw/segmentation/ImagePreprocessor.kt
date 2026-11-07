package com.example.esw.segmentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Resizes an image to the model input and writes it as normalised RGB floats. */
object ImagePreprocessor {
    private val PADDING_COLOR = Color.rgb(114, 114, 114)

    fun process(bitmap: Bitmap): Pair<ByteBuffer, Letterbox> {
        val size = ModelSpec.INPUT_SIZE
        val letterbox = Letterbox.fit(bitmap.width, bitmap.height, size)

        val scaled = Bitmap.createScaledBitmap(bitmap, letterbox.scaledWidth, letterbox.scaledHeight, true)
        val padded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(padded).apply {
            drawColor(PADDING_COLOR)
            drawBitmap(scaled, letterbox.padLeft, letterbox.padTop, null)
        }

        val pixels = IntArray(size * size)
        padded.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled !== bitmap) scaled.recycle()
        padded.recycle()

        val buffer = ByteBuffer.allocateDirect(ModelSpec.INPUT_BYTES).order(ByteOrder.nativeOrder())
        val floats = buffer.asFloatBuffer()
        for (pixel in pixels) {
            floats.put((pixel shr 16 and 0xFF) / 255f)
            floats.put((pixel shr 8 and 0xFF) / 255f)
            floats.put((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer to letterbox
    }
}
