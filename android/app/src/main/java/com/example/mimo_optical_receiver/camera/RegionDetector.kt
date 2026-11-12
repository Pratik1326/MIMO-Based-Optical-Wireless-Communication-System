package com.example.mimo_optical_receiver.camera

import org.opencv.core.Mat
import org.opencv.core.Rect

class RegionDetector {

    /**
     * Slices a 512x512 perspective-corrected matrix into individual MIMO spatial channels based on mode.
     * Uses 0 margin to ensure the outer data grid cells and padding cells are preserved at exact pixel boundaries.
     * 
     * Supported Modes: "1x1", "2x2", "4x4"
     * 
     * @param warpedMat The 512x512 un-skewed screen matrix.
     * @param mimoMode Spatial channel layout configuration ("1x1", "2x2", "4x4").
     * @return List of isolated Mat quadrants for each MIMO spatial channel.
     */
    fun extractChannels(warpedMat: Mat, mimoMode: String): List<Mat> {
        val channels = mutableListOf<Mat>()
        when (mimoMode.lowercase()) {
            "1x1" -> {
                // Full 100% inner data grid (Zero margin cropping)
                channels.add(warpedMat.clone())
            }
            "2x2" -> {
                val halfW = warpedMat.cols() / 2
                val halfH = warpedMat.rows() / 2
                channels.add(warpedMat.submat(Rect(0, 0, halfW, halfH))) // Top-Left
                channels.add(warpedMat.submat(Rect(halfW, 0, halfW, halfH))) // Top-Right
                channels.add(warpedMat.submat(Rect(0, halfH, halfW, halfH))) // Bottom-Left
                channels.add(warpedMat.submat(Rect(halfW, halfH, halfW, halfH))) // Bottom-Right
            }
            "4x4" -> {
                val quarterW = warpedMat.cols() / 4
                val quarterH = warpedMat.rows() / 4
                for (r in 0 until 4) {
                    for (c in 0 until 4) {
                        channels.add(warpedMat.submat(Rect(c * quarterW, r * quarterH, quarterW, quarterH)))
                    }
                }
            }
            else -> {
                channels.add(warpedMat.clone())
            }
        }
        return channels
    }
}
