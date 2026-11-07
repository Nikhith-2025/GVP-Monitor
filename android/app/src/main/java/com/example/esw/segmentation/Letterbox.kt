package com.example.esw.segmentation

import kotlin.math.min

/**
 * The scale and padding used to fit an image into the square model input without
 * stretching it. Keeping this as a value (not fields on a class) means a result can
 * always be mapped back to the image it came from.
 */
data class Letterbox(
    val scale: Float,
    val padLeft: Float,
    val padTop: Float,
    val scaledWidth: Int,
    val scaledHeight: Int
) {
    /** Converts a distance in model space to a distance in the original image. */
    fun toImageLength(modelLength: Float): Float = modelLength / scale

    /** Converts model space coordinates to original image coordinates. */
    fun toImageX(modelX: Float): Float = (modelX - padLeft) / scale
    fun toImageY(modelY: Float): Float = (modelY - padTop) / scale

    companion object {
        fun fit(imageWidth: Int, imageHeight: Int, target: Int): Letterbox {
            require(imageWidth > 0 && imageHeight > 0) { "Image has no size" }
            val scale = min(target.toFloat() / imageWidth, target.toFloat() / imageHeight)
            val scaledWidth = (imageWidth * scale).toInt()
            val scaledHeight = (imageHeight * scale).toInt()
            return Letterbox(
                scale = scale,
                padLeft = ((target - scaledWidth) / 2).toFloat(),
                padTop = ((target - scaledHeight) / 2).toFloat(),
                scaledWidth = scaledWidth,
                scaledHeight = scaledHeight
            )
        }
    }
}
