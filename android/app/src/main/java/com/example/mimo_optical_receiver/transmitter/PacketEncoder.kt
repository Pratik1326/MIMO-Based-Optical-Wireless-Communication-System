package com.example.mimo_optical_receiver.transmitter

import com.example.mimo_optical_receiver.protocol.FrameType
import com.example.mimo_optical_receiver.protocol.Packet
import com.example.mimo_optical_receiver.protocol.ProtocolConstants
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object PacketEncoder {

    /**
     * Serializes a Packet object into a standardized binary optical frame.
     * 
     * Structure:
     * - Magic Header (2B): 0x4D, 0x4F ('M', 'O')
     * - Protocol Version (1B): 1
     * - Frame Type (1B): 1 (DATA)
     * - Channel ID (1B): Spatial quadrant index (0..15)
     * - Sequence ID (4B, UInt32 Big-Endian): Packet sequence index
     * - Payload Length (2B, UInt16 Big-Endian): Number of payload bytes
     * - Payload (NB): Raw payload bytes
     * - CRC-32 Checksum (4B, UInt32 Big-Endian): IEEE CRC-32 computed over all preceding bytes
     * 
     * Total overhead: 11 bytes header + 4 bytes CRC = 15 bytes.
     */
    fun encode(packet: Packet): ByteArray {
        val payload = packet.payload
        val payloadLen = payload.size
        require(payloadLen <= 0xFFFF) { "Payload size cannot exceed 65535 bytes" }
        require(packet.channelId in 0..0xFF) { "Channel ID must fit in 1 byte" }

        val totalSize = ProtocolConstants.HEADER_SIZE_BYTES + payloadLen + ProtocolConstants.CRC_SIZE_BYTES
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // 1. Magic Bytes (2B)
        buffer.put(ProtocolConstants.MAGIC_HEADER[0])
        buffer.put(ProtocolConstants.MAGIC_HEADER[1])

        // 2. Version (1B), Frame Type (1B), Channel ID (1B)
        buffer.put(packet.version.toByte())
        buffer.put(packet.frameType.value.toByte())
        buffer.put(packet.channelId.toByte())

        // 3. Sequence ID (4B)
        buffer.putInt(packet.sequenceId.toInt())

        // 4. Payload Length (2B)
        buffer.putShort(payloadLen.toShort())

        // 5. Payload Bytes
        buffer.put(payload)

        // 6. Compute IEEE CRC-32 over Header + Payload
        val bytesBeforeCrc = buffer.array()
        val crc32 = CRC32()
        crc32.update(bytesBeforeCrc, 0, ProtocolConstants.HEADER_SIZE_BYTES + payloadLen)
        val checksum = crc32.value

        // 7. Append CRC-32 (4B)
        buffer.putInt(checksum.toInt())

        return buffer.array()
    }
}
