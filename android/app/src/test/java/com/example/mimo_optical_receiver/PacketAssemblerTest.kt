package com.example.mimo_optical_receiver

import com.example.mimo_optical_receiver.protocol.FrameType
import com.example.mimo_optical_receiver.protocol.Packet
import com.example.mimo_optical_receiver.protocol.PacketAssembler
import com.example.mimo_optical_receiver.transmitter.PacketEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketAssemblerTest {

    private fun bytesToBits(bytes: ByteArray): ByteArray {
        val bits = ByteArray(bytes.size * 8)
        var bitIdx = 0
        for (b in bytes) {
            val byteVal = b.toInt() and 0xFF
            for (shift in 7 downTo 0) {
                bits[bitIdx++] = ((byteVal shr shift) and 1).toByte()
            }
        }
        return bits
    }

    @Test
    fun testCleanPacketDecode() {
        val payload = "Hello MIMO Optical Link!".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            frameType = FrameType.DATA,
            channelId = 0,
            sequenceId = 42L,
            payload = payload,
            version = 1
        )
        val encodedBytes = PacketEncoder.encode(packet)
        val bits = bytesToBits(encodedBytes)

        val assembler = PacketAssembler()
        val decoded = assembler.processChannelBits(listOf(bits), expectedPacketSize = payload.size)

        assertEquals(1, decoded.size)
        assertEquals(42L, decoded[0].sequenceId)
        assertEquals("Hello MIMO Optical Link!", String(decoded[0].payload, Charsets.UTF_8))
        assertTrue("Magic should be found", assembler.lastMagicFound)
        assertEquals(0, assembler.lastMagicBitOffset)
    }

    @Test
    fun testCorruptedMagicHeader_HammingDistance2Recovery() {
        // Reproduce Log 8 condition: Byte 0 was received as 0xDD instead of 0x4D (2 bits flipped)
        val payload = "Testing glare recovery on magic header byte 0".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            frameType = FrameType.DATA,
            channelId = 1,
            sequenceId = 100L,
            payload = payload,
            version = 1
        )
        val encodedBytes = PacketEncoder.encode(packet)
        // Corrupt Byte 0 from 0x4D to 0xDD
        encodedBytes[0] = 0xDD.toByte()

        val bits = bytesToBits(encodedBytes)

        val assembler = PacketAssembler()
        val decoded = assembler.processChannelBits(listOf(bits), expectedPacketSize = payload.size)

        assertEquals("Should recover packet using Hamming tolerance on magic word", 1, decoded.size)
        assertEquals(100L, decoded[0].sequenceId)
        assertEquals(String(payload, Charsets.UTF_8), String(decoded[0].payload, Charsets.UTF_8))
        assertTrue("Magic should be found via candidate reconstruction", assembler.lastMagicFound)
    }

    @Test
    fun testCorruptedPayload_CrcRejection() {
        // Ensure packets with genuine payload bit errors are securely rejected by CRC-32
        val payload = "Authentic secret data payload".toByteArray(Charsets.UTF_8)
        val packet = Packet(
            frameType = FrameType.DATA,
            channelId = 0,
            sequenceId = 5L,
            payload = payload,
            version = 1
        )
        val encodedBytes = PacketEncoder.encode(packet)
        // Corrupt a payload byte
        encodedBytes[15] = (encodedBytes[15].toInt() xor 0xFF).toByte()

        val bits = bytesToBits(encodedBytes)

        val assembler = PacketAssembler()
        val decoded = assembler.processChannelBits(listOf(bits), expectedPacketSize = payload.size)

        assertEquals("Corrupted payload must be rejected by CRC32", 0, decoded.size)
        assertEquals(1, assembler.crcFailedCount)
    }
}
