package com.example.esw.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun plainValuesAreLeftAlone() {
        assertEquals("capture", Csv.escape("capture"))
    }

    @Test
    fun commasAreQuoted() {
        assertEquals("\"a,b\"", Csv.escape("a,b"))
    }

    @Test
    fun quotesAreDoubled() {
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
    }

    @Test
    fun rowHasOneValuePerHeaderColumn() {
        val record = CaptureRecord(
            timestamp = 1000L,
            captureCount = 2,
            totalPixels = 100,
            coverage = 12.5f,
            processingTimeMs = 40L,
            delegate = "CPU",
            classCoverage = emptyMap(),
            source = "gallery"
        )
        val columns = CaptureRecord.CSV_HEADER.split(",").size
        assertEquals(columns, record.toCsvRow("/tmp/a.jpg").split(",").size)
    }
}
