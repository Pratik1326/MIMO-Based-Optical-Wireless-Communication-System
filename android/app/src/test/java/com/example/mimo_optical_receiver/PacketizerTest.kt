package com.example.mimo_optical_receiver

import com.example.mimo_optical_receiver.protocol.SequenceManager
import com.example.mimo_optical_receiver.transmitter.PacketEncoder
import com.example.mimo_optical_receiver.transmitter.Packetizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketizerTest {

    @Test
    fun testEndToEnd_PacketizerToSequenceManager_Uncompressed() {
        val testData = "Optical MIMO Wireless Communication Packetizer Test Payload 1234567890".toByteArray(Charsets.UTF_8)
        val packetizer = Packetizer(payloadSize = 64, channelId = 0, sequenceStart = 0L, compress = false)
        val packets = packetizer.packetize(testData)

        assertTrue("Should produce at least 1 packet", packets.isNotEmpty())

        val sequenceManager = SequenceManager(totalExpectedPackets = packets.size, packetSize = 64)
        sequenceManager.processPackets(packets)

        assertTrue("SequenceManager should be complete", sequenceManager.isComplete())
        val reconstructed = sequenceManager.assembleFileBytes()
        assertArrayEquals("Reconstructed bytes must match original testData", testData, reconstructed)
    }

    @Test
    fun testEndToEnd_PacketizerToSequenceManager_CompressedZFIL() {
        val originalText = "HIGH_THROUGHPUT_OPTICAL_COMMUNICATION_APP_TO_APP_".repeat(30)
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val packetizer = Packetizer(payloadSize = 64, channelId = 0, sequenceStart = 0L, compress = true)
        val packets = packetizer.packetize(originalBytes)

        assertTrue("Should produce multiple packets", packets.size > 1)

        val sequenceManager = SequenceManager(totalExpectedPackets = packets.size, packetSize = 64)
        sequenceManager.processPackets(packets)

        assertTrue("SequenceManager should be complete", sequenceManager.isComplete())
        val reconstructed = sequenceManager.assembleFileBytes()
        assertArrayEquals("Decompressed bytes must match originalBytes exactly", originalBytes, reconstructed)
    }

    @Test
    fun testPacketEncodingAndDecodingRoundTrip() {
        val data = "Test payload string".toByteArray(Charsets.UTF_8)
        val packetizer = Packetizer(payloadSize = 64, channelId = 2, sequenceStart = 10L, compress = false)
        val packets = packetizer.packetize(data)

        val decoder = com.example.mimo_optical_receiver.protocol.PacketDecoder()
        for (packet in packets) {
            val encodedBytes = PacketEncoder.encode(packet)
            val decodedPacket = decoder.decode(encodedBytes)

            assertEquals(packet.frameType, decodedPacket.frameType)
            assertEquals(packet.channelId, decodedPacket.channelId)
            assertEquals(packet.sequenceId, decodedPacket.sequenceId)
            assertEquals(packet.version, decodedPacket.version)
            assertArrayEquals(packet.payload, decodedPacket.payload)
        }
    }
}
