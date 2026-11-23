package com.example.mimo_optical_receiver.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

class SequenceManager(
    val totalExpectedPackets: Int,
    val packetSize: Int = 64
) {
    
    val receivedPackets = mutableMapOf<Long, Packet>()
    
    var uniqueCount = 0
        private set
        
    var duplicateCount = 0
        private set

    private val FILE_MAGIC = "FILE".toByteArray(Charsets.US_ASCII)
    private val ZFIL_MAGIC = "ZFIL".toByteArray(Charsets.US_ASCII)

    /**
     * Processes incoming packets, dropping duplicates and storing unique ones.
     */
    fun processPackets(packets: List<Packet>) {
        for (packet in packets) {
            if (receivedPackets.containsKey(packet.sequenceId)) {
                duplicateCount++
            } else {
                receivedPackets[packet.sequenceId] = packet
                uniqueCount++
            }
        }
    }

    /**
     * Returns the completion progress as a float between 0.0 and 1.0.
     */
    fun getProgress(): Float {
        if (totalExpectedPackets <= 0) return 0f
        return (uniqueCount.toFloat() / totalExpectedPackets.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Checks if all expected packets have been received.
     */
    fun isComplete(): Boolean {
        return uniqueCount >= totalExpectedPackets
    }

    fun getMissingSequenceIds(): List<Long> {
        val missing = mutableListOf<Long>()
        for (i in 0 until totalExpectedPackets) {
            val seq = i.toLong()
            if (!receivedPackets.containsKey(seq)) {
                missing.add(seq)
            }
        }
        return missing
    }

    fun getMissingSummary(): String {
        val missing = getMissingSequenceIds()
        if (missing.isEmpty()) return ""
        val top = missing.take(3).map { "#$it" }.joinToString(", ")
        return if (missing.size > 3) "$top (+${missing.size - 3})" else top
    }

    /**
     * Reconstructs the complete file payload.
     * Verifies completion, sorts by sequenceId, parses & strips the 44-byte FileMetadata
     * header from Packet #0, decompresses if ZFIL stream, extracts expected file size,
     * and truncates to the exact original file size.
     */
    fun assembleFileBytes(): ByteArray {
        if (!isComplete()) {
            throw IllegalStateException("Cannot assemble file: Not all packets received.")
        }

        val outputStream = ByteArrayOutputStream()
        val sortedKeys = receivedPackets.keys.sorted()
        var expectedFileSize = -1L
        var isCompressed = false
        
        for ((index, key) in sortedKeys.withIndex()) {
            val packet = receivedPackets[key]
            if (packet != null) {
                var payload = packet.payload
                // PC Packetizer prepends 44-byte FileMetadata ("FILE" or "ZFIL" magic + uint64 file_size + 32B sha256) to Packet #0
                if (index == 0 && payload.size >= 44) {
                    val magic = payload.take(4).toByteArray()
                    if (magic.contentEquals(FILE_MAGIC)) {
                        val sizeBuffer = ByteBuffer.wrap(payload, 4, 8)
                        expectedFileSize = sizeBuffer.long
                        payload = payload.copyOfRange(44, payload.size)
                    } else if (magic.contentEquals(ZFIL_MAGIC)) {
                        isCompressed = true
                        val sizeBuffer = ByteBuffer.wrap(payload, 4, 8)
                        expectedFileSize = sizeBuffer.long
                        payload = payload.copyOfRange(44, payload.size)
                    }
                }
                outputStream.write(payload)
            }
        }
        
        val fullBytes = outputStream.toByteArray()
        val resultBytes = if (isCompressed) {
            decompressZlib(fullBytes)
        } else {
            fullBytes
        }

        return if (expectedFileSize > 0 && resultBytes.size > expectedFileSize) {
            resultBytes.copyOf(expectedFileSize.toInt())
        } else {
            resultBytes
        }
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return data
        } finally {
            inflater.end()
        }
        val decompressed = outputStream.toByteArray()
        return if (decompressed.isNotEmpty()) decompressed else data
    }
}
