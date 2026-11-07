package com.example.esw.storage

import com.example.esw.segmentation.SegmentationStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One processed image, in the form it is written to the CSV file and to Firebase. */
data class CaptureRecord(
    val timestamp: Long,
    val captureCount: Int,
    val totalPixels: Int,
    val coverage: Float,
    val processingTimeMs: Long,
    val delegate: String,
    val classCoverage: Map<String, Float>,
    val source: String
) {
    val timestampIst: String get() = formatIst(timestamp)

    fun toCsvRow(localImagePath: String): String = listOf(
        timestamp.toString(),
        Csv.escape(timestampIst),
        captureCount.toString(),
        totalPixels.toString(),
        coverage.toString(),
        processingTimeMs.toString(),
        Csv.escape(delegate),
        Csv.escape(source),
        Csv.escape(localImagePath)
    ).joinToString(",")

    fun toFirebaseMap(): MutableMap<String, Any> = mutableMapOf(
        "timestamp" to timestamp,
        "timestampIST" to timestampIst,
        "captureCount" to captureCount,
        "totalPixels" to totalPixels,
        "coverage" to coverage,
        "processingTimeMs" to processingTimeMs,
        "delegateUsed" to delegate,
        "classBreakdown" to classCoverage,
        "source" to source
    )

    companion object {
        const val CSV_HEADER =
            "timestamp,timestampIST,captureCount,totalPixels,coverage,processingTimeMs,delegateUsed,source,localImagePath"

        fun from(stats: SegmentationStats, captureCount: Int, source: String, timestamp: Long) = CaptureRecord(
            timestamp = timestamp,
            captureCount = captureCount,
            totalPixels = stats.totalPixels,
            coverage = stats.coverage,
            processingTimeMs = stats.timings.totalMs,
            delegate = stats.delegate.label,
            classCoverage = stats.classStats.associate { it.segmentClass.label to it.coverage },
            source = source
        )

        private fun formatIst(millis: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            format.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            return format.format(Date(millis))
        }
    }
}

object Csv {
    /** Quotes a value when it contains a comma, a quote or a line break. */
    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
