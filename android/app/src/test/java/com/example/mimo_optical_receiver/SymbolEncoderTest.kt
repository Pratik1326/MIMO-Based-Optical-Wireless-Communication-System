package com.example.mimo_optical_receiver

import com.example.mimo_optical_receiver.transmitter.ArucoAnchorGenerator
import com.example.mimo_optical_receiver.transmitter.SymbolEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolEncoderTest {

    @Test
    fun testUnpackBits() {
        val encoder = SymbolEncoder(padding = 1)
        val testBytes = byteArrayOf(0b10110001.toByte(), 0b01001110.toByte())
        val bits = encoder.unpackBits(testBytes)

        assertEquals(16, bits.size)
        // Byte 0: 1, 0, 1, 1, 0, 0, 0, 1
        assertEquals(1, bits[0])
        assertEquals(0, bits[1])
        assertEquals(1, bits[2])
        assertEquals(1, bits[3])
        assertEquals(0, bits[4])
        assertEquals(0, bits[5])
        assertEquals(0, bits[6])
        assertEquals(1, bits[7])

        // Byte 1: 0, 1, 0, 0, 1, 1, 1, 0
        assertEquals(0, bits[8])
        assertEquals(1, bits[9])
        assertEquals(0, bits[10])
        assertEquals(0, bits[11])
        assertEquals(1, bits[12])
        assertEquals(1, bits[13])
        assertEquals(1, bits[14])
        assertEquals(0, bits[15])
    }

    @Test
    fun testEncodeToGrid_hasPaddingBorder() {
        val encoder = SymbolEncoder(padding = 1)
        val data = byteArrayOf(0xFF.toByte()) // 8 bits
        val grid = encoder.encodeToGrid(data)

        // 8 bits -> side = ceil(sqrt(8)) = 3.
        // gridSide = 3 + 2 = 5.
        assertEquals(5, grid.size)
        assertEquals(5, grid[0].size)

        // Outer borders must be 0 (black guard band)
        for (i in 0 until 5) {
            assertEquals(0, grid[0][i])
            assertEquals(0, grid[4][i])
            assertEquals(0, grid[i][0])
            assertEquals(0, grid[i][4])
        }

        // Inner 3x3 data core should contain bits
        var innerOnes = 0
        for (r in 1..3) {
            for (c in 1..3) {
                if (grid[r][c] == 1) innerOnes++
            }
        }
        assertEquals(8, innerOnes)
    }

    @Test
    fun testArucoAnchorBitPattern() {
        val generator = ArucoAnchorGenerator(markerSize = 44, margin = 6)
        for (id in 0..3) {
            val marker = generator.generateMarker(id)
            assertEquals(44, marker.size)
            assertEquals(44, marker[0].size)

            // Outer perimeter of 6x6 marker scaled to 44px should be solid black
            assertEquals(0, marker[0][0])
            assertEquals(0, marker[43][43])
        }
    }
}
