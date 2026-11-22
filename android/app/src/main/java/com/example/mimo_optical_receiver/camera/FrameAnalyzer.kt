package com.example.mimo_optical_receiver.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.mimo_optical_receiver.protocol.PacketAssembler
import com.example.mimo_optical_receiver.protocol.SequenceManager
import com.example.mimo_optical_receiver.protocol.SessionMetadata
import com.example.mimo_optical_receiver.util.DiagnosticLogger
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

class FrameAnalyzer(
    private val onFpsUpdated: (Double) -> Unit,
    private val onQrScanned: (SessionMetadata) -> Unit,
    private val onTransferComplete: (ByteArray) -> Unit,
    private val onStatusUpdated: (Boolean, String, Int, Int, Int, Int, Float, List<Pair<Float, Float>>, List<Double>) -> Unit,
    private val onRetransmitRequested: (() -> Unit)? = null
) : ImageAnalysis.Analyzer {

    enum class AnalyzerMode { SCANNING_QR, DECODING_DATA, COMPLETED }
    
    var currentMode = AnalyzerMode.SCANNING_QR
        private set

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var lastUniquePacketFrame = 0
    private var lastNackTimestamp = 0L
    private var qrLockedFrames = 0
    
    // Decoupled sub-systems
    private val qrMetadataParser = QrMetadataParser()
    private val screenDetector = ScreenDetector()
    private val perspectiveCorrector = PerspectiveCorrector()
    private val regionDetector = RegionDetector()
    private val syncDetector = SyncDetector()
    private val symbolDecoder = SymbolDecoder()
    private val packetAssembler = PacketAssembler()
    
    // Dynamically initialized when QR is scanned
    private var sequenceManager: SequenceManager? = null
    private var activePacketSize = 64
    private var activeMimoMode = "1x1"

    private var yByteArray: ByteArray? = null

    fun setMode(mode: AnalyzerMode) {
        currentMode = mode
    }

    fun initializeSession(totalPackets: Int, packetSize: Int = 64, mimoMode: String = "1x1") {
        sequenceManager = SequenceManager(totalPackets, packetSize)
        activePacketSize = packetSize
        activeMimoMode = mimoMode
    }

    fun resetSession() {
        sequenceManager = null
        activePacketSize = 64
        activeMimoMode = "1x1"
        qrLockedFrames = 0
        frameCount = 0
        lastUniquePacketFrame = 0
        currentMode = AnalyzerMode.SCANNING_QR
        syncDetector.release()
        screenDetector.resetTracking()
    }

    override fun analyze(image: ImageProxy) {
        if (currentMode == AnalyzerMode.COMPLETED) {
            image.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        frameCount++

        if (currentTime - lastFpsTimestamp >= 1000) {
            val fps = frameCount.toDouble() * 1000.0 / (currentTime - lastFpsTimestamp)
            onFpsUpdated(fps)
            frameCount = 0
            lastFpsTimestamp = currentTime
        }

        var grayMat: Mat? = null
        var warpedMat: Mat? = null
        var channelMats: List<Mat> = emptyList()
        var currentIntensities: List<Double> = emptyList()

        try {
            grayMat = extractGrayscale(image)

            val rotation = image.imageInfo.rotationDegrees
            if (rotation != 0) {
                val rotatedMat = Mat()
                val rotateCode = when (rotation) {
                    90 -> Core.ROTATE_90_CLOCKWISE
                    180 -> Core.ROTATE_180
                    270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                    else -> -1
                }
                if (rotateCode != -1) {
                    Core.rotate(grayMat, rotatedMat, rotateCode)
                    grayMat?.release()
                    grayMat = rotatedMat
                }
            }

            // 1. ALWAYS Detect ArUco Markers & Screen Bounds (IDs 0, 1, 2, 3)
            val detectionResult = screenDetector.detect(grayMat)
            val hasValidScreen = detectionResult.screenCorners.size == 4
            val normalizedBounds = if (hasValidScreen) {
                val frameWidth = grayMat!!.cols().toFloat()
                val frameHeight = grayMat!!.rows().toFloat()
                detectionResult.screenCorners.map { Pair((it.x / frameWidth).toFloat(), (it.y / frameHeight).toFloat()) }
            } else emptyList()

            // 2. ALWAYS Attempt Perspective Warp if 4 corners found (works with 2, 3, or 4 markers + geometry)
            if (hasValidScreen) {
                warpedMat = perspectiveCorrector.correct(grayMat, detectionResult.screenCorners)
            }

            // 3. State Machine Routing
            if (currentMode == AnalyzerMode.SCANNING_QR) {
                val metadata = qrMetadataParser.detectAndParse(
                    primaryMat = grayMat ?: warpedMat!!,
                    secondaryMat = warpedMat
                )
                if (metadata != null) {
                    activePacketSize = metadata.packet_size
                    activeMimoMode = metadata.mimo_mode
                    initializeSession(
                        totalPackets = metadata.total_packets,
                        packetSize = metadata.packet_size,
                        mimoMode = metadata.mimo_mode
                    )
                    DiagnosticLogger.log("ACTION", "QR Handshake verified (${metadata.filename}, ${metadata.total_packets} pkts, ${metadata.mimo_mode}). Awaiting live optical stream.")
                    onQrScanned(metadata)
                }
                
                val isLocked = warpedMat != null
                if (isLocked && sequenceManager != null) {
                    qrLockedFrames++
                    // Check if transmitter has flipped from Phase 1 QR to Phase 2 live data stream
                    val channels = regionDetector.extractChannels(warpedMat, activeMimoMode)
                    val decodeResult = symbolDecoder.decode(channels, activePacketSize)
                    val earlyPackets = packetAssembler.processChannelBits(decodeResult.channelBits, activePacketSize)
                    if (earlyPackets.isNotEmpty() || detectionResult.markerCount >= 2 || packetAssembler.lastMagicFound) {
                        currentMode = AnalyzerMode.DECODING_DATA
                        DiagnosticLogger.log("ACTION", "Live optical data stream detected (markers=${detectionResult.markerCount}, earlyPackets=${earlyPackets.size}). Transitioning to DECODING_DATA mode.")
                        if (earlyPackets.isNotEmpty()) {
                            sequenceManager?.processPackets(earlyPackets)
                        }
                    }
                } else if (!isLocked) {
                    qrLockedFrames = 0
                }

                val statusMsg = when {
                    sequenceManager != null && isLocked -> "HANDSHAKE SYNCED • Waiting for stream to start..."
                    sequenceManager != null && !isLocked -> "HANDSHAKE SYNCED! Align camera with screen"
                    detectionResult.isObstructed -> "⚠️ VIEW OBSTRUCTED: ${detectionResult.obstructionReason ?: "Clear camera path"}"
                    isLocked -> "SCREEN LOCKED • SCANNING QR..."
                    detectionResult.markerCount in 1..3 -> "ALIGNING: (${detectionResult.markerCount}/4 markers found)"
                    else -> "POINT CAMERA AT SCREEN"
                }
                
                onStatusUpdated(
                    isLocked,
                    statusMsg,
                    0, 0, 0, 0, 0f, normalizedBounds, emptyList()
                )
            } 
            else if (currentMode == AnalyzerMode.DECODING_DATA && warpedMat != null) {
                // Self-healing: if session was not initialized yet, attempt immediate QR parse
                if (sequenceManager == null) {
                    val metadata = qrMetadataParser.detectAndParse(
                        primaryMat = grayMat ?: warpedMat!!,
                        secondaryMat = warpedMat
                    )
                    if (metadata != null) {
                        activePacketSize = metadata.packet_size
                        activeMimoMode = metadata.mimo_mode
                        initializeSession(
                            totalPackets = metadata.total_packets,
                            packetSize = metadata.packet_size,
                            mimoMode = metadata.mimo_mode
                        )
                        onQrScanned(metadata)
                    }
                }

                val (isNew, confidence) = syncDetector.isNewFrame(warpedMat!!)
                val needsMorePackets = sequenceManager?.isComplete() == false

                // DECODE: Continuously decode while screen is locked and confidence >= 10 (or fresh frame)
                if (needsMorePackets && (confidence >= 10 || isNew)) {
                    channelMats = regionDetector.extractChannels(warpedMat!!, activeMimoMode)
                    val decodeResult = symbolDecoder.decode(channelMats, activePacketSize)
                    currentIntensities = decodeResult.intensities

                    // Decode each quadrant channel independently without serial concatenating or bit-slip
                    val validPackets = packetAssembler.processChannelBits(decodeResult.channelBits, activePacketSize)

                    DiagnosticLogger.logFrameDecode(
                        frameIndex = frameCount,
                        corners = detectionResult.screenCorners.map { Pair(it.x, it.y) },
                        minVal = symbolDecoder.lastMin,
                        maxVal = symbolDecoder.lastMax,
                        threshold = symbolDecoder.lastThreshold,
                        bits = decodeResult.flatBits,
                        foundMagic = packetAssembler.lastMagicFound,
                        magicBitOffset = packetAssembler.lastMagicBitOffset,
                        crcFailures = packetAssembler.crcFailedCount,
                        validPackets = sequenceManager?.uniqueCount ?: 0
                    )

                    if (validPackets.isNotEmpty()) {
                        val prevCount = sequenceManager?.uniqueCount ?: 0
                        sequenceManager?.processPackets(validPackets)
                        val newCount = sequenceManager?.uniqueCount ?: 0
                        if (newCount > prevCount) {
                            lastUniquePacketFrame = frameCount
                        }
                        if (sequenceManager?.isComplete() == true) {
                            currentMode = AnalyzerMode.COMPLETED
                            onTransferComplete(sequenceManager!!.assembleFileBytes())
                        }
                    }

                    // ARQ NACK Retransmit: if locked and expecting packets, but stalled for 40 frames (~1.5-2.5s)
                    if (needsMorePackets && (frameCount - lastUniquePacketFrame) >= 40 && (sequenceManager?.uniqueCount ?: 0) > 0) {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastNackTimestamp >= 2500L) {
                            lastNackTimestamp = nowMs
                            lastUniquePacketFrame = frameCount
                            onRetransmitRequested?.invoke()
                        }
                    }
                }
                val missingInfo = sequenceManager?.getMissingSummary() ?: ""
                val missingText = if (missingInfo.isNotEmpty()) " (Need: $missingInfo)" else ""
                val syncLabel = if (isNew) "NEW" else "STEADY"
                onStatusUpdated(
                    true, 
                    "LINK LOCKED | SYNC: $syncLabel$missingText", 
                    confidence, 
                    sequenceManager?.uniqueCount ?: 0, 
                    sequenceManager?.duplicateCount ?: 0, 
                    packetAssembler.crcFailedCount,
                    sequenceManager?.getProgress() ?: 0f, 
                    normalizedBounds, 
                    currentIntensities
                )
            } 
            else {
                val statusMsg = if (detectionResult.isObstructed) {
                    "⚠️ VIEW OBSTRUCTED: ${detectionResult.obstructionReason ?: "Clear camera path"}"
                } else {
                    "Searching... (${detectionResult.markerCount}/4)"
                }
                onStatusUpdated(
                    false, 
                    statusMsg, 
                    0, 
                    sequenceManager?.uniqueCount ?: 0, 
                    sequenceManager?.duplicateCount ?: 0, 
                    packetAssembler.crcFailedCount,
                    sequenceManager?.getProgress() ?: 0f, 
                    normalizedBounds, 
                    emptyList()
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            onStatusUpdated(
                false, 
                "Processing Error", 
                0,
                sequenceManager?.uniqueCount ?: 0,
                sequenceManager?.duplicateCount ?: 0,
                packetAssembler.crcFailedCount,
                sequenceManager?.getProgress() ?: 0f,
                emptyList(),
                emptyList()
            )
        } finally {
            grayMat?.release()
            warpedMat?.release()
            channelMats.forEach { it.release() }
            image.close()
        }
    }

    private fun extractGrayscale(image: ImageProxy): Mat {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        if (yByteArray == null || yByteArray!!.size != yBuffer.capacity()) {
            yByteArray = ByteArray(yBuffer.capacity())
        }
        yBuffer.get(yByteArray!!)

        val mat = Mat(height, yRowStride, CvType.CV_8UC1)
        mat.put(0, 0, yByteArray)

        return if (yRowStride > width) {
            val cropped = mat.submat(0, height, 0, width)
            val result = cropped.clone()
            mat.release()
            cropped.release()
            result
        } else {
            mat
        }
    }
}

