package com.example.mimo_optical_receiver

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class ZxingDiagnosticTest {

    @Test
    fun testZxingOnFrame07() {
        val frameFile = File("C:/Users/pratik/.gemini/antigravity/brain/ba6a0fa3-0fd4-4579-a0d0-26a68bef5129/scratch/mimo4_frames/frame_07_10.4s.png")
        if (!frameFile.exists()) {
            println("Frame file not found: ${frameFile.absolutePath}")
            return
        }

        val bImage: BufferedImage = ImageIO.read(frameFile)
        val width = bImage.width
        val height = bImage.height
        val pixels = IntArray(width * height)
        bImage.getRGB(0, 0, width, height, pixels, 0, width)

        // Convert ARGB to grayscale byte buffer as PlanarYUVLuminanceSource expects
        val yBuffer = ByteArray(width * height)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            yBuffer[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
        }

        val source = PlanarYUVLuminanceSource(yBuffer, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val reader = MultiFormatReader().apply {
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            setHints(hints)
        }

        try {
            val result = reader.decodeWithState(bitmap)
            println("ZXing SUCCESS on full frame 07: ${result.text}")
        } catch (e: Exception) {
            println("ZXing FAILED on full frame 07: ${e.javaClass.simpleName} - ${e.message}")
        }

        // Now test crop around the QR code
        // QR was at [225, 639] to [402, 809]
        val cropX = 200
        val cropY = 600
        val cropW = 220
        val cropH = 220
        val cropSource = PlanarYUVLuminanceSource(yBuffer, width, height, cropX, cropY, cropW, cropH, false)
        val cropBitmap = BinaryBitmap(HybridBinarizer(cropSource))
        try {
            reader.reset()
            val result = reader.decodeWithState(cropBitmap)
            println("ZXing SUCCESS on CROPPED frame 07: ${result.text}")
        } catch (e: Exception) {
            println("ZXing FAILED on CROPPED frame 07: ${e.javaClass.simpleName} - ${e.message}")
        }
    }
}
