package com.example.esw.capture

import android.os.Handler
import android.os.Looper

/** Calls onTick at a fixed interval until stopped. */
class AutoCaptureController(private val onTick: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var intervalMs = 0L

    var isRunning = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            onTick()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start(intervalSeconds: Int) {
        intervalMs = intervalSeconds * 1000L
        isRunning = true
        handler.postDelayed(tick, intervalMs)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(tick)
    }
}
