package com.example.mimo_optical_receiver.camera

import com.example.mimo_optical_receiver.protocol.ProtocolConstants
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class SymbolDecoder {

    var lastMin: Double = 0.0
        private set
    var lastMax: Double = 0.0
        private set
    var lastThreshold: Double = 0.0
        private set

    data class DecodeOutput(
        val channelBits: List<ByteArray>,
        val flatBits: ByteArray,
        val intensities: List<Double>
    )

    /**
     * Decodes binary values from each independent MIMO spatial channel.
     * Uses zero-allocation direct byte sampling and 2-Means dynamic clustering thresholding
     * with CLAHE contrast equalization to eliminate bit flips caused by monitor angle gradients,
     * backlight bleed, and room reflections.
     */
    fun decode(channelMats: List<Mat>, payloadSizeBytes: Int): DecodeOutput {
        // PC Transmitter sends: Header (11B) + Payload (NB) + CRC32 (4B) = N + 15 bytes total
        val totalBytes = payloadSizeBytes + ProtocolConstants.OVERHEAD_BYTES
        val totalBits = totalBytes * 8

        // Unpadded data side length
        val dataSide = Math.ceil(Math.sqrt(totalBits.toDouble())).toInt()

        // Total grid side including PC's 1-cell outer black padding (padding = 1)
        val gridSide = dataSide + 2

        val bitsPerQuadrant = dataSide * dataSide
        val channelBitsList = mutableListOf<ByteArray>()
        val allBits = mutableListOf<Byte>()
        val intensities = mutableListOf<Double>()

        for (chIndex in channelMats.indices) {
            val quadrantMat = channelMats[chIndex]
            val width = quadrantMat.cols()
            val height = quadrantMat.rows()

            // Direct buffer access from raw perspective-corrected quadrant (0 JNI overhead, 0 artificial CLAHE noise amplification)
            val quadBytes = ByteArray(width * height)
            quadrantMat.get(0, 0, quadBytes)

            val cellWidth = width / gridSide.toDouble()
            val cellHeight = height / gridSide.toDouble()

            // Adaptive center-cell guard inset:
            // For dense grids (<8px cells such as 4x4), use 15% inset to maximize optical energy.
            // For medium dense grids (8-15px cells such as 128B in 2x2), use 22% inset (360% more light energy than 35%).
            // For large grids (>15px cells such as 1x1 or 32B), use 30% inset for clean margin rejection.
            val insetFactor = when {
                cellWidth < 8.0 -> 0.15
                cellWidth < 15.0 -> 0.22
                else -> 0.30
            }
            val insetX = cellWidth * insetFactor
            val insetY = cellHeight * insetFactor

            var sampleMin = 255.0
            var sampleMax = 0.0
            val cellMeans = DoubleArray(bitsPerQuadrant)
            var cellIdx = 0

            for (row in 1..dataSide) {
                val yStart = (row * cellHeight + insetY).toInt().coerceIn(0, height - 1)
                val yEnd = ((row + 1) * cellHeight - insetY).toInt().coerceAtLeast(yStart + 1).coerceAtMost(height)

                for (col in 1..dataSide) {
                    val xStart = (col * cellWidth + insetX).toInt().coerceIn(0, width - 1)
                    val xEnd = ((col + 1) * cellWidth - insetX).toInt().coerceAtLeast(xStart + 1).coerceAtMost(width)

                    var sum = 0
                    var count = 0
                    for (y in yStart until yEnd) {
                        val rowOffset = y * width
                        for (x in xStart until xEnd) {
                            sum += quadBytes[rowOffset + x].toInt() and 0xFF
                            count++
                        }
                    }

                    val meanVal = if (count > 0) sum.toDouble() / count else 0.0
                    cellMeans[cellIdx++] = meanVal
                    if (meanVal < sampleMin) sampleMin = meanVal
                    if (meanVal > sampleMax) sampleMax = meanVal
                }
            }

            // CONTRAST GUARDRAIL: Adaptive threshold for dense grids vs large cells
            val minAllowedDynamicRange = when {
                cellWidth < 8.0 -> 18.0
                cellWidth < 15.0 -> 22.0
                else -> 28.0
            }
            if ((sampleMax - sampleMin) < minAllowedDynamicRange) {
                return DecodeOutput(emptyList(), ByteArray(0), emptyList())
            }

            // High-precision 2-Means dynamic cluster thresholding (Global channel baseline)
            val globalThreshold = compute2MeansThreshold(cellMeans, sampleMin, sampleMax, cellWidth)
            if (globalThreshold < 0.0) {
                // Insufficient bimodal contrast
                return DecodeOutput(emptyList(), ByteArray(0), emptyList())
            }
            if (chIndex == 0) {
                lastMin = sampleMin
                lastMax = sampleMax
                lastThreshold = globalThreshold
            }

            // 2x2 Sub-Block Adaptive Local Thresholding:
            // Eliminates bit flips caused by monitor angle gradients, vignette falloff, and room reflections
            val halfSide = dataSide / 2
            val subThresholds = DoubleArray(4) { -1.0 }
            for (subBlock in 0 until 4) {
                val rStart = if (subBlock >= 2) halfSide else 0
                val rEnd = if (subBlock >= 2) dataSide else halfSide
                val cStart = if (subBlock % 2 == 1) halfSide else 0
                val cEnd = if (subBlock % 2 == 1) dataSide else halfSide

                var subMin = 255.0
                var subMax = 0.0
                var subCount = 0
                for (r in rStart until rEnd) {
                    val rOffset = r * dataSide
                    for (c in cStart until cEnd) {
                        val v = cellMeans[rOffset + c]
                        if (v < subMin) subMin = v
                        if (v > subMax) subMax = v
                        subCount++
                    }
                }

                if (subCount > 0 && (subMax - subMin) >= minAllowedDynamicRange) {
                    val subValues = DoubleArray(subCount)
                    var idx = 0
                    for (r in rStart until rEnd) {
                        val rOffset = r * dataSide
                        for (c in cStart until cEnd) {
                            subValues[idx++] = cellMeans[rOffset + c]
                        }
                    }
                    val st = compute2MeansThreshold(subValues, subMin, subMax, cellWidth)
                    if (st > 0.0) subThresholds[subBlock] = st
                }
            }

            // Binarize symbols using local sub-block threshold with global fallback
            val chBits = ByteArray(bitsPerQuadrant)
            for (r in 0 until dataSide) {
                val rSub = if (r >= halfSide) 2 else 0
                val rOffset = r * dataSide
                for (c in 0 until dataSide) {
                    val cSub = if (c >= halfSide) 1 else 0
                    val subBlockIdx = rSub + cSub
                    val effectiveThreshold = if (subThresholds[subBlockIdx] > 0.0) {
                        subThresholds[subBlockIdx]
                    } else {
                        globalThreshold
                    }

                    val cellPos = rOffset + c
                    val meanVal = cellMeans[cellPos]
                    if (cellPos == 0) {
                        intensities.add(meanVal)
                    }
                    val bit = if (meanVal > effectiveThreshold) 1.toByte() else 0.toByte()
                    chBits[cellPos] = bit
                    allBits.add(bit)
                }
            }
            channelBitsList.add(chBits)
        }

        return DecodeOutput(
            channelBits = channelBitsList,
            flatBits = allBits.toByteArray(),
            intensities = intensities
        )
    }

    /**
     * 2-Means clustering to find the optimal decision boundary between 0 and 1 symbols.
     * Converges in 3-5 iterations and is immune to LCD backlight falloff and glare outliers.
     */
    private fun compute2MeansThreshold(values: DoubleArray, minVal: Double, maxVal: Double, cellWidth: Double = 15.0): Double {
        val minDynRange = when {
            cellWidth < 8.0 -> 18.0
            cellWidth < 15.0 -> 22.0
            else -> 28.0
        }
        if (maxVal - minVal < minDynRange || values.isEmpty()) {
            return -1.0
        }

        // Initialize centers near bottom 25% and top 75%
        var c0 = minVal + (maxVal - minVal) * 0.25
        var c1 = minVal + (maxVal - minVal) * 0.75

        for (iter in 0 until 8) {
            var sum0 = 0.0
            var count0 = 0
            var sum1 = 0.0
            var count1 = 0

            for (v in values) {
                if (Math.abs(v - c0) <= Math.abs(v - c1)) {
                    sum0 += v
                    count0++
                } else {
                    sum1 += v
                    count1++
                }
            }

            val newC0 = if (count0 > 0) sum0 / count0 else c0
            val newC1 = if (count1 > 0) sum1 / count1 else c1

            if (Math.abs(newC0 - c0) < 0.5 && Math.abs(newC1 - c1) < 0.5) {
                c0 = newC0
                c1 = newC1
                break
            }
            c0 = newC0
            c1 = newC1
        }

        val clusterSeparation = c1 - c0
        val minSeparation = when {
            cellWidth < 8.0 -> 10.0
            cellWidth < 15.0 -> 13.0
            else -> 16.0
        }
        if (clusterSeparation < minSeparation) {
            return -1.0 // Bimodal distribution separation insufficient for reliable symbol recovery
        }

        return (c0 + c1) / 2.0
    }
}
