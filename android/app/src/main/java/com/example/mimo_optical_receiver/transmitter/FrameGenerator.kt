package com.example.mimo_optical_receiver.transmitter

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Spatial channel compositor for MIMO optical frames.
 * Accurately implements 1x1, 2x2, and 4x4 spatial multiplexing layouts,
 * corner ArUco tracking anchors, safe zone margins, and Phase 1 QR handshakes.
 */
class FrameGenerator(
    val baseWidth: Int = 800,
    val baseHeight: Int = 800,
    val markerSize: Int = 44,
    val markerMargin: Int = 6,
    val borderThickness: Int = 4,
    val gridThickness: Int = 2,
    val safeZonePercent: Float = 0.10f
) {
    val safeZoneMargin = (baseWidth * safeZonePercent).roundToInt()
    val totalWidth = baseWidth + (safeZoneMargin * 2)
    val totalHeight = baseHeight + (safeZoneMargin * 2)

    private val anchorGenerator = ArucoAnchorGenerator(markerSize, markerMargin)
    private val qrCodeGenerator = QrCodeGenerator()

    companion object {
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_BLACK = 0xFF000000.toInt()
    }

    val baseTemplate: IntArray by lazy { createBaseTemplate() }

    private fun createBaseTemplate(): IntArray {
        val basePixels = IntArray(baseWidth * baseHeight) { COLOR_BLACK }
        drawRectOutline(basePixels, baseWidth, baseHeight, 0, 0, baseWidth - 1, baseHeight - 1, borderThickness, COLOR_WHITE)
        anchorGenerator.placeAnchors(basePixels, baseWidth, baseHeight)
        return applySafeZone(basePixels)
    }

    /**
     * Fast zero-allocation rendering of a MIMO data frame directly into targetPixels.
     * Reuses precomputed base template (outer border, ArUco corner anchors, safe zone),
     * blits channel data blocks, and draws separating grid lines.
     */
    fun renderDataInto(
        targetPixels: IntArray,
        channelPixels: Map<Int, IntArray>,
        channelDim: Int,
        mimoMode: String
    ) {
        System.arraycopy(baseTemplate, 0, targetPixels, 0, totalWidth * totalHeight)

        val side = when (mimoMode.lowercase()) {
            "1x1" -> 1
            "2x2" -> 2
            "4x4" -> 4
            else -> 1
        }
        val channelCount = side * side

        val cornerZone = markerSize + markerMargin * 2
        val innerLeft = cornerZone
        val innerTop = cornerZone
        val innerWidth = baseWidth - cornerZone * 2
        val innerHeight = baseHeight - cornerZone * 2

        // 2. Draw spatial channel blocks directly into targetPixels
        for (chId in 0 until channelCount) {
            val row = chId / side
            val col = chId % side
            val x0 = innerLeft + col * innerWidth / side
            val x1 = innerLeft + (col + 1) * innerWidth / side
            val y0 = innerTop + row * innerHeight / side
            val y1 = innerTop + (row + 1) * innerHeight / side
            val quadW = x1 - x0
            val quadH = y1 - y0

            val chData = channelPixels[chId]
            if (chData != null && quadW > 0 && quadH > 0 && channelDim > 0) {
                for (qy in 0 until quadH) {
                    val srcY = (qy * channelDim) / quadH
                    val srcRowOffset = srcY * channelDim
                    val dstRowOffset = (y0 + qy + safeZoneMargin) * totalWidth + (x0 + safeZoneMargin)
                    for (qx in 0 until quadW) {
                        val srcX = (qx * channelDim) / quadW
                        targetPixels[dstRowOffset + qx] = chData[srcRowOffset + srcX]
                    }
                }
            }
        }

        // 3. Draw separating grid lines between channels
        if (gridThickness > 0 && side > 1) {
            for (coord in 1 until side) {
                val x = innerLeft + coord * innerWidth / side
                val y = innerTop + coord * innerHeight / side
                // Vertical line
                for (py in innerTop until (innerTop + innerHeight)) {
                    val dstRowOffset = (py + safeZoneMargin) * totalWidth
                    for (gt in 0 until gridThickness) {
                        val px = (x + gt).coerceIn(0, baseWidth - 1) + safeZoneMargin
                        targetPixels[dstRowOffset + px] = COLOR_BLACK
                    }
                }
                // Horizontal line
                for (gt in 0 until gridThickness) {
                    val dstRowOffset = (y + gt + safeZoneMargin).coerceIn(0, totalHeight - 1) * totalWidth
                    for (px in innerLeft until (innerLeft + innerWidth)) {
                        targetPixels[dstRowOffset + px + safeZoneMargin] = COLOR_BLACK
                    }
                }
            }
            drawRectOutline(
                targetPixels,
                totalWidth,
                totalHeight,
                innerLeft + safeZoneMargin,
                innerTop + safeZoneMargin,
                innerLeft + innerWidth - 1 + safeZoneMargin,
                innerTop + innerHeight - 1 + safeZoneMargin,
                gridThickness,
                COLOR_BLACK
            )
        }
    }

    /**
     * Renders a Phase 2 MIMO data frame combining up to 16 spatial channels.
     *
     * @param channelPixels Map of channel ID (0..15) to channel ARGB pixel array.
     * @param channelDim Dimension of each channel pixel matrix (e.g. 384 for 2x2).
     * @param mimoMode "1x1", "2x2", or "4x4".
     */
    fun renderDataFrame(
        channelPixels: Map<Int, IntArray>,
        channelDim: Int,
        mimoMode: String
    ): IntArray {
        val result = IntArray(totalWidth * totalHeight)
        renderDataInto(result, channelPixels, channelDim, mimoMode)
        return result
    }

    /**
     * Renders a Phase 1 QR Handshake frame with corner ArUco anchors and centered high-contrast card.
     */
    fun renderQrFrame(qrPixels: IntArray, qrSize: Int): IntArray {
        val basePixels = IntArray(baseWidth * baseHeight) { COLOR_BLACK }
        drawRectOutline(basePixels, baseWidth, baseHeight, 0, 0, baseWidth - 1, baseHeight - 1, borderThickness, COLOR_WHITE)

        val cardInset = markerSize + markerMargin * 2 + 10
        val cardWidth = baseWidth - cardInset * 2
        val cardHeight = baseHeight - cardInset * 2

        // Draw solid white background card
        for (y in cardInset until (cardInset + cardHeight)) {
            val rowOffset = y * baseWidth
            for (x in cardInset until (cardInset + cardWidth)) {
                basePixels[rowOffset + x] = COLOR_WHITE
            }
        }

        // Center and scale QR code inside the card with clean quiet margins
        val maxQrSize = min(cardWidth, cardHeight) - 48
        val qrLeft = cardInset + (cardWidth - maxQrSize) / 2
        val qrTop = cardInset + (cardHeight - maxQrSize) / 2

        for (qy in 0 until maxQrSize) {
            val srcY = (qy * qrSize) / maxQrSize
            val srcRow = srcY * qrSize
            val dstRow = (qrTop + qy) * baseWidth + qrLeft
            for (qx in 0 until maxQrSize) {
                val srcX = (qx * qrSize) / maxQrSize
                basePixels[dstRow + qx] = qrPixels[srcRow + srcX]
            }
        }

        // Place ArUco corner anchors
        anchorGenerator.placeAnchors(basePixels, baseWidth, baseHeight)

        // Safe zone black margin
        return applySafeZone(basePixels)
    }

    private fun applySafeZone(source: IntArray): IntArray {
        if (safeZoneMargin <= 0) return source
        val result = IntArray(totalWidth * totalHeight) { COLOR_BLACK }

        for (y in 0 until baseHeight) {
            val srcOffset = y * baseWidth
            val dstOffset = (y + safeZoneMargin) * totalWidth + safeZoneMargin
            System.arraycopy(source, srcOffset, result, dstOffset, baseWidth)
        }
        return result
    }

    private fun drawRectOutline(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        thickness: Int,
        color: Int
    ) {
        for (t in 0 until thickness) {
            // Top and Bottom horizontal lines
            val topRow = (y0 + t) * width
            val bottomRow = (y1 - t) * width
            for (x in x0..x1) {
                if (y0 + t in 0 until height && x in 0 until width) pixels[topRow + x] = color
                if (y1 - t in 0 until height && x in 0 until width) pixels[bottomRow + x] = color
            }
            // Left and Right vertical lines
            for (y in y0..y1) {
                if (y in 0 until height) {
                    if (x0 + t in 0 until width) pixels[y * width + (x0 + t)] = color
                    if (x1 - t in 0 until width) pixels[y * width + (x1 - t)] = color
                }
            }
        }
    }
}
