package com.example.mimo_optical_receiver

import com.example.mimo_optical_receiver.transmitter.FrameGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FrameGeneratorTest {

    @Test
    fun testFrameDimensionsAreEdgeToEdge() {
        val generator = FrameGenerator()
        // With safeZonePercent = 0.0f, totalWidth and totalHeight equal baseWidth/baseHeight (800x800)
        assertEquals(800, generator.totalWidth)
        assertEquals(800, generator.totalHeight)
        assertEquals(0, generator.safeZoneMargin)
    }

    @Test
    fun testFrameGenerator1x12x24x4DataRendering() {
        val generator = FrameGenerator()

        for (mode in listOf("1x1", "2x2", "4x4")) {
            val side = when (mode) {
                "1x1" -> 1
                "2x2" -> 2
                "4x4" -> 4
                else -> 1
            }
            val chCount = side * side
            val chDim = 688 / side

            val channelMap = mutableMapOf<Int, IntArray>()
            for (ch in 0 until chCount) {
                channelMap[ch] = IntArray(chDim * chDim) { 0xFFFFFFFF.toInt() }
            }

            val targetBuf = IntArray(generator.totalWidth * generator.totalHeight)
            generator.renderDataInto(targetBuf, channelMap, chDim, mode)

            // Verify outer border at (0, 0) is white
            assertEquals(FrameGenerator.COLOR_WHITE, targetBuf[0])
            assertEquals(FrameGenerator.COLOR_WHITE, targetBuf[generator.totalWidth - 1])
            assertEquals(FrameGenerator.COLOR_WHITE, targetBuf[(generator.totalHeight - 1) * generator.totalWidth])
            assertEquals(FrameGenerator.COLOR_WHITE, targetBuf[(generator.totalHeight - 1) * generator.totalWidth + generator.totalWidth - 1])

            // Verify data inside quadrant 0 is white (active data block drawn)
            val quad0Center = 200 * generator.totalWidth + 200
            assertEquals(FrameGenerator.COLOR_WHITE, targetBuf[quad0Center])

            // If multi-channel (2x2, 4x4), verify separating grid line at center (400, 400) is black
            if (side > 1) {
                val centerOffset = 400 * generator.totalWidth + 400
                assertEquals(FrameGenerator.COLOR_BLACK, targetBuf[centerOffset])
            }
        }
    }
}
