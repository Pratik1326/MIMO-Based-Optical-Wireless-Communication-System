package com.example.mimo_optical_receiver.protocol

enum class FrameType(val value: Int) {
    IDLE(0),
    CALIB(1),
    SYNC(2),
    CONFIG(3),
    DATA(4),
    END(5);

    companion object {
        fun fromInt(value: Int): FrameType {
            return values().find { it.value == value } ?: IDLE
        }
    }
}

data class Packet(
    val frameType: FrameType,
    val channelId: Int,
    val sequenceId: Long,
    val payload: ByteArray,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Packet

        if (frameType != other.frameType) return false
        if (channelId != other.channelId) return false
        if (sequenceId != other.sequenceId) return false
        if (!payload.contentEquals(other.payload)) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frameType.hashCode()
        result = 31 * result + channelId
        result = 31 * result + sequenceId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + version
        return result
    }
}
