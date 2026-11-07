package com.example.esw.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Shows a result image. With a second image set, a handle can be dragged across the view
 * to compare the two: the second image is shown to the left of the handle.
 */
class ResultImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var base: Bitmap? = null
    private var compare: Bitmap? = null
    private var dividerRatio = 0.5f

    val isInCompareMode: Boolean get() = compare != null

    private val fitMatrix = Matrix()
    private val imageRect = RectF()
    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }

    fun show(image: Bitmap?, compareWith: Bitmap? = null) {
        base = image
        compare = compareWith
        updateMatrix()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = updateMatrix()

    /** Fits the image inside the view, centred, keeping its shape. */
    private fun updateMatrix() {
        val image = base ?: return
        if (width == 0 || height == 0) return
        val source = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
        fitMatrix.setRectToRect(source, RectF(0f, 0f, width.toFloat(), height.toFloat()), Matrix.ScaleToFit.CENTER)
        fitMatrix.mapRect(imageRect, source)
    }

    override fun onDraw(canvas: Canvas) {
        val image = base ?: return
        canvas.drawBitmap(image, fitMatrix, bitmapPaint)

        val other = compare ?: return
        val x = imageRect.left + imageRect.width() * dividerRatio

        canvas.save()
        canvas.clipRect(imageRect.left, imageRect.top, x, imageRect.bottom)
        canvas.drawBitmap(other, fitMatrix, bitmapPaint)
        canvas.restore()

        drawHandle(canvas, x)
    }

    private fun drawHandle(canvas: Canvas, x: Float) {
        val y = imageRect.centerY()
        val arrow = 4f * density
        canvas.drawLine(x, imageRect.top, x, imageRect.bottom, linePaint)
        canvas.drawCircle(x, y, HANDLE_RADIUS_DP * density, handlePaint)
        for (direction in listOf(-1f, 1f)) {
            val tip = x + direction * 8f * density
            val back = x + direction * 3f * density
            canvas.drawLine(back, y - arrow, tip, y, arrowPaint)
            canvas.drawLine(back, y + arrow, tip, y, arrowPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (compare == null || imageRect.width() <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Stops the scrolling page from taking over while the handle is dragged.
                parent?.requestDisallowInterceptTouchEvent(true)
                dividerRatio = ((event.x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
                invalidate()
            }
        }
        return true
    }

    private companion object {
        const val HANDLE_RADIUS_DP = 16f
    }
}
