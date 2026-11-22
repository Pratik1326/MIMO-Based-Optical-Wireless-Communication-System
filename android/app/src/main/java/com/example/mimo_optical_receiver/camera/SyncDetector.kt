package com.example.mimo_optical_receiver.camera

import org.opencv.core.Core
import org.opencv.core.Mat

class SyncDetector(
    private val settlingThreshold: Double = 35.0,
    private val stableThreshold: Double = 22.0,
    private val requiredSettledFrames: Int = 1
) {
    
    private var prevMat: Mat? = null
    private var isSettling = false
    private var settledConsecutiveFrames = 0

    /**
     * Compares the current warped matrix against the previous one to drop duplicate frames
     * and filter out LCD screen tearing (half-drawn frames).
     * 
     * Handheld camera shake typically has meanDiff between 6.0 and 15.0, whereas a monitor frame
     * swap causes a transient jump > 26.0.
     * 
     * @return Pair<Boolean, Int> where Boolean is `isNewFrame` and Int is `syncConfidenceScore` (0-100).
     */
    fun isNewFrame(currentMat: Mat): Pair<Boolean, Int> {
        if (prevMat == null) {
            prevMat = currentMat.clone()
            return Pair(true, 100)
        }

        val diffMat = Mat()
        return try {
            // Calculate absolute difference between current frame (Ft) and previous frame (Ft-1)
            Core.absdiff(currentMat, prevMat, diffMat)
            
            val meanDiff = Core.mean(diffMat).`val`[0]

            // Always update prevMat so diffMat measures consecutive inter-frame delta
            prevMat?.release()
            prevMat = currentMat.clone()

            if (meanDiff > settlingThreshold) {
                // SCREEN FLIPPING / TEARING DETECTED: Monitor is mid-draw or switching frames
                isSettling = true
                settledConsecutiveFrames = 0
                Pair(false, 15) // Mid-draw tearing
            } else if (isSettling) {
                if (meanDiff < stableThreshold) {
                    settledConsecutiveFrames++
                    if (settledConsecutiveFrames >= requiredSettledFrames) {
                        isSettling = false
                        settledConsecutiveFrames = 0
                        Pair(true, 100) // Clean, un-torn fresh frame ready for decoding!
                    } else {
                        Pair(false, 60) // Settling in progress
                    }
                } else {
                    settledConsecutiveFrames = 0
                    Pair(false, 25) // Still fluctuating/tearing
                }
            } else {
                // Steady state: Frame is stable and settled (handheld micro-shake stays <= 18.0)
                Pair(false, 100) // 100% link lock confidence during settled frames!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, 0)
        } finally {
            diffMat.release()
        }
    }

    /**
     * Should be called when the pipeline is destroyed or reset to prevent memory leaks.
     */
    fun release() {
        prevMat?.release()
        prevMat = null
        isSettling = false
        settledConsecutiveFrames = 0
    }
}
