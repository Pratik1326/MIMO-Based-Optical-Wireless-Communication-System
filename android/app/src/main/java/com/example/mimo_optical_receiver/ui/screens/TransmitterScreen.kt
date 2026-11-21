package com.example.mimo_optical_receiver.ui.screens

import android.app.Activity
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimo_optical_receiver.transmitter.TransmissionEngine
import com.example.mimo_optical_receiver.transmitter.TransmissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransmitterScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { TransmissionEngine(coroutineScope) }

    val stats by engine.stats.collectAsState()
    val currentBitmap by engine.currentBitmap.collectAsState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedMimoMode by remember { mutableStateOf("2x2") }
    var selectedFps by remember { mutableIntStateOf(15) }
    var selectedPacketSize by remember { mutableIntStateOf(128) }
    var autoStartCountdown by remember { mutableIntStateOf(3) }
    var enableAutoCountdown by remember { mutableStateOf(true) }

    // Stream countdown: auto-advances to live stream in 3s for zero-wait handoff
    LaunchedEffect(stats.state, enableAutoCountdown) {
        if (stats.state == TransmissionState.QR_HANDSHAKE && enableAutoCountdown) {
            autoStartCountdown = 3
            while (autoStartCountdown > 0 && enableAutoCountdown) {
                kotlinx.coroutines.delay(1000)
                if (enableAutoCountdown) {
                    autoStartCountdown--
                }
            }
            if (stats.state == TransmissionState.QR_HANDSHAKE && enableAutoCountdown) {
                engine.startStreaming()
            }
        }
    }

    // Keep screen on during active transmission
    DisposableEffect(stats.state) {
        val window = (context as? Activity)?.window
        if (stats.state == TransmissionState.TRANSMITTING || stats.state == TransmissionState.QR_HANDSHAKE) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Clean up engine when user navigates away from the Transmitter tab
    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
        }
    }

    // System File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                var fileName = "optical_file.bin"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    selectedFileName = fileName
                    selectedFileBytes = bytes
                    Toast.makeText(context, "Loaded: $fileName (${bytes.size} bytes)", Toast.LENGTH_SHORT).show()
                    // Auto-generate dynamic Phase 1 QR Handshake immediately on file selection
                    coroutineScope.launch {
                        engine.prepare(
                            data = bytes,
                            filename = fileName,
                            mimoMode = selectedMimoMode,
                            targetFps = selectedFps,
                            packetSize = selectedPacketSize
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MIMO Optical Transmitter", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = when (stats.state) {
                                TransmissionState.TRANSMITTING -> "Streaming: Frame ${stats.currentFrameIndex}/${stats.totalMimoFrames} (Cycle ${stats.carouselCycle}) • ${stats.targetFps} FPS"
                                TransmissionState.QR_HANDSHAKE -> "Phase 1: QR Handshake Ready"
                                TransmissionState.PAUSED -> "Transmission Paused"
                                else -> "Mobile-to-Mobile Optical Link"
                            },
                            fontSize = 12.sp,
                            color = when (stats.state) {
                                TransmissionState.TRANSMITTING -> Color(0xFF00E676)
                                TransmissionState.QR_HANDSHAKE -> Color(0xFF29B6F6)
                                TransmissionState.PAUSED -> Color(0xFFFFB300)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (stats.state != TransmissionState.IDLE) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                },
                actions = {
                    if (stats.state != TransmissionState.IDLE) {
                        IconButton(onClick = { engine.stop() }) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        val isTransmittingOrReady = stats.state != TransmissionState.IDLE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (!isTransmittingOrReady) {
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    } else {
                        Modifier
                            .padding(horizontal = 2.dp, vertical = 4.dp)
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (!isTransmittingOrReady) Arrangement.spacedBy(16.dp) else Arrangement.spacedBy(8.dp)
        ) {

            // 1. TRANSMISSION DISPLAY CANVAS (Square 1:1 Aspect Ratio, maximized)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(if (isTransmittingOrReady) 6.dp else 16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(if (isTransmittingOrReady) 6.dp else 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = currentBitmap
                if (bmp != null && !bmp.isRecycled) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                isFilterBitmap = false
                                isDither = false
                            }
                            val src = Rect(0, 0, bmp.width, bmp.height)
                            val dst = Rect(0, 0, size.width.toInt(), size.height.toInt())
                            canvas.nativeCanvas.drawBitmap(bmp, src, dst, paint)
                        }
                    }
                    // Pure optical transmission canvas: ZERO overlays or text inside to prevent optical corruption!
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Optical Transmitter Ready",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select a file or sample below to generate the optical MIMO stream.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // 2. TRANSMISSION CONTROLS & STATUS
            when (stats.state) {
                TransmissionState.IDLE -> {
                    // File Selection Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Payload File", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (selectedFileName != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${selectedFileBytes?.size ?: 0} B",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (selectedFileName != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(selectedFileName!!, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick File")
                                }

                                FilledTonalButton(
                                    onClick = {
                                        val sampleText = "Hello from Mobile-to-Mobile MIMO Optical Link! Timestamp: ${System.currentTimeMillis()}\n" +
                                                "MIMO Optical Wireless Communication System allows high-speed simplex data streaming between smartphones."
                                        val sampleBytes = sampleText.toByteArray(Charsets.UTF_8)
                                        selectedFileName = "demo_sample.txt"
                                        selectedFileBytes = sampleBytes
                                        Toast.makeText(context, "Loaded demo sample (140B)", Toast.LENGTH_SHORT).show()
                                        // Auto-generate dynamic Phase 1 QR Handshake immediately on demo sample load
                                        coroutineScope.launch {
                                            engine.prepare(
                                                data = sampleBytes,
                                                filename = "demo_sample.txt",
                                                mimoMode = selectedMimoMode,
                                                targetFps = selectedFps,
                                                packetSize = selectedPacketSize
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Demo Sample")
                                }
                            }
                        }
                    }

                    // MIMO Mode Selector
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MIMO Spatial Channels", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1x1", "2x2", "4x4").forEach { mode ->
                                FilterChip(
                                    selected = selectedMimoMode == mode,
                                    onClick = {
                                        selectedMimoMode = mode
                                        val bytes = selectedFileBytes
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            coroutineScope.launch {
                                                engine.prepare(
                                                    data = bytes,
                                                    filename = selectedFileName ?: "transmission.bin",
                                                    mimoMode = mode,
                                                    targetFps = selectedFps,
                                                    packetSize = selectedPacketSize
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(mode, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Transmission Speed (FPS) Selector
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Stream Frame Rate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 30).forEach { fps ->
                                FilterChip(
                                    selected = selectedFps == fps,
                                    onClick = {
                                        selectedFps = fps
                                        val bytes = selectedFileBytes
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            coroutineScope.launch {
                                                engine.prepare(
                                                    data = bytes,
                                                    filename = selectedFileName ?: "transmission.bin",
                                                    mimoMode = selectedMimoMode,
                                                    targetFps = fps,
                                                    packetSize = selectedPacketSize
                                                )
                                            }
                                        }
                                    },
                                    label = { Text("$fps FPS", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Packet Size (Payload) Selector
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Packet Payload Size", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(64 to "64 B", 128 to "128 B", 256 to "256 B").forEach { (size, label) ->
                                FilterChip(
                                    selected = selectedPacketSize == size,
                                    onClick = {
                                        selectedPacketSize = size
                                        val bytes = selectedFileBytes
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            coroutineScope.launch {
                                                engine.prepare(
                                                    data = bytes,
                                                    filename = selectedFileName ?: "transmission.bin",
                                                    mimoMode = selectedMimoMode,
                                                    targetFps = selectedFps,
                                                    packetSize = size
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(label, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Generate & Broadcast Button
                    Button(
                        onClick = {
                            val bytes = selectedFileBytes
                            val name = selectedFileName ?: "transmission.bin"
                            if (bytes != null && bytes.isNotEmpty()) {
                                coroutineScope.launch {
                                    engine.prepare(
                                        data = bytes,
                                        filename = name,
                                        mimoMode = selectedMimoMode,
                                        targetFps = selectedFps,
                                        packetSize = selectedPacketSize
                                    )
                                }
                            } else {
                                Toast.makeText(context, "Please select or generate a file first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PREPARE OPTICAL STREAM", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                TransmissionState.PREPARING -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Encoding packets and synthesizing ArUco frames...", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                TransmissionState.QR_HANDSHAKE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0288D1).copy(alpha = 0.15f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF0288D1))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Phase 1: QR Handshake Ready", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        if (enableAutoCountdown && autoStartCountdown > 0)
                                            "Point receiver phone at screen. Auto-starting stream in ${autoStartCountdown}s..."
                                        else
                                            "Point receiver phone at screen to sync. Tap START DATA STREAM when ready.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { engine.startStreaming() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (enableAutoCountdown && autoStartCountdown > 0) "START NOW (${autoStartCountdown}s)" else "START DATA STREAM",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 14.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    enableAutoCountdown = !enableAutoCountdown
                                    if (enableAutoCountdown) autoStartCountdown = 3
                                },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(if (enableAutoCountdown) Icons.Default.Pause else Icons.Default.AccessTime, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (enableAutoCountdown) "Pause (${autoStartCountdown}s)" else "Timer (3s)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // MIMO Mode Quick Switch during QR Handshake
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1x1", "2x2", "4x4").forEach { mode ->
                                FilterChip(
                                    selected = selectedMimoMode == mode,
                                    onClick = {
                                        selectedMimoMode = mode
                                        val bytes = selectedFileBytes
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            coroutineScope.launch {
                                                engine.prepare(
                                                    data = bytes,
                                                    filename = selectedFileName ?: "transmission.bin",
                                                    mimoMode = mode,
                                                    targetFps = selectedFps,
                                                    packetSize = selectedPacketSize
                                                )
                                            }
                                        }
                                    },
                                    label = { Text("MIMO $mode", fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                TransmissionState.TRANSMITTING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { engine.pause() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pause", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        FilledTonalButton(
                            onClick = { engine.showQr() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show QR", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = { engine.stop() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                TransmissionState.PAUSED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { engine.resume() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        FilledTonalButton(
                            onClick = { engine.showQr() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show QR", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = { engine.stop() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                TransmissionState.COMPLETED -> {
                    Button(
                        onClick = { engine.stop() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset Transmitter")
                    }
                }
            }
        }
    }
}
