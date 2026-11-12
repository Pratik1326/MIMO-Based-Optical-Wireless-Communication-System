package com.example.mimo_optical_receiver.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

object DiagnosticLogger {
    private const val MAX_LOG_ENTRIES = 500
    private val logBuffer = LinkedList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp][$tag] $message"
        Log.d(tag, message)
        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeFirst()
        }
    }

    @Synchronized
    fun logFrameDecode(
        frameIndex: Int,
        corners: List<Pair<Double, Double>>,
        minVal: Double,
        maxVal: Double,
        threshold: Double,
        bits: ByteArray,
        foundMagic: Boolean,
        magicBitOffset: Int,
        crcFailures: Int,
        validPackets: Int
    ) {
        val timestamp = dateFormat.format(Date())
        val first64Bits = bits.take(64).joinToString("") { if (it > 0) "1" else "0" }
        
        // Convert first 8 bytes to hex
        val hexBuilder = StringBuilder()
        for (byteIdx in 0 until minOf(8, bits.size / 8)) {
            var b = 0
            for (bit in 0 until 8) {
                val idx = byteIdx * 8 + bit
                val bitVal = if (idx < bits.size) bits[idx].toInt() and 1 else 0
                b = (b shl 1) or bitVal
            }
            hexBuilder.append(String.format("%02X ", b))
        }

        val entry = buildString {
            append("[$timestamp][FRAME #$frameIndex] ")
            append("Corners: ${corners.size} | ")
            append(String.format(Locale.US, "Range: [%.1f - %.1f], Thresh: %.1f | ", minVal, maxVal, threshold))
            append("First8BytesHex: [${hexBuilder.toString().trim()}] | ")
            append("MagicFound: $foundMagic (offset=$magicBitOffset) | ")
            append("Stats(Valid=$validPackets, CRC_Fail=$crcFailures)\n")
            append("   Bits[0..63]: $first64Bits")
        }

        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeFirst()
        }
    }

    @Synchronized
    fun getLogCount(): Int = logBuffer.size

    @Synchronized
    fun exportToFile(context: Context): String? {
        return try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, "MIMO_Receiver_Telemetry.log")
            PrintWriter(FileWriter(file, false)).use { writer ->
                writer.println("=================================================================")
                writer.println("        MIMO OPTICAL RECEIVER - LIVE TELEMETRY REPORT            ")
                writer.println("  Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                writer.println("  Total Log Entries: ${logBuffer.size}")
                writer.println("=================================================================")
                writer.println()
                for (line in logBuffer) {
                    writer.println(line)
                }
                writer.println()
                writer.println("========================== END OF LOG ==========================")
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun clear() {
        logBuffer.clear()
    }
}
