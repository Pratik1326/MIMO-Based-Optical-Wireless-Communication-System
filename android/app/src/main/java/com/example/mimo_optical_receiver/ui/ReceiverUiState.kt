package com.example.mimo_optical_receiver.ui

data class ReceiverUiState(
    val cameraStatus: String = "INACTIVE",
    val resolution: String = "",
    val analyzerFps: Double = 0.0,
    
    val currentState: String = "WAITING_FOR_QR",
    
    // Handshake & Metadata
    val qrSessionId: String? = null,
    val qrFilename: String? = null,
    val qrTotalPackets: Int = 0,
    val qrPacketSize: Int = 64,
    val qrMimoMode: String = "1x1",
    val qrStatus: String = "Pending",
    
    // Optical & ArUco Telemetry
    val arucoMarkersDetected: Int = 0,
    val isPerspectiveLocked: Boolean = false,
    val syncConfidenceScore: Int = 0,
    
    // Reception Statistics
    val validPacketsReceived: Int = 0,
    val duplicatePackets: Int = 0,
    val crcFailedPackets: Int = 0,
    val missingPackets: Int = 0,
    
    // Performance
    val throughputKbs: Double = 0.0,
    val transferProgress: Float = 0f,
    
    // AR Overlay
    val screenBounds: List<Pair<Float, Float>> = emptyList(),
    
    // Optical Diagnostics
    val channelIntensities: List<Double> = emptyList(),

    // Occlusion & Obstruction Detection
    val isObstructed: Boolean = false,
    val obstructionReason: String? = null
)
