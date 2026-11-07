package com.example.esw.segmentation

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxTest {

    @Test
    fun wideImageGetsPaddingOnTopAndBottom() {
        val box = Letterbox.fit(1280, 640, 640)
        assertEquals(0.5f, box.scale, 0.0001f)
        assertEquals(640, box.scaledWidth)
        assertEquals(320, box.scaledHeight)
        assertEquals(0f, box.padLeft, 0f)
        assertEquals(160f, box.padTop, 0f)
    }

    @Test
    fun tallImageGetsPaddingOnTheSides() {
        val box = Letterbox.fit(480, 960, 640)
        assertEquals(320, box.scaledWidth)
        assertEquals(640, box.scaledHeight)
        assertEquals(160f, box.padLeft, 0f)
        assertEquals(0f, box.padTop, 0f)
    }

    @Test
    fun modelCoordinatesMapBackToTheImage() {
        val box = Letterbox.fit(1280, 640, 640)
        assertEquals(0f, box.toImageY(160f), 0.001f)
        assertEquals(640f, box.toImageY(480f), 0.001f)
        assertEquals(1280f, box.toImageX(640f), 0.001f)
        assertEquals(200f, box.toImageLength(100f), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyImageIsRejected() {
        Letterbox.fit(0, 100, 640)
    }
}
