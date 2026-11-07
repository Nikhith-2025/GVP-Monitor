package com.example.esw.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves an image to the shared Pictures/ESWSeg folder. */
class GalleryExporter(private val resolver: ContentResolver) {

    /**
     * Returns false on Android 9, which has no scoped MediaStore path to write to without
     * a storage permission. The app's own copy from LocalResultStore still exists there.
     */
    fun export(image: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val name = "seg_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ESWSeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        resolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
