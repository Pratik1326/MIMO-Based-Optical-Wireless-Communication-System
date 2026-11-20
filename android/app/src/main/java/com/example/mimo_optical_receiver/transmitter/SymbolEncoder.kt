package com.example.mimo_optical_receiver.transmitter

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Encodes binary packet data into 2D optical symbol matrices conforming to the
 * MIMO optical communication transmission standard.
 *
 * Each byte is unpacked MSB-first into 8 bits and mapped row-by-row into a
 * (side + 2*padding) x (side + 2*padding) square matrix surrounded by a 1-cell black guard band.
 */
class SymbolEncoder(
    val padding: Int = 1
) {
    init {
        require(padding >= 0) { "padding cannot be negative" }
    }

    /**
     * Unpacks bytes into individual bits (MSB first: bit 7 down to bit 0).
     */
    fun unpackBits(data: ByteArray, minBytes: Int = 0): IntArray {
        val raw = if (minBytes > 0 && data.size < minBytes) {
            val paddingLen = minBytes - data.size
            val padded = ByteArray(minBytes)
            System.arraycopy(data, 0, padded, 0, data.size)
            for (i in 0 until paddingLen) {
                padded[data.size + i] = (((i * 137 + 0x5A) xor 0xA5) and 0xFF).toByte()
            }
            padded
        } else {
            data
        }

        val bits = IntArray(raw.size * 8)
        var bitIdx = 0
        for (b in raw) {
            val byteVal = b.toInt() and 0xFF
            for (shift in 7 downTo 0) {
                bits[bitIdx++] = (byteVal shr shift) and 1
            }
        }
        return bits
    }

    /**
     * Encodes raw packet data into a 2D binary grid (0 = black, 1 = white).
     * Dimensions: (dataSide + 2 * padding) x (dataSide + 2 * padding).
     */
    fun encodeToGrid(data: ByteArray, minBytes: Int = 0): Array<IntArray> {
        val bits = unpackBits(data, minBytes)
        val dataSide = max(1, ceil(sqrt(bits.size.toDouble())).toInt())
        val gridSide = dataSide + (padding * 2)

        val grid = Array(gridSide) { IntArray(gridSide) { 0 } }

        var bitIdx = 0
        for (row in 0 until dataSide) {
            val r = padding + row
            for (col in 0 until dataSide) {
                val c = padding + col
                grid[r][c] = if (bitIdx < bits.size) bits[bitIdx++] else 0
            }
        }

        return grid
    }

    /**
     * Renders the symbol matrix to a 1D ARGB pixel array of dimensions [targetSize x targetSize]
     * using nearest-neighbor cell scaling with zero blur or interpolation bleed.
     * White cells are rendered as 0xFFFFFFFF, Black cells as 0xFF000000.
     */
    fun encodeToPixels(data: ByteArray, targetSize: Int, minBytes: Int = 0): IntArray {
        require(targetSize > 0) { "targetSize must be positive" }
        val grid = encodeToGrid(data, minBytes)
        val gridSide = grid.size

        val pixels = IntArray(targetSize * targetSize)
        val colorWhite = 0xFFFFFFFF.toInt()
        val colorBlack = 0xFF000000.toInt()

        for (y in 0 until targetSize) {
            val gy = (y * gridSide) / targetSize
            val rowOffset = y * targetSize
            val gridRow = grid[gy]
            for (x in 0 until targetSize) {
                val gx = (x * gridSide) / targetSize
                pixels[rowOffset + x] = if (gridRow[gx] == 1) colorWhite else colorBlack
            }
        }

        return pixels
    }
}
