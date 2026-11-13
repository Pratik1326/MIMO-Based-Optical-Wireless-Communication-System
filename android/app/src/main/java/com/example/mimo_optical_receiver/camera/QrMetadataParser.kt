package com.example.mimo_optical_receiver.camera

import android.util.Log
import com.example.mimo_optical_receiver.protocol.SessionMetadata
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector

class QrMetadataParser {
    
    private val qrDetector = QRCodeDetector()

    private val zxingReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    /**
     * Attempts to detect and decode a QR code.
     * Uses ZXing with HybridBinarizer on warpedMat (unskewed screen area) as primary,
     * then raw camera frame, adaptive thresholding, and OpenCV QRCodeDetector as fallback.
     */
    fun detectAndParse(primaryMat: Mat, secondaryMat: Mat? = null): SessionMetadata? {
        // 1. Tier 1 (Best): ZXing on un-skewed warped screen matrix
        if (secondaryMat != null && !secondaryMat.empty()) {
            val res = decodeMatZxing(secondaryMat)
            if (res != null) return parseJsonMetadata(res)
        }

        // 1b. Tier 1b: ZXing on central inset of warped matrix (isolates QR card and eliminates peripheral edge artifacts)
        if (secondaryMat != null && !secondaryMat.empty()) {
            val insetW = (secondaryMat.cols() * 0.06).toInt()
            val insetH = (secondaryMat.rows() * 0.06).toInt()
            val innerW = secondaryMat.cols() - insetW * 2
            val innerH = secondaryMat.rows() - insetH * 2
            if (innerW > 100 && innerH > 100) {
                val centerSub = secondaryMat.submat(org.opencv.core.Rect(insetW, insetH, innerW, innerH))
                try {
                    val res = decodeMatZxing(centerSub)
                    if (res != null) return parseJsonMetadata(res)
                } finally {
                    centerSub.release()
                }
            }
        }

        // 2. Tier 2: ZXing on raw camera frame
        if (!primaryMat.empty()) {
            val res = decodeMatZxing(primaryMat)
            if (res != null) return parseJsonMetadata(res)
        }

        // 3. Tier 3: ZXing on Otsu-thresholded warped matrix (cuts through glare / backlight bleed)
        if (secondaryMat != null && !secondaryMat.empty()) {
            val otsuWarped = Mat()
            try {
                Imgproc.threshold(secondaryMat, otsuWarped, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                val res = decodeMatZxing(otsuWarped)
                if (res != null) return parseJsonMetadata(res)
            } catch (_: Exception) {
            } finally {
                otsuWarped.release()
            }
        }

        // 4. Tier 4: OpenCV QRCodeDetector fallback on warped matrix
        if (secondaryMat != null && !secondaryMat.empty()) {
            try {
                val cvDecoded = qrDetector.detectAndDecode(secondaryMat)
                if (!cvDecoded.isNullOrEmpty()) {
                    val meta = parseJsonMetadata(cvDecoded)
                    if (meta != null) return meta
                }
            } catch (_: Exception) {}
        }

        // 5. Tier 5: OpenCV QRCodeDetector fallback on primary frame
        if (!primaryMat.empty()) {
            try {
                val cvDecoded = qrDetector.detectAndDecode(primaryMat)
                if (!cvDecoded.isNullOrEmpty()) {
                    val meta = parseJsonMetadata(cvDecoded)
                    if (meta != null) return meta
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun decodeMatZxing(mat: Mat): String? {
        if (mat.empty()) return null
        return try {
            val width = mat.cols()
            val height = mat.rows()
            val buffer = ByteArray(width * height)
            mat.get(0, 0, buffer)

            val source = PlanarYUVLuminanceSource(
                buffer, width, height, 0, 0, width, height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            synchronized(zxingReader) {
                try {
                    zxingReader.decodeWithState(bitmap).text
                } finally {
                    zxingReader.reset()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonMetadata(jsonString: String): SessionMetadata? {
        if (jsonString.isEmpty()) return null
        return try {
            Log.i("QR_PARSER", "Successfully decoded QR: $jsonString")
            val json = JSONObject(jsonString)

            val filename = when {
                json.has("filename") -> json.optString("filename", "unknown")
                json.has("f") -> json.optString("f", "unknown")
                else -> "unknown"
            }.trim()

            val totalPackets = when {
                json.has("total_packets") -> json.optInt("total_packets", 0)
                json.has("p") -> json.optInt("p", 0)
                else -> 0
            }

            val fileSize = when {
                json.has("file_size") -> json.optInt("file_size", 0)
                json.has("s") -> json.optInt("s", 0)
                else -> 0
            }

            val mimoMode = when {
                json.has("mimo_mode") -> json.optString("mimo_mode", "1x1")
                json.has("m") -> json.optString("m", "1x1")
                else -> "1x1"
            }.trim()

            val symbolMode = when {
                json.has("symbol_mode") -> json.optString("symbol_mode", "BINARY")
                json.has("y") -> json.optString("y", "BINARY")
                else -> "BINARY"
            }.trim()

            val packetSize = when {
                json.has("packet_size") -> json.optInt("packet_size", 64)
                json.has("z") -> json.optInt("z", 64)
                else -> 64
            }

            val sessionId = when {
                json.has("session_id") -> json.optString("session_id", "UNKNOWN")
                json.has("id") -> json.optString("id", "UNKNOWN")
                else -> "UNKNOWN"
            }.trim()

            val version = when {
                json.has("protocol_version") -> json.optInt("protocol_version", 1)
                json.has("v") -> json.optInt("v", 1)
                else -> 1
            }

            if (totalPackets <= 0 || filename == "unknown") {
                return null
            }

            SessionMetadata(
                protocol_version = version,
                filename = filename,
                file_size = fileSize,
                total_packets = totalPackets,
                mimo_mode = mimoMode,
                symbol_mode = symbolMode,
                packet_size = packetSize,
                session_id = sessionId
            )
        } catch (e: Exception) {
            Log.d("QR_PARSER", "Failed parsing metadata JSON: ${e.message}")
            null
        }
    }
}
