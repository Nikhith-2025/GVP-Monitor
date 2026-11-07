package com.example.esw.storage

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter

/** Keeps a copy of every result on the device: the image, and one line in captures.csv. */
class LocalResultStore(context: Context) {

    private val folder = File(context.getExternalFilesDir(null), "segmentation_captures")

    fun save(record: CaptureRecord, image: Bitmap): File {
        folder.mkdirs()
        val imageFile = File(folder, "seg_${record.timestamp}.jpg")
        FileOutputStream(imageFile).use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }

        val csv = File(folder, "captures.csv")
        val isNew = !csv.exists()
        FileWriter(csv, true).use { writer ->
            if (isNew) writer.appendLine(CaptureRecord.CSV_HEADER)
            writer.appendLine(record.toCsvRow(imageFile.absolutePath))
        }
        return imageFile
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
