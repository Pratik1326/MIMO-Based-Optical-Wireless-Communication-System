package com.example.mimo_optical_receiver

import com.example.mimo_optical_receiver.protocol.FrameType
import com.example.mimo_optical_receiver.protocol.Packet
import com.example.mimo_optical_receiver.protocol.SequenceManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater

class SequenceManagerTest {

    @Test
    fun testAssembleFileBytes_uncompressed() {
        val seqManager = SequenceManager(totalExpectedPackets = 2)
        val testData = "Hello Uncompressed World!".toByteArray(Charsets.UTF_8)

        // Build 44-byte metadata header: "FILE" + 8-byte size + 32-byte zero hash
        val header = ByteBuffer.allocate(44)
        header.put("FILE".toByteArray(Charsets.US_ASCII))
        header.putLong(testData.size.toLong())
        header.put(ByteArray(32))

        val packet0Payload = header.array() + testData.take(10).toByteArray()
        val packet1Payload = testData.drop(10).toByteArray()

        seqManager.processPackets(listOf(
            Packet(FrameType.DATA, channelId = 0, sequenceId = 0, payload = packet0Payload, version = 1),
            Packet(FrameType.DATA, channelId = 0, sequenceId = 1, payload = packet1Payload, version = 1)
        ))

        assertTrue(seqManager.isComplete())
        val result = seqManager.assembleFileBytes()
        assertArrayEquals(testData, result)
    }

    @Test
    fun testAssembleFileBytes_compressedZFIL() {
        val seqManager = SequenceManager(totalExpectedPackets = 2)
        val originalText = "HIGH_THROUGHPUT_OPTICAL_COMMUNICATION_".repeat(20)
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        // Compress original bytes using Deflater
        val deflater = Deflater(6)
        deflater.setInput(originalBytes)
        deflater.finish()
        val compressedStream = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            compressedStream.write(buf, 0, count)
        }
        deflater.end()
        val compressedBytes = compressedStream.toByteArray()

        // Build 44-byte metadata header: "ZFIL" + 8-byte size + 32-byte zero hash
        val header = ByteBuffer.allocate(44)
        header.put("ZFIL".toByteArray(Charsets.US_ASCII))
        header.putLong(originalBytes.size.toLong())
        header.put(ByteArray(32))

        val half = compressedBytes.size / 2
        val packet0Payload = header.array() + compressedBytes.take(half).toByteArray()
        val packet1Payload = compressedBytes.drop(half).toByteArray()

        seqManager.processPackets(listOf(
            Packet(FrameType.DATA, channelId = 0, sequenceId = 0, payload = packet0Payload, version = 1),
            Packet(FrameType.DATA, channelId = 0, sequenceId = 1, payload = packet1Payload, version = 1)
        ))

        assertTrue(seqManager.isComplete())
        val reconstructed = seqManager.assembleFileBytes()
        assertArrayEquals(originalBytes, reconstructed)
    }
}
