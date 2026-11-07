package com.example.esw

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.esw.camera.CameraController
import com.example.esw.capture.AutoCaptureController
import com.example.esw.databinding.ActivityMainBinding
import com.example.esw.databinding.DialogAutoCaptureBinding
import com.example.esw.segmentation.DelegateType
import com.example.esw.segmentation.RenderOptions
import com.example.esw.segmentation.SegmentClasses
import com.example.esw.segmentation.SegmentationResult
import com.example.esw.segmentation.SegmentationWorker
import com.example.esw.storage.ResultArchive
import com.example.esw.ui.StatsPanel
import com.example.esw.util.BitmapLoader
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.Locale

/** Connects the screen to the camera, the model and storage. The work itself is in those classes. */
class MainActivity : AppCompatActivity() {

    private enum class ViewMode { OVERLAY, MASK, COMPARE, ORIGINAL }

    private lateinit var binding: ActivityMainBinding
    private lateinit var camera: CameraController
    private lateinit var worker: SegmentationWorker
    private lateinit var archive: ResultArchive
    private lateinit var stats: StatsPanel
    private val autoCapture = AutoCaptureController { captureAndSegment(source = SOURCE_AUTO) }

    private var processors: SegmentationWorker.Processors? = null
    private var result: SegmentationResult? = null
    private var viewMode = ViewMode.OVERLAY
    private var options = RenderOptions()
    private var autoCaptureCount = 0
    private var runningJobs = 0

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showCameraDenied()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::segmentGalleryImage) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        camera = CameraController(this, this, binding.previewView)
        worker = SegmentationWorker(this)
        archive = ResultArchive(this)
        stats = StatsPanel(binding.stats)

        setUpControls()
        setUpClassChips()
        showLive()
        loadModel()
        requestCameraIfNeeded()
    }

    private fun loadModel() {
        setBusy(true, R.string.loading_model)
        worker.start(
            onReady = {
                setBusy(false)
                showProcessors(it)
            },
            onError = {
                setBusy(false)
                binding.processorChip.setText(R.string.model_failed)
                message(getString(R.string.model_failed))
            }
        )
    }

    private fun setUpControls() {
        binding.captureButton.setOnClickListener { captureAndSegment(source = SOURCE_CAPTURE) }
        binding.galleryButton.setOnClickListener { pickImage.launch("image/*") }
        binding.autoButton.setOnClickListener {
            if (autoCapture.isRunning) stopAutoCapture() else showAutoCaptureDialog()
        }
        binding.liveButton.setOnClickListener { showLive() }
        binding.grantCameraButton.setOnClickListener { requestCamera.launch(Manifest.permission.CAMERA) }
        binding.processorChip.setOnClickListener { showProcessorPicker() }

        binding.viewModeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            viewMode = when (id) {
                R.id.modeMask -> ViewMode.MASK
                R.id.modeCompare -> ViewMode.COMPARE
                R.id.modeOriginal -> ViewMode.ORIGINAL
                else -> ViewMode.OVERLAY
            }
            showResultImage()
        }

        binding.confidenceSlider.value = RenderOptions.DEFAULT_CONFIDENCE
        binding.confidenceSlider.setLabelFormatter(::formatPercent)
        showConfidence(RenderOptions.DEFAULT_CONFIDENCE)
        binding.confidenceSlider.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            showConfidence(value)
            if (fromUser) updateOptions(options.copy(confidenceThreshold = value))
        })
    }

    private fun setUpClassChips() {
        for (segmentClass in SegmentClasses.all) {
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_class_chip, binding.classChips, false) as Chip
            chip.text = segmentClass.label
            chip.isChecked = true
            chip.setOnCheckedChangeListener { _, visible ->
                val hidden = if (visible) options.hiddenClassIds - segmentClass.id else options.hiddenClassIds + segmentClass.id
                updateOptions(options.copy(hiddenClassIds = hidden))
            }
            binding.classChips.addView(chip)
        }
    }

    private fun updateOptions(updated: RenderOptions) {
        options = updated
        if (result == null) return
        worker.redraw(updated) { redrawn ->
            result = redrawn
            showResult(redrawn)
        }
    }

    // Camera

    private fun requestCameraIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        binding.cameraDeniedGroup.visibility = View.GONE
        binding.captureButton.isEnabled = true
        binding.autoButton.isEnabled = true
        camera.start { message(getString(R.string.camera_failed)) }
    }

    private fun showCameraDenied() {
        binding.cameraDeniedGroup.visibility = View.VISIBLE
        binding.captureButton.isEnabled = false
        binding.autoButton.isEnabled = false
    }

    private fun captureAndSegment(source: String) {
        if (processors == null) return
        // On a slow processor the timer can outrun the model, so skip a tick instead of queueing.
        if (source == SOURCE_AUTO && runningJobs > 0) return
        val started = camera.capture(
            onSaved = { file -> runOnUiThread { onPhotoSaved(file, source) } },
            onError = { runOnUiThread { message(getString(R.string.capture_failed)) } }
        )
        if (started) showLive()
    }

    private fun onPhotoSaved(file: File, source: String) {
        if (source == SOURCE_AUTO) {
            autoCaptureCount++
            binding.autoStatus.text = getString(R.string.auto_running, autoCaptureCount)
        }
        segment(source) {
            try {
                BitmapLoader.fromJpegFile(file)
            } finally {
                file.delete()
            }
        }
    }

    private fun segmentGalleryImage(uri: Uri) {
        segment(SOURCE_GALLERY) { BitmapLoader.fromUri(contentResolver, uri) }
    }

    // Results

    private fun segment(source: String, load: () -> Bitmap) {
        if (processors == null) return
        setBusy(true, R.string.processing)
        worker.segment(load, options) { outcome ->
            setBusy(false)
            outcome
                .onSuccess { onSegmented(it, source) }
                .onFailure { message(getString(R.string.segmentation_failed)) }
        }
    }

    private fun onSegmented(segmented: SegmentationResult, source: String) {
        result = segmented
        showResult(segmented)

        val isAuto = source == SOURCE_AUTO
        archive.save(segmented, autoCaptureCount, source, exportToGallery = isAuto) { uploaded ->
            if (!isAuto) message(getString(if (uploaded) R.string.uploaded else R.string.upload_failed))
        }
    }

    private fun showResult(segmented: SegmentationResult) {
        binding.previewView.visibility = View.INVISIBLE
        binding.resultView.visibility = View.VISIBLE
        binding.liveButton.visibility = View.VISIBLE
        binding.viewModeGroup.visibility = View.VISIBLE
        stats.show(segmented.stats)
        showResultImage()
    }

    private fun showResultImage() {
        val current = result ?: return
        when (viewMode) {
            ViewMode.OVERLAY -> binding.resultView.show(current.overlay)
            ViewMode.MASK -> binding.resultView.show(current.maskOnly)
            ViewMode.COMPARE -> binding.resultView.show(current.overlay, compareWith = current.original)
            ViewMode.ORIGINAL -> binding.resultView.show(current.original)
        }
    }

    private fun showLive() {
        binding.previewView.visibility = View.VISIBLE
        binding.resultView.visibility = View.INVISIBLE
        binding.liveButton.visibility = View.GONE
    }

    // Processor

    private fun showProcessors(info: SegmentationWorker.Processors) {
        processors = info
        binding.processorChip.text = info.current.label
        binding.processorChip.isEnabled = info.available.size > 1
    }

    private fun showProcessorPicker() {
        val info = processors ?: return
        val labels = info.available.map { it.label }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_processor)
            .setSingleChoiceItems(labels, info.available.indexOf(info.current)) { dialog, index ->
                selectProcessor(info.available[index])
                dialog.dismiss()
            }
            .apply {
                if (DelegateType.GPU !in info.available) {
                    info.gpuUnavailableReason?.let { setMessage(getString(R.string.gpu_unavailable, it)) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectProcessor(type: DelegateType) {
        worker.selectProcessor(type) { selected, info ->
            showProcessors(info)
            if (!selected) message(getString(R.string.processor_unavailable))
        }
    }

    // Auto capture

    private fun showAutoCaptureDialog() {
        val dialog = DialogAutoCaptureBinding.inflate(layoutInflater)
        dialog.intervalSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            dialog.intervalLabel.text = getString(R.string.auto_interval, value.toInt())
        })
        dialog.intervalLabel.text = getString(R.string.auto_interval, dialog.intervalSlider.value.toInt())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_title)
            .setView(dialog.root)
            .setPositiveButton(R.string.auto_start) { _, _ -> startAutoCapture(dialog.intervalSlider.value.toInt()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startAutoCapture(intervalSeconds: Int) {
        autoCaptureCount = 0
        autoCapture.start(intervalSeconds)
        archive.markAutoCaptureStarted()
        showLive()
        binding.autoButton.setIconResource(R.drawable.ic_stop)
        binding.autoButton.setText(R.string.auto_stop)
        binding.autoStatus.visibility = View.VISIBLE
        binding.autoStatus.text = getString(R.string.auto_running, 0)
    }

    private fun stopAutoCapture() {
        autoCapture.stop()
        binding.autoButton.setIconResource(R.drawable.ic_timer)
        binding.autoButton.setText(R.string.auto_button)
        binding.autoStatus.text = getString(R.string.auto_stopped, autoCaptureCount)
    }

    // Small helpers

    private fun setBusy(busy: Boolean, textRes: Int = R.string.processing) {
        runningJobs = if (busy) runningJobs + 1 else (runningJobs - 1).coerceAtLeast(0)
        binding.busyGroup.visibility = if (runningJobs > 0) View.VISIBLE else View.GONE
        if (busy) binding.busyText.setText(textRes)
    }

    private fun showConfidence(value: Float) {
        binding.confidenceValue.text = formatPercent(value)
    }

    private fun formatPercent(value: Float): String = String.format(Locale.US, "%.0f%%", value * 100)

    private fun message(text: String) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        // The camera stops with the activity, so a running timer would only produce errors.
        if (autoCapture.isRunning) stopAutoCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.close()
        camera.close()
        archive.close()
    }

    private companion object {
        const val SOURCE_CAPTURE = "capture"
        const val SOURCE_AUTO = "auto"
        const val SOURCE_GALLERY = "gallery"
    }
}
