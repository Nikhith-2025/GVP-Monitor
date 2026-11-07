package com.example.esw.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.esw.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

/**
 * Uploads each result to Firebase. Firebase is optional: without google-services.json and
 * a database URL this does nothing, and results are only stored on the device.
 */
class FirebaseUploader(context: Context) {

    private val database: FirebaseDatabase? = connect(context)

    val isEnabled: Boolean get() = database != null

    private fun connect(context: Context): FirebaseDatabase? {
        val url = BuildConfig.FIREBASE_DATABASE_URL
        if (url.isEmpty() || FirebaseApp.initializeApp(context) == null) return null
        return FirebaseDatabase.getInstance(url)
    }

    /** Uploads the image first, so the database entry can link to it. */
    fun upload(record: CaptureRecord, image: Bitmap, localPath: String, onDone: (Boolean) -> Unit) {
        val results = database?.getReference(RESULTS_NODE) ?: return
        val data = record.toFirebaseMap().apply { put("localImagePath", localPath) }

        fun write() {
            results.push().setValue(data)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Could not write the result", e)
                    onDone(false)
                }
        }

        val bytes = ByteArrayOutputStream().also {
            image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }.toByteArray()

        val file = FirebaseStorage.getInstance().reference.child("segmented/seg_${record.timestamp}.jpg")
        file.putBytes(bytes)
            .continueWithTask { file.downloadUrl }
            .addOnSuccessListener { uri ->
                data["imageUrl"] = uri.toString()
                write()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Image upload failed, saving the result without it", e)
                write()
            }
    }

    /** Lets the Firebase console show when a run started, as a quick connection check. */
    fun markAutoCaptureStarted() {
        database?.getReference("app_status/auto_capture_started_at")?.setValue(System.currentTimeMillis())
    }

    private companion object {
        const val TAG = "FirebaseUploader"
        const val RESULTS_NODE = "segmentation_results"
        const val JPEG_QUALITY = 90
    }
}
