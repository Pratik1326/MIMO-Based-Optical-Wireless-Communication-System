package com.example.mimo_optical_receiver.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.mimo_optical_receiver.camera.CameraManager
import com.example.mimo_optical_receiver.camera.FrameAnalyzer
import com.example.mimo_optical_receiver.camera.OpticalFeedbackController
import com.example.mimo_optical_receiver.profiler.DeviceProfiler
import com.example.mimo_optical_receiver.ui.ReceiverUiState
import com.example.mimo_optical_receiver.util.DiagnosticLogger
import com.example.mimo_optical_receiver.util.FileSaver
import com.example.mimo_optical_receiver.util.ReceiverSettingsManager
import com.example.mimo_optical_receiver.util.TransferHistoryManager
import com.example.mimo_optical_receiver.util.TransferRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val profile = remember { DeviceProfiler.getProfile(context) }
    val feedbackController = remember { OpticalFeedbackController() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    var uiState by remember { mutableStateOf(ReceiverUiState()) }
    var analyzerMode by remember { mutableStateOf(FrameAnalyzer.AnalyzerMode.SCANNING_QR) }
    var lastSavedRecord by remember { mutableStateOf<TransferRecord?>(null) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var lastValidCount by remember { mutableIntStateOf(0) }
    var receptionStartTime by remember { mutableLongStateOf(0L) }
    var lastPacketTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            uiState = uiState.copy(
                cameraStatus = "ACTIVE",
                resolution = "${profile.targetWidth}x${profile.targetHeight}"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            feedbackController.release()
        }
    }

    // Hands-free Auto-Start: Automatically transition from QR_SCANNING to DECODING_DATA
    LaunchedEffect(uiState.qrSessionId) {
        if (uiState.qrSessionId != null && analyzerMode == FrameAnalyzer.AnalyzerMode.SCANNING_QR) {
            DiagnosticLogger.log("ACTION", "Auto-transitioning to DECODING_DATA mode hands-free.")
            analyzerMode = FrameAnalyzer.AnalyzerMode.DECODING_DATA
            uiState = uiState.copy(currentState = "RECEIVING_DATA")
            feedbackController.sendReadySignal()
        }
    }

    // Modal BottomSheet for Developer Diagnostics
    if (showDiagnosticsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDiagnosticsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer Diagnostics", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showDiagnosticsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                DiagRow("Camera FPS", "%.1f".format(uiState.analyzerFps))
                DiagRow("Resolution", uiState.resolution)
                DiagRow("Optical Sync Score", "${uiState.syncConfidenceScore}%")
                DiagRow("ArUco Markers Detected", "${uiState.arucoMarkersDetected} / 4")
                DiagRow("Valid Packets", "${uiState.validPacketsReceived} / ${uiState.qrTotalPackets}")
                DiagRow("Duplicate Packets", "${uiState.duplicatePackets}")
                DiagRow("CRC Mismatches", "${uiState.crcFailedPackets}")
                DiagRow("Throughput", "%.1f KB/s".format(uiState.throughputKbs))

                if (uiState.channelIntensities.isNotEmpty()) {
                    Text("Channel Intensities:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        uiState.channelIntensities.forEachIndexed { idx, value ->
                            Text("CH$idx: ${"%.1f".format(value)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val path = DiagnosticLogger.exportToFile(context)
                            Toast.makeText(context, if (path != null) "Log saved:\n$path" else "Export failed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Log")
                    }

                    OutlinedButton(
                        onClick = {
                            DiagnosticLogger.clear()
                            Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Log")
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraManager(
                deviceProfile = profile,
                analyzerMode = analyzerMode,
                sessionTotalPackets = uiState.qrTotalPackets.takeIf { it > 0 },
                sessionPacketSize = uiState.qrPacketSize.takeIf { it > 0 },
                sessionMimoMode = uiState.qrMimoMode,
                onFpsUpdated = { fps ->
                    uiState = uiState.copy(analyzerFps = fps)
                },
                onQrScanned = { metadata ->
                    if (uiState.qrSessionId != metadata.session_id) {
                        DiagnosticLogger.log("QR_SCAN", "Scanned Session: ${metadata.session_id}, File: ${metadata.filename}")
                        uiState = uiState.copy(
                            qrSessionId = metadata.session_id,
                            qrFilename = metadata.filename,
                            qrTotalPackets = metadata.total_packets,
                            qrPacketSize = metadata.packet_size,
                            qrMimoMode = metadata.mimo_mode,
                            qrStatus = "Scanned",
                            currentState = "RECEIVING_DATA"
                        )
                        analyzerMode = FrameAnalyzer.AnalyzerMode.DECODING_DATA
                    }
                },
                onTransferComplete = { bytes ->
                    val fileName = uiState.qrFilename ?: "download.bin"
                    val savedPath = FileSaver.saveFile(context, fileName, bytes)
                    DiagnosticLogger.log("TRANSFER", "Transfer complete! File saved to: $savedPath")
                    
                    if (ReceiverSettingsManager.isAutoExportLogs(context)) {
                        DiagnosticLogger.exportToFile(context)
                    }

                    if (savedPath != null) {
                        val record = TransferRecord(
                            fileName = fileName,
                            fileSizeBytes = bytes.size.toLong(),
                            savedPath = savedPath,
                            mimoMode = uiState.qrMimoMode ?: "1x1",
                            sessionId = uiState.qrSessionId ?: "",
                            packetCount = uiState.qrTotalPackets
                        )
                        TransferHistoryManager.addRecord(context, record)
                        lastSavedRecord = record

                        // Optical Reverse ACK: 3 rapid strobe pulses back to the PC!
                        feedbackController.sendCompletionAck()

                        if (ReceiverSettingsManager.isAutoOpenFile(context)) {
                            TransferHistoryManager.openFile(context, record)
                        }
                    }

                    uiState = uiState.copy(
                        currentState = "TRANSFER_COMPLETE",
                        cameraStatus = "SAVED TO: ${savedPath ?: "ERROR"}"
                    )
                    analyzerMode = FrameAnalyzer.AnalyzerMode.COMPLETED
                },
                onStatusUpdated = { locked, statusText, confidence, validCount, duplicateCount, crcFailed, progress, bounds, intensities ->
                    val markers = if (locked) 4 else {
                        Regex("\\((\\d+)/4\\)").find(statusText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                    val isObstructed = statusText.contains("OBSTRUCTED") || statusText.startsWith("⚠️")
                    val obstructionReason = if (isObstructed) {
                        statusText.substringAfter("OBSTRUCTED:").trim().takeIf { it.isNotEmpty() }
                            ?: "Camera view blocked. Please remove object in front of screen."
                    } else null

                    val now = System.currentTimeMillis()
                    if (validCount > lastValidCount) {
                        if (receptionStartTime == 0L) {
                            receptionStartTime = now
                        }
                        lastPacketTimestamp = now
                        lastValidCount = validCount
                    }

                    val elapsedSec = (now - receptionStartTime) / 1000.0
                    val packetSize = uiState.qrPacketSize.takeIf { it > 0 } ?: 64
                    val throughput = if (validCount > 0 && elapsedSec > 0.5) {
                        ((validCount * packetSize) / 1024.0) / elapsedSec
                    } else 0.0

                    val isStalled = (uiState.currentState == "RECEIVING_DATA" || uiState.currentState == "STALLED") &&
                            validCount > 0 &&
                            validCount < uiState.qrTotalPackets &&
                            (now - lastPacketTimestamp > 3500L)

                    val nextState = when {
                        uiState.currentState == "TRANSFER_COMPLETE" -> "TRANSFER_COMPLETE"
                        uiState.currentState == "PAUSED" -> "PAUSED"
                        isStalled -> "STALLED"
                        analyzerMode == FrameAnalyzer.AnalyzerMode.DECODING_DATA || uiState.qrSessionId != null -> "RECEIVING_DATA"
                        locked -> "LOCKED"
                        else -> "WAITING_FOR_QR"
                    }

                    uiState = uiState.copy(
                        currentState = nextState,
                        isPerspectiveLocked = locked,
                        arucoMarkersDetected = markers,
                        syncConfidenceScore = confidence,
                        validPacketsReceived = validCount,
                        duplicatePackets = duplicateCount,
                        crcFailedPackets = crcFailed,
                        transferProgress = progress,
                        throughputKbs = throughput,
                        screenBounds = bounds,
                        channelIntensities = intensities,
                        isObstructed = isObstructed,
                        obstructionReason = obstructionReason
                    )
                },
                onCameraControlReady = { control ->
                    feedbackController.attachCameraControl(control)
                },
                onRetransmitRequested = {
                    feedbackController.sendRetransmitSignal()
                },
                modifier = Modifier.fillMaxSize()
            )

            // AR Holographic Screen Border Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (uiState.screenBounds.size == 4) {
                    val frameAspect = 480f / 640f
                    val scaledWidth = size.height * frameAspect
                    val offsetX = (size.width - scaledWidth) / 2f

                    val path = Path().apply {
                        val p0 = uiState.screenBounds[0]
                        moveTo(offsetX + p0.first * scaledWidth, p0.second * size.height)
                        for (i in 1..3) {
                            val p = uiState.screenBounds[i]
                            lineTo(offsetX + p.first * scaledWidth, p.second * size.height)
                        }
                        close()
                    }

                    val overlayColor = if (uiState.isObstructed) {
                        Color(0xFFEF4444).copy(alpha = 0.90f) // Warning Red when obstructed
                    } else {
                        Color(0xFF00E676).copy(alpha = 0.85f) // High-contrast green when clear
                    }

                    drawPath(
                        path = path,
                        color = overlayColor,
                        style = Stroke(width = 6f)
                    )
                }
            }

            // Minimalist Modern HUD Controls
            ModernScannerOverlay(
                uiState = uiState,
                analyzerMode = analyzerMode,
                onStart = {
                    DiagnosticLogger.log("ACTION", "START clicked. Switching to DECODING_DATA mode.")
                    analyzerMode = FrameAnalyzer.AnalyzerMode.DECODING_DATA
                    uiState = uiState.copy(currentState = if (uiState.validPacketsReceived > 0) "RECEIVING_DATA" else "WAITING_FOR_DATA")
                    // Optical Ready Signal: 1 flash pulse to PC
                    feedbackController.sendReadySignal()
                },
                onPause = {
                    analyzerMode = FrameAnalyzer.AnalyzerMode.SCANNING_QR
                    uiState = uiState.copy(currentState = "PAUSED")
                },
                onReset = {
                    DiagnosticLogger.clear()
                    analyzerMode = FrameAnalyzer.AnalyzerMode.SCANNING_QR
                    lastSavedRecord = null
                    lastValidCount = 0
                    receptionStartTime = 0L
                    lastPacketTimestamp = 0L
                    uiState = uiState.copy(
                        qrSessionId = null,
                        qrFilename = null,
                        qrTotalPackets = 0,
                        qrStatus = "Pending",
                        currentState = "WAITING_FOR_QR",
                        validPacketsReceived = 0,
                        throughputKbs = 0.0,
                        transferProgress = 0f
                    )
                },
                onViewFile = {
                    lastSavedRecord?.let { record ->
                        TransferHistoryManager.openFile(context, record)
                    }
                },
                onOpenDiagnostics = {
                    showDiagnosticsSheet = true
                }
            )

        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required for optical wireless reception.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun ModernScannerOverlay(
    uiState: ReceiverUiState,
    analyzerMode: FrameAnalyzer.AnalyzerMode,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onViewFile: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // TOP AREA: Floating Minimalist Status Pill + Diagnostics Icon + Obstruction Alert
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusDotColor = when {
                            uiState.isObstructed -> Color(0xFFEF4444)
                            uiState.currentState == "TRANSFER_COMPLETE" -> Color(0xFF00E676)
                            uiState.currentState == "RECEIVING_DATA" -> Color(0xFF00E676)
                            uiState.currentState == "STALLED" -> Color(0xFFFFB300)
                            uiState.qrSessionId != null -> Color(0xFF00E676)
                            uiState.isPerspectiveLocked -> Color(0xFFFFB300)
                            else -> Color.White.copy(alpha = 0.6f)
                        }

                        Surface(
                            color = statusDotColor,
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}

                        Spacer(modifier = Modifier.width(8.dp))

                        val statusText = when {
                            uiState.isObstructed -> "Obstruction Detected"
                            uiState.currentState == "TRANSFER_COMPLETE" -> "Transfer Complete"
                            uiState.currentState == "RECEIVING_DATA" -> "Receiving Data • ${uiState.qrMimoMode ?: "1x1"}"
                            uiState.currentState == "STALLED" -> "Stream Stalled • Re-align Camera"
                            uiState.qrSessionId != null -> "Receiving Data • ${uiState.qrMimoMode ?: "1x1"}"
                            uiState.isPerspectiveLocked -> "Screen Locked • Scanning QR..."
                            uiState.arucoMarkersDetected > 0 -> "Aligning ArUco (${uiState.arucoMarkersDetected}/4)"
                            else -> "Point Camera at Screen"
                        }

                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Minimalist Analytics / Diagnostics Icon
                IconButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.Analytics,
                        contentDescription = "Diagnostics",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Obstruction / Occlusion Alert Popup Banner
            AnimatedVisibility(
                visible = uiState.isObstructed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xD9B71C1C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Obstruction Warning",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.obstructionReason ?: "Camera view or screen obstructed. Please clear any blocking objects or hands.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // BOTTOM AREA: Floating Progress Card & Single Primary Action Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Floating Transfer Progress Card (shown when QR is scanned or receiving or complete)
            AnimatedVisibility(
                visible = uiState.qrSessionId != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.qrFilename ?: "Transfer File",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Surface(
                                color = Color(0xFF29B6F6).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = uiState.qrMimoMode ?: "1x1",
                                    color = Color(0xFF29B6F6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { uiState.transferProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00E676),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val pct = (uiState.transferProgress * 100).toInt()
                            Text(
                                text = "${uiState.validPacketsReceived} / ${uiState.qrTotalPackets} Packets ($pct%)",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                            if (uiState.currentState == "RECEIVING_DATA" || uiState.currentState == "STALLED") {
                                Text(
                                    text = "%.1f KB/s".format(uiState.throughputKbs),
                                    color = if (uiState.currentState == "STALLED") Color(0xFFFFB300) else Color(0xFF00E676),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // PRIMARY DYNAMIC ACTION BUTTON
            when {
                uiState.currentState == "TRANSFER_COMPLETE" -> {
                    Button(
                        onClick = onViewFile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VIEW RECEIVED FILE", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
                    }
                }
                uiState.currentState == "RECEIVING_DATA" -> {
                    if (uiState.validPacketsReceived == 0) {
                        // Handshake complete, awaiting stream frames: provide manual force start and rescan controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isActivelyDecoding = analyzerMode == FrameAnalyzer.AnalyzerMode.DECODING_DATA
                            Button(
                                onClick = onStart,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActivelyDecoding) Color(0xFF00B0FF) else Color(0xFF00E676)
                                )
                            ) {
                                Icon(
                                    if (isActivelyDecoding) Icons.Default.Sync else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isActivelyDecoding) "Scanning Stream..." else "Start Receiving",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onReset,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rescan QR")
                            }
                        }
                    } else {
                        // Actively receiving stream packets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPause,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }
                            Button(
                                onClick = onReset,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
                uiState.currentState == "STALLED" -> {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFB300))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Optical Stream Stalled", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("Waiting for next packet. Keep camera aligned with screen.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPause,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }

                            Button(
                                onClick = onStart,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-sync Stream", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { /* Wait for QR metadata to complete handshake */ },
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = if (uiState.isPerspectiveLocked) Color(0xFF0288D1).copy(alpha = 0.5f) else Color(0xFF263238),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Icon(
                            if (uiState.isPerspectiveLocked) Icons.Default.QrCodeScanner else Icons.Default.CropFree,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                uiState.isPerspectiveLocked -> "SCREEN LOCKED • SCANNING QR METADATA..."
                                uiState.arucoMarkersDetected > 0 -> "ALIGNING ARUCO (${uiState.arucoMarkersDetected}/4)"
                                else -> "POINT CAMERA AT TRANSMITTER SCREEN"
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
