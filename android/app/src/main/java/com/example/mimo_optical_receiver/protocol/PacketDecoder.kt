package com.example.mimo_optical_receiver.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PacketDecoder {

    /**
     * Parses a raw byte array into a verified Packet.
     * Validates the structure, protocol version, and standard IEEE CRC-32 integrity.
     * 
     * @param bytes The raw frame byte array captured from the optical transmission.
     * @return Packet if successfully decoded and validated.
     * @throws IllegalArgumentException if the packet length or magic header is invalid.
     * @throws SecurityException if the CRC checksum fails.
     */
    fun decode(bytes: ByteArray): Packet {
        if (bytes.size < ProtocolConstants.OVERHEAD_BYTES) {
            throw IllegalArgumentException("Packet too short. Received ${bytes.size} bytes, min overhead is ${ProtocolConstants.OVERHEAD_BYTES}")
        }

        // Buffer wraps the byte array using BIG_ENDIAN standard
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // 1. Validate Magic Bytes (2 bytes)
        val magic1 = buffer.get()
        val magic2 = buffer.get()
        if (magic1 != ProtocolConstants.MAGIC_HEADER[0] || magic2 != ProtocolConstants.MAGIC_HEADER[1]) {
            throw IllegalArgumentException("Invalid magic header: [${magic1.toInt()}, ${magic2.toInt()}]")
        }

        // 2. Read Metadata (3 bytes)
        val version = buffer.get().toInt()
        val frameTypeInt = buffer.get().toInt()
        val frameType = FrameType.fromInt(frameTypeInt)
        val channelId = buffer.get().toInt()

        // 3. Read Sequence ID (4 bytes, unsigned 32-bit handled as Kotlin Long)
        val sequenceId = buffer.getInt().toUInt().toLong()

        // 4. Read Payload Length (2 bytes, unsigned 16-bit)
        val payloadLength = buffer.getShort().toUShort().toInt()

        // 5. Length Validation
        val expectedSize = ProtocolConstants.HEADER_SIZE_BYTES + payloadLength + ProtocolConstants.CRC_SIZE_BYTES
        if (bytes.size != expectedSize) {
            throw IllegalArgumentException("Size mismatch. Expected $expectedSize bytes, received ${bytes.size}")
        }

        // 6. Extract Payload
        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        // 7. Extract Received CRC (4 bytes, unsigned 32-bit)
        val receivedCrc = buffer.getInt().toUInt().toLong()

        // 8. Calculate Actual CRC over (Header + Payload)
        val crc32 = CRC32()
        crc32.update(bytes, 0, bytes.size - ProtocolConstants.CRC_SIZE_BYTES)
        val calculatedCrc = crc32.value // Returns a long

        // 9. Verify CRC Integrity
        if (calculatedCrc != receivedCrc) {
            throw SecurityException("CRC Mismatch! Expected: $calculatedCrc, Received: $receivedCrc")
        }

        // 10. Success! Return parsed packet.
        return Packet(
            frameType = frameType,
            channelId = channelId,
            sequenceId = sequenceId,
            payload = payload,
            version = version
        )
    }
}
