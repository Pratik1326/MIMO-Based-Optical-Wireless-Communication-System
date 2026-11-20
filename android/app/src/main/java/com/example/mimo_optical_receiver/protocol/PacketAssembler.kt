package com.example.mimo_optical_receiver.protocol

import android.util.Log
import com.example.mimo_optical_receiver.util.DiagnosticLogger

class PacketAssembler {

    private val packetDecoder = PacketDecoder()

    var crcFailedCount = 0
        private set

    var lastMagicFound = false
        private set

    var lastMagicBitOffset = -1
        private set

    /**
     * Decodes independent spatial channels per frame with zero inter-channel bit drift.
     * Uses Hamming-tolerant (<= 2 bit error) magic word sync detection and candidate reconstruction
     * with 100% CRC-32 integrity validation.
     */
    fun processChannelBits(channels: List<ByteArray>, expectedPacketSize: Int = 64): List<Packet> {
        val validPackets = mutableListOf<Packet>()
        val magic16 = 0x4D4F // 'M' (0x4D), 'O' (0x4F)
        var anyMagicFound = false
        var firstOffset = -1

        for ((chIdx, chBits) in channels.withIndex()) {
            if (chBits.size < (ProtocolConstants.HEADER_SIZE_BYTES * 8)) continue

            // Strict sync window: search up to 8 bits to catch sub-byte alignment jitter
            val maxScan = (chBits.size - (ProtocolConstants.HEADER_SIZE_BYTES * 8)).coerceAtMost(8)

            for (bitIdx in 0..maxScan) {
                if (bitIdx + 16 > chBits.size) break

                var window16 = 0
                for (k in 0 until 16) {
                    val bitVal = chBits.getOrNull(bitIdx + k)?.toInt() ?: 0
                    window16 = (window16 shl 1) or bitVal
                }

                // Compute Hamming distance against magic header 0x4D4F
                val hamming = Integer.bitCount(window16 xor magic16)

                // Allow up to 2 bit flips ONLY at exact cell alignment (bitIdx == 0); require exact match for shifted offsets
                val isPreambleMatch = if (bitIdx == 0) hamming <= 2 else hamming == 0

                if (isPreambleMatch) {
                    anyMagicFound = true
                    if (firstOffset == -1) firstOffset = bitIdx

                    // Extract 11-byte header candidate
                    val headerBytes = extractBytes(chBits, bitIdx, ProtocolConstants.HEADER_SIZE_BYTES)

                    // Strict header structural verification to discard noise false-positives
                    val version = headerBytes[2].toInt() and 0xFF
                    val frameType = headerBytes[3].toInt() and 0xFF
                    val channelId = headerBytes[4].toInt() and 0xFF
                    val b1 = headerBytes[9].toInt() and 0xFF
                    val b2 = headerBytes[10].toInt() and 0xFF
                    val payloadLength = (b1 shl 8) or b2

                    // Discard if structural fields violate protocol constraints
                    if (version != ProtocolConstants.PROTOCOL_VERSION.toInt() ||
                        frameType !in 1..4 ||
                        channelId !in 0..15 ||
                        payloadLength <= 0 || payloadLength > expectedPacketSize) {
                        continue
                    }

                    val totalPacketBytes = ProtocolConstants.HEADER_SIZE_BYTES + payloadLength + ProtocolConstants.CRC_SIZE_BYTES
                    val totalPacketBits = totalPacketBytes * 8

                    if (bitIdx + totalPacketBits > chBits.size) {
                        continue
                    }

                    val packetBytes = extractBytes(chBits, bitIdx, totalPacketBytes)
                    // If magic had minor bit flips (Hamming <= 2), restore pure 'M' 'O' for CRC verification
                    if (hamming > 0) {
                        packetBytes[0] = ProtocolConstants.MAGIC_HEADER[0]
                        packetBytes[1] = ProtocolConstants.MAGIC_HEADER[1]
                    }

                    try {
                        val packet = packetDecoder.decode(packetBytes)
                        validPackets.add(packet)
                        DiagnosticLogger.log("MIMO_DECODER", "SUCCESS: Decoded Ch=$chIdx, Seq=${packet.sequenceId}, Len=${packet.payload.size}, BitOffset=$bitIdx, Hamming=$hamming")
                        break // Clean packet decoded for this quadrant
                    } catch (e: Exception) {
                        crcFailedCount++
                        DiagnosticLogger.log("MIMO_DECODER", "CRC mismatch on Ch=$chIdx at bit $bitIdx (Hamming=$hamming, Len=$payloadLength): ${e.message}")
                    }
                }
            }
        }

        lastMagicFound = anyMagicFound
        lastMagicBitOffset = firstOffset
        return validPackets
    }

    private fun extractBytes(bits: ByteArray, bitOffset: Int, byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var b = 0
            val base = bitOffset + (i * 8)
            for (bit in 0..7) {
                val bitVal = bits.getOrNull(base + bit)?.toInt() ?: 0
                b = (b shl 1) or bitVal
            }
            result[i] = b.toByte()
        }
        return result
    }

    fun clear() {
        crcFailedCount = 0
        lastMagicFound = false
        lastMagicBitOffset = -1
    }
}
