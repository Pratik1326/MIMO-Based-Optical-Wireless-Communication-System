package com.example.mimo_optical_receiver.protocol

object ProtocolConstants {
    val MAGIC_HEADER = byteArrayOf(0x4D, 0x4F) // "MO"
    const val PROTOCOL_VERSION: Byte = 0x01
    const val HEADER_SIZE_BYTES = 11
    const val CRC_SIZE_BYTES = 4
    const val OVERHEAD_BYTES = HEADER_SIZE_BYTES + CRC_SIZE_BYTES
    const val DEFAULT_FEC_PARITY = 32
}
