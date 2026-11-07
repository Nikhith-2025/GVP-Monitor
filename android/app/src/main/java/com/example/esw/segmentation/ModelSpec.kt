package com.example.esw.segmentation

import java.util.TreeMap

/** The shape of the YOLO11 segmentation model in the assets folder. */
object ModelSpec {
    const val ASSET_NAME = "yolo11_seg.tflite"
    const val INPUT_SIZE = 640
    const val INPUT_BYTES = INPUT_SIZE * INPUT_SIZE * 3 * 4

    const val OUTPUT_ROWS = 39
    const val CANDIDATES = 8400
    const val PROTOTYPE_SIZE = 160
    const val PROTOTYPE_COUNT = 32
}

/** Buffers the model writes into: detection candidates and mask prototypes. */
class ModelOutputs {
    val detections = Array(1) { Array(ModelSpec.OUTPUT_ROWS) { FloatArray(ModelSpec.CANDIDATES) } }
    val prototypes = Array(1) {
        Array(ModelSpec.PROTOTYPE_SIZE) {
            Array(ModelSpec.PROTOTYPE_SIZE) { FloatArray(ModelSpec.PROTOTYPE_COUNT) }
        }
    }

    fun asMap(): Map<Int, Any> = TreeMap<Int, Any>().apply {
        put(0, detections)
        put(1, prototypes)
    }
}
