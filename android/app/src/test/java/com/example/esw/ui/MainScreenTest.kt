package com.example.esw.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.esw.MainActivity
import com.example.esw.R
import com.example.esw.databinding.ActivityMainBinding
import com.example.esw.segmentation.ClassStats
import com.example.esw.segmentation.DelegateType
import com.example.esw.segmentation.SegmentClasses
import com.example.esw.segmentation.SegmentationStats
import com.example.esw.segmentation.Timings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreenTest {

    private val stats = SegmentationStats(
        classStats = listOf(
            ClassStats(SegmentClasses.all[1], pixels = 228_000, coverage = 18.6f, objectCount = 3),
            ClassStats(SegmentClasses.all[2], pixels = 49_000, coverage = 4.0f, objectCount = 2)
        ),
        segmentedPixels = 277_000,
        totalPixels = 1_228_800,
        coverage = 22.6f,
        timings = Timings(preprocessMs = 34, inferenceMs = 412, postprocessMs = 96),
        delegate = DelegateType.GPU
    )

    private fun inflate(): ActivityMainBinding {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ESW)
        return ActivityMainBinding.inflate(LayoutInflater.from(context))
    }

    @Test
    fun activityStartsWithoutTheModelOrCamera() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull(activity.findViewById<View>(R.id.captureButton))
    }

    @Test
    fun statsPanelShowsResults() {
        val binding = inflate()
        StatsPanel(binding.stats).show(stats)

        assertEquals("22.6%", binding.stats.coverageValue.text.toString())
        assertEquals(View.VISIBLE, binding.stats.timingGroup.visibility)
        assertEquals(3, binding.stats.classRows.childCount)
    }

    @Test
    fun statsPanelStartsEmpty() {
        val binding = inflate()
        StatsPanel(binding.stats)

        assertEquals("-", binding.stats.coverageValue.text.toString())
        assertEquals(View.GONE, binding.stats.timingGroup.visibility)
    }

    @Test
    fun compareHandleOnlyTakesTouchesWhenComparing() {
        val binding = inflate()
        binding.resultView.show(sampleImage())
        assertFalse(binding.resultView.isInCompareMode)

        binding.resultView.show(sampleImage(), compareWith = sampleImage())
        assertTrue(binding.resultView.isInCompareMode)
    }

    /** Draws the screen to a PNG so the layout can be looked at without a device. */
    @Test
    fun rendersPreview() {
        val binding = inflate()
        StatsPanel(binding.stats).show(stats)
        binding.processorChip.text = "GPU"
        binding.previewView.visibility = View.INVISIBLE
        binding.resultView.visibility = View.VISIBLE
        binding.liveButton.visibility = View.VISIBLE
        binding.viewModeGroup.visibility = View.VISIBLE
        binding.resultView.show(sampleImage(withMask = true), compareWith = sampleImage())

        val width = 1080
        val root = binding.root
        val exactWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val height = 3000
        root.measure(exactWidth, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)

        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(image))
        File("build/ui-preview.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleImage(withMask: Boolean = false): Bitmap {
        val image = Bitmap.createBitmap(960, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(92, 104, 96))
        val paint = Paint()
        paint.color = Color.rgb(120, 128, 118)
        canvas.drawRect(0f, 760f, 960f, 1280f, paint)
        if (withMask) {
            paint.color = Color.argb(120, 105, 240, 174)
            canvas.drawCircle(420f, 880f, 230f, paint)
            paint.color = Color.argb(120, 68, 138, 255)
            canvas.drawRect(700f, 420f, 840f, 900f, paint)
        }
        return image
    }
}
