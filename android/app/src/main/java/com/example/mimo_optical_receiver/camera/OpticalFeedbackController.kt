package com.example.mimo_optical_receiver.camera

import android.util.Log
import androidx.camera.core.CameraControl
import kotlinx.coroutines.*

class OpticalFeedbackController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cameraControl: CameraControl? = null
    private var isPulsing = false

    fun attachCameraControl(control: CameraControl?) {
        this.cameraControl = control
        Log.d("OPTICAL_FEEDBACK", "CameraControl attached successfully: " + control)
    }

    /**
     * Pattern 1: Receiver Locked & Ready (1 pulse, 280ms)
     */
    fun sendReadySignal() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 1: READY / LOCKED")
        pulse(count = 1, pulseDurationMs = 280, gapDurationMs = 200)
    }

    /**
     * Pattern 2: NACK / Retransmit Missing Packet (2 pulses, 220ms ON / 250ms OFF)
     */
    fun sendRetransmitSignal() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 2: NACK / RETRANSMIT")
        pulse(count = 2, pulseDurationMs = 220, gapDurationMs = 250)
    }

    /**
     * Pattern 3: Transfer Complete & SHA-256 Verified ACK
     * Sends an 850ms sustained strobe (triggering immediate PC auto-off at >=650ms),
     * followed by two verification pulses (200ms ON / 220ms OFF).
     */
    fun sendCompletionAck() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 3: COMPLETE ACK (Sustained Strobe + Verification)")
        val control = cameraControl ?: run {
            Log.w("OPTICAL_FEEDBACK", "Cannot pulse flash: CameraControl is null")
            return
        }
        if (isPulsing) return

        scope.launch {
            isPulsing = true
            try {
                // 1. Sustained strobe (>= 650ms threshold on PC triggers instant auto-stop)
                control.enableTorch(true)
                delay(850)
                control.enableTorch(false)
                delay(250)

                // 2. Verification pulses
                for (i in 0 until 2) {
                    control.enableTorch(true)
                    delay(200)
                    control.enableTorch(false)
                    if (i < 1) delay(220)
                }
            } catch (e: Exception) {
                Log.e("OPTICAL_FEEDBACK", "Error in sendCompletionAck", e)
            } finally {
                try {
                    control.enableTorch(false)
                } catch (_: Exception) {}
                isPulsing = false
            }
        }
    }

    private fun pulse(count: Int, pulseDurationMs: Long, gapDurationMs: Long) {
        val control = cameraControl ?: run {
            Log.w("OPTICAL_FEEDBACK", "Cannot pulse flash: CameraControl is null")
            return
        }
        if (isPulsing) return

        scope.launch {
            isPulsing = true
            try {
                for (i in 0 until count) {
                    control.enableTorch(true)
                    delay(pulseDurationMs)
                    control.enableTorch(false)
                    if (i < count - 1) {
                        delay(gapDurationMs)
                    }
                }
            } catch (e: Exception) {
                Log.e("OPTICAL_FEEDBACK", "Error toggling flash torch", e)
            } finally {
                try {
                    control.enableTorch(false)
                } catch (_: Exception) {}
                isPulsing = false
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}
