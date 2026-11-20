package com.example.mimo_optical_receiver.transmitter

import kotlin.math.max
import kotlin.math.min

/**
 * High-performance ArUco DICT_4X4_50 marker generator and compositor.
 * Implements pure-Kotlin synthesis of standard ArUco corner anchors (IDs 0, 1, 2, 3),
 * eliminating native JNI dependencies and ensuring 100% testability across unit and UI tests.
 */
class ArucoAnchorGenerator(
    val markerSize: Int = 44,
    val margin: Int = 6
) {
    init {
        require(markerSize > 0) { "markerSize must be positive" }
        require(margin >= 0) { "margin cannot be negative" }
    }

    companion object {
        // Pre-calculated 6x6 ArUco DICT_4X4_50 bit matrices for IDs 0, 1, 2, 3
        // Outer 1-cell border is solid 0 (black). Inner 4x4 carries the marker code.
        val ARUCO_DICT_4X4_50 = arrayOf(
            // ID 0 (Top-Left)
            arrayOf(
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(0, 1, 0, 1, 1, 0),
                intArrayOf(0, 0, 1, 0, 1, 0),
                intArrayOf(0, 0, 0, 1, 1, 0),
                intArrayOf(0, 0, 0, 1, 0, 0),
                intArrayOf(0, 0, 0, 0, 0, 0)
            ),
            // ID 1 (Top-Right)
            arrayOf(
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(0, 1, 1, 1, 1, 0),
                intArrayOf(0, 1, 0, 0, 1, 0),
                intArrayOf(0, 1, 0, 1, 0, 0),
                intArrayOf(0, 0, 0, 0, 0, 0)
            ),
            // ID 2 (Bottom-Right)
            arrayOf(
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(0, 0, 0, 1, 1, 0),
                intArrayOf(0, 0, 0, 1, 1, 0),
                intArrayOf(0, 0, 0, 1, 0, 0),
                intArrayOf(0, 1, 1, 0, 1, 0),
                intArrayOf(0, 0, 0, 0, 0, 0)
            ),
            // ID 3 (Bottom-Left)
            arrayOf(
                intArrayOf(0, 0, 0, 0, 0, 0),
                intArrayOf(0, 1, 0, 0, 1, 0),
                intArrayOf(0, 1, 0, 0, 1, 0),
                intArrayOf(0, 0, 1, 0, 0, 0),
                intArrayOf(0, 0, 1, 1, 0, 0),
                intArrayOf(0, 0, 0, 0, 0, 0)
            )
        )
    }

    /**
     * Renders a single 6x6 marker to a [markerSize x markerSize] 2D binary grid.
     */
    fun generateMarker(markerId: Int): Array<IntArray> {
        require(markerId in 0..3) { "Only ArUco IDs 0..3 are supported" }
        val pattern = ARUCO_DICT_4X4_50[markerId]
        val result = Array(markerSize) { IntArray(markerSize) }

        for (my in 0 until markerSize) {
            val gy = (my * 6) / markerSize
            val row = pattern[gy]
            for (mx in 0 until markerSize) {
                val gx = (mx * 6) / markerSize
                result[my][mx] = row[gx]
            }
        }
        return result
    }

    /**
     * Composites all four ArUco anchors (IDs 0, 1, 2, 3) with surrounding white quiet zones
     * into the target pixel buffer [width x height].
     */
    fun placeAnchors(
        pixels: IntArray,
        width: Int,
        height: Int,
        markerIds: IntArray = intArrayOf(0, 1, 2, 3)
    ) {
        val minSpan = markerSize * 2 + margin * 2
        require(width >= minSpan && height >= minSpan) {
            "Frame dimensions ($width x $height) are too small for 4 corner markers (min: $minSpan)"
        }

        val colorWhite = 0xFFFFFFFF.toInt()
        val colorBlack = 0xFF000000.toInt()

        // 4 Corner positions: TL(0), TR(1), BR(2), BL(3)
        val positions = arrayOf(
            Pair(margin, margin),
            Pair(width - margin - markerSize, margin),
            Pair(width - margin - markerSize, height - margin - markerSize),
            Pair(margin, height - margin - markerSize)
        )

        for (i in 0 until 4) {
            val markerId = markerIds[i]
            val (x, y) = positions[i]

            // 1. Draw crisp white quiet zone rectangle around marker
            val quietLeft = max(0, x - margin)
            val quietTop = max(0, y - margin)
            val quietRight = min(width - 1, x + markerSize + margin - 1)
            val quietBottom = min(height - 1, y + markerSize + margin - 1)

            for (qy in quietTop..quietBottom) {
                val rowOffset = qy * width
                for (qx in quietLeft..quietRight) {
                    pixels[rowOffset + qx] = colorWhite
                }
            }

            // 2. Render scaled ArUco marker into (x, y)
            val pattern = ARUCO_DICT_4X4_50[markerId]
            for (my in 0 until markerSize) {
                val py = y + my
                if (py >= height) continue
                val rowOffset = py * width
                val gy = (my * 6) / markerSize
                val patternRow = pattern[gy]

                for (mx in 0 until markerSize) {
                    val px = x + mx
                    if (px >= width) continue
                    val gx = (mx * 6) / markerSize
                    val bit = patternRow[gx]
                    pixels[rowOffset + px] = if (bit == 1) colorWhite else colorBlack
                }
            }
        }
    }
}
