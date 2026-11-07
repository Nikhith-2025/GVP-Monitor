package com.example.esw.segmentation

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.EnumMap

/**
 * Loads the model once for each processor the device supports and keeps track of the
 * one in use. Loading is slow, so this must not be created on the main thread.
 */
class InterpreterPool(context: Context) : Closeable {

    private val interpreters = EnumMap<DelegateType, Interpreter>(DelegateType::class.java)
    private val closeables = ArrayList<Closeable>()

    var gpuUnavailableReason: String? = null
        private set

    var current: DelegateType
        private set

    val available: List<DelegateType> get() = DelegateType.values().filter { it in interpreters }

    init {
        val model = loadModel(context)
        createGpu(model)
        createNnapi(model)
        createCpu(model)
        current = available.firstOrNull() ?: DelegateType.CPU
        Log.d(TAG, "Ready on $current, available: $available")
    }

    /** Picks a processor. Returns false if this device does not have it. */
    fun select(type: DelegateType): Boolean {
        if (type !in interpreters) return false
        current = type
        return true
    }

    /** Runs the model on the current processor and falls back to the CPU if it is missing. */
    fun run(input: ByteBuffer, outputs: ModelOutputs) {
        val interpreter = interpreters[current] ?: interpreters[DelegateType.CPU]
            ?: throw IllegalStateException("No processor could load the model")
        input.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs.asMap())
    }

    private fun createGpu(model: ByteBuffer) {
        try {
            if (!CompatibilityList().isDelegateSupportedOnThisDevice) {
                gpuUnavailableReason = "Not supported on this device"
                return
            }
            addInterpreter(DelegateType.GPU, model, GpuDelegate())
        } catch (e: Exception) {
            gpuUnavailableReason = e.message ?: "Could not start"
            Log.w(TAG, "GPU delegate failed", e)
        }
    }

    private fun createNnapi(model: ByteBuffer) {
        try {
            addInterpreter(DelegateType.NNAPI, model, NnApiDelegate())
        } catch (e: Throwable) {
            Log.w(TAG, "NNAPI delegate failed", e)
        }
    }

    private fun createCpu(model: ByteBuffer) {
        try {
            val options = Interpreter.Options().setNumThreads(THREADS).setUseNNAPI(false)
            model.rewind()
            interpreters[DelegateType.CPU] = Interpreter(model, options)
        } catch (e: Exception) {
            Log.e(TAG, "CPU interpreter failed", e)
        }
    }

    private fun <T> addInterpreter(type: DelegateType, model: ByteBuffer, delegate: T)
        where T : org.tensorflow.lite.Delegate, T : Closeable {
        try {
            val options = Interpreter.Options().addDelegate(delegate).setNumThreads(THREADS)
            model.rewind()
            val interpreter = Interpreter(model, options)
            warmUp(interpreter)
            interpreters[type] = interpreter
            closeables += delegate
        } catch (e: Throwable) {
            delegate.close()
            throw e
        }
    }

    /** The first runs on a delegate are much slower, so they are done before any timing matters. */
    private fun warmUp(interpreter: Interpreter) {
        val input = ByteBuffer.allocateDirect(ModelSpec.INPUT_BYTES).order(ByteOrder.nativeOrder())
        val outputs = ModelOutputs()
        repeat(WARM_UP_RUNS) {
            input.rewind()
            interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs.asMap())
        }
    }

    private fun loadModel(context: Context): ByteBuffer {
        val file = context.assets.openFd(ModelSpec.ASSET_NAME)
        return FileInputStream(file.fileDescriptor).use { stream ->
            stream.channel.map(FileChannel.MapMode.READ_ONLY, file.startOffset, file.declaredLength)
        }
    }

    override fun close() {
        interpreters.values.forEach { it.close() }
        interpreters.clear()
        closeables.forEach { it.close() }
        closeables.clear()
    }

    private companion object {
        const val TAG = "InterpreterPool"
        const val THREADS = 4
        const val WARM_UP_RUNS = 2
    }
}
