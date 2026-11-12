package com.example.mimo_optical_receiver.protocol

data class SessionMetadata(
    val protocol_version: Int,
    val filename: String,
    val file_size: Int,
    val total_packets: Int,
    val mimo_mode: String,
    val symbol_mode: String,
    val packet_size: Int,
    val session_id: String
)
