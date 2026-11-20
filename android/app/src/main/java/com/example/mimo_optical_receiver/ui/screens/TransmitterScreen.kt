package com.example.mimo_optical_receiver.ui.screens

import android.app.Activity
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
    var selectedPacketSize by remember { mutableIntStateOf(64) }
    var autoStartCountdown by remember { mutableIntStateOf(4) }

    // Automatic stream countdown: gives the receiver 4 seconds to scan QR metadata before auto-starting optical stream hands-free
    LaunchedEffect(stats.state) {
        if (stats.state == TransmissionState.QR_HANDSHAKE) {
            autoStartCountdown = 4
            while (autoStartCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                autoStartCountdown--
            }
            if (stats.state == TransmissionState.QR_HANDSHAKE) {
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
            if (stats.state == TransmissionState.TRANSMITTING) {
                engine.stop()
            }
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
                        Text("Mobile-to-Mobile Optical Link", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. TRANSMISSION DISPLAY CANVAS (Square 1:1 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = currentBitmap
                if (bmp != null && !bmp.isRecycled) {
                    val frameIndexTrigger = stats.currentFrameIndex
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Subscribe directly to frameIndexTrigger so Compose invalidates and redraws on every tick
                        val currentTick = frameIndexTrigger
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                isFilterBitmap = false // Nearest-neighbor sharp pixels, zero blur
                                isDither = false
                            }
                            val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
                            val dstRect = android.graphics.Rect(0, 0, size.width.toInt(), size.height.toInt())
                            canvas.nativeCanvas.drawBitmap(bmp, srcRect, dstRect, paint)
                        }
                    }

                    // Minimalist top overlay badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = when (stats.state) {
                                TransmissionState.QR_HANDSHAKE -> "PHASE 1: QR HANDSHAKE"
                                TransmissionState.TRANSMITTING -> "STREAMING: ${stats.currentFrameIndex}/${stats.totalMimoFrames} (CYCLE ${stats.carouselCycle})"
                                TransmissionState.PAUSED -> "PAUSED"
                                else -> ""
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
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
                                        selectedFileName = "demo_sample.txt"
                                        selectedFileBytes = sampleText.toByteArray(Charsets.UTF_8)
                                        Toast.makeText(context, "Loaded demo sample (140B)", Toast.LENGTH_SHORT).show()
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
                                    onClick = { selectedMimoMode = mode },
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
                                    onClick = { selectedFps = fps },
                                    label = { Text("$fps FPS", fontWeight = FontWeight.Bold) },
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
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        if (autoStartCountdown > 0)
                                            "Point receiver phone at screen. Auto-starting optical stream in ${autoStartCountdown}s..."
                                        else
                                            "Point receiving phone at screen to synchronize session metadata.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { engine.startStreaming() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (autoStartCountdown > 0) "START OPTICAL DATA STREAM (${autoStartCountdown}s)" else "START OPTICAL DATA STREAM",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                TransmissionState.TRANSMITTING -> {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Live Stats Bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Frame", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${stats.currentFrameIndex} / ${stats.totalMimoFrames}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column {
                                    Text("Packets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${stats.totalPackets}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column {
                                    Text("Carousel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Cycle ${stats.carouselCycle}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Column {
                                    Text("Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${stats.targetFps} FPS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { engine.pause() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }

                            FilledTonalButton(
                                onClick = { engine.showQr() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show QR")
                            }

                            Button(
                                onClick = { engine.stop() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }
                }

                TransmissionState.PAUSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { engine.resume() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { engine.stop() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.Bold)
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
