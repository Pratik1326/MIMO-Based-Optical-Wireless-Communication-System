package com.example.mimo_optical_receiver.transmitter

import com.example.mimo_optical_receiver.protocol.SessionMetadata
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates compact QR handshake bitmaps containing Phase 1 session metadata.
 * Uses high-efficiency compact JSON encoding to produce low-density QR codes
 * that scan reliably under all camera angles and lighting conditions.
 */
class QrCodeGenerator {

    private val writer = QRCodeWriter()

    /**
     * Serializes [SessionMetadata] into a standardized compact JSON payload.
     */
    fun serializeMetadata(metadata: SessionMetadata): String {
        return "{\"f\":\"${metadata.filename}\",\"s\":${metadata.file_size},\"p\":${metadata.total_packets},\"m\":\"${metadata.mimo_mode}\",\"y\":\"${metadata.symbol_mode}\",\"z\":${metadata.packet_size},\"id\":\"${metadata.session_id}\",\"v\":${metadata.protocol_version}}"
    }

    /**
     * Generates a 2D binary grid (0 = white background, 1 = black QR module) of size [targetSize x targetSize].
     */
    fun generateQrGrid(metadata: SessionMetadata, targetSize: Int = 300): Array<IntArray> {
        val json = serializeMetadata(metadata)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = writer.encode(json, BarcodeFormat.QR_CODE, targetSize, targetSize, hints)
        val grid = Array(targetSize) { IntArray(targetSize) }
        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                grid[y][x] = if (matrix.get(x, y)) 1 else 0
            }
        }
        return grid
    }

    /**
     * Renders a QR code to an ARGB [targetSize x targetSize] pixel array.
     */
    fun generateQrPixels(metadata: SessionMetadata, targetSize: Int = 300): IntArray {
        val grid = generateQrGrid(metadata, targetSize)
        val pixels = IntArray(targetSize * targetSize)
        val colorWhite = 0xFFFFFFFF.toInt()
        val colorBlack = 0xFF000000.toInt()

        for (y in 0 until targetSize) {
            val rowOffset = y * targetSize
            val gridRow = grid[y]
            for (x in 0 until targetSize) {
                pixels[rowOffset + x] = if (gridRow[x] == 1) colorBlack else colorWhite
            }
        }
        return pixels
    }
}
