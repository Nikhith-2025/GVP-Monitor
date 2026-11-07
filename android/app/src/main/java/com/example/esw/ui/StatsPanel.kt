package com.example.esw.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.example.esw.R
import com.example.esw.databinding.ItemClassRowBinding
import com.example.esw.databinding.ViewStatsBinding
import com.example.esw.segmentation.ClassStats
import com.example.esw.segmentation.SegmentClass
import com.example.esw.segmentation.SegmentClasses
import com.example.esw.segmentation.SegmentationStats
import com.example.esw.segmentation.Timings
import java.util.Locale

/** Fills in the results card: total coverage, one row per class, and the time taken. */
class StatsPanel(private val binding: ViewStatsBinding) {

    private val context = binding.root.context
    private val rows: Map<Int, ItemClassRowBinding> = SegmentClasses.all.associate { it.id to addRow(it) }

    init {
        clear()
    }

    private fun addRow(segmentClass: SegmentClass): ItemClassRowBinding {
        val row = ItemClassRowBinding.inflate(LayoutInflater.from(context), binding.classRows, true)
        row.colorDot.backgroundTintList = ColorStateList.valueOf(segmentClass.color)
        row.className.text = segmentClass.label
        row.classProgress.setIndicatorColor(segmentClass.color)
        return row
    }

    fun clear() {
        binding.coverageValue.text = context.getString(R.string.empty_value)
        binding.pixelSummary.setText(R.string.stats_hint)
        rows.values.forEach { row ->
            row.classValue.text = context.getString(R.string.empty_value)
            row.classProgress.setProgressCompat(0, false)
        }
        binding.timingGroup.visibility = View.GONE
    }

    fun show(stats: SegmentationStats) {
        binding.coverageValue.text = String.format(Locale.US, "%.1f%%", stats.coverage)
        binding.pixelSummary.text = context.getString(
            R.string.pixel_summary,
            String.format(Locale.US, "%,d", stats.segmentedPixels),
            String.format(Locale.US, "%,d", stats.totalPixels)
        )

        val byClass = stats.classStats.associateBy { it.segmentClass.id }
        rows.forEach { (id, row) ->
            val classStats = byClass[id]
            row.classValue.text = classStats?.let(::describe) ?: context.getString(R.string.nothing_found)
            row.classProgress.setProgressCompat(classStats?.coverage?.toInt() ?: 0, true)
        }

        showTimings(stats.timings, stats.delegate.label)
    }

    private fun describe(stats: ClassStats): String {
        val coverage = String.format(Locale.US, "%.1f%%", stats.coverage)
        if (!stats.segmentClass.isCounted) return coverage
        val count = context.resources.getQuantityString(R.plurals.objects_found, stats.objectCount, stats.objectCount)
        return "$count, $coverage"
    }

    private fun showTimings(timings: Timings, processor: String) {
        binding.timingGroup.visibility = View.VISIBLE
        binding.timingTotal.text = context.getString(R.string.timing_total, timings.totalMs, processor)
        binding.timingDetail.text = context.getString(
            R.string.timing_detail, timings.preprocessMs, timings.inferenceMs, timings.postprocessMs
        )
        setWeight(binding.barPreprocess, timings.preprocessMs)
        setWeight(binding.barInference, timings.inferenceMs)
        setWeight(binding.barPostprocess, timings.postprocessMs)
    }

    private fun setWeight(bar: View, ms: Long) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        params.weight = ms.coerceAtLeast(1L).toFloat()
        bar.layoutParams = params
    }
}
