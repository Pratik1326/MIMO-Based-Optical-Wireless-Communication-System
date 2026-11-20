package com.example.mimo_optical_receiver.transmitter

import com.example.mimo_optical_receiver.protocol.FrameType
import com.example.mimo_optical_receiver.protocol.Packet
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater

/**
 * Chunks raw source bytes into optical packets conforming to the dual-platform protocol.
 * Prepend 44-byte FileMetadata ("FILE" or "ZFIL" magic + uint64 file size + 32-byte SHA-256)
 * to Packet #0 for automated, zero-loss receiver file reassembly and verification.
 */
class Packetizer(
    val payloadSize: Int = 64,
    val channelId: Int = 0,
    val sequenceStart: Long = 0L,
    val compress: Boolean = true
) {
    companion object {
        val FILE_MAGIC = "FILE".toByteArray(Charsets.US_ASCII)
        val ZFIL_MAGIC = "ZFIL".toByteArray(Charsets.US_ASCII)
        const val METADATA_HEADER_SIZE = 44 // 4B magic + 8B uint64 size + 32B sha256
    }

    init {
        require(payloadSize in 64..2048) { "payloadSize must be between 64 and 2048 bytes" }
        require(channelId in 0..255) { "channelId must fit in 1 byte" }
        require(sequenceStart >= 0) { "sequenceStart cannot be negative" }
    }

    fun packetize(data: ByteArray): List<Packet> {
        val originalSize = data.size.toLong()
        val digest = MessageDigest.getInstance("SHA-256").digest(data)

        var streamToChunk = data
        var magic = FILE_MAGIC

        if (compress && originalSize > 64) {
            val compressed = compressZlib(data)
            if (compressed.size < data.size) {
                streamToChunk = compressed
                magic = ZFIL_MAGIC
            }
        }

        // Build 44-byte FileMetadata header: 4B magic + 8B size + 32B sha256
        val metadataBuffer = ByteBuffer.allocate(METADATA_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        metadataBuffer.put(magic)
        metadataBuffer.putLong(originalSize)
        metadataBuffer.put(digest)
        val metadataBytes = metadataBuffer.array()

        val firstCapacity = payloadSize - METADATA_HEADER_SIZE
        val chunks = mutableListOf<ByteArray>()

        if (streamToChunk.isEmpty()) {
            chunks.add(metadataBytes)
        } else {
            val firstChunkData = if (streamToChunk.size <= firstCapacity) {
                streamToChunk
            } else {
                streamToChunk.copyOfRange(0, firstCapacity)
            }
            chunks.add(metadataBytes + firstChunkData)

            var offset = firstCapacity
            while (offset < streamToChunk.size) {
                val end = (offset + payloadSize).coerceAtMost(streamToChunk.size)
                chunks.add(streamToChunk.copyOfRange(offset, end))
                offset += payloadSize
            }
        }

        return chunks.mapIndexed { idx, chunkPayload ->
            Packet(
                frameType = FrameType.DATA,
                channelId = channelId,
                sequenceId = sequenceStart + idx,
                payload = chunkPayload,
                version = 1
            )
        }
    }

    private fun compressZlib(data: ByteArray): ByteArray {
        val deflater = Deflater(6)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size)
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            out.write(buf, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }
}
