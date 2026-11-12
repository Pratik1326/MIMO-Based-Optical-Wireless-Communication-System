package com.example.mimo_optical_receiver.ui.screens

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimo_optical_receiver.profiler.DeviceProfiler
import com.example.mimo_optical_receiver.util.DiagnosticLogger
import com.example.mimo_optical_receiver.util.ReceiverSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val profile = remember { DeviceProfiler.getProfile(context) }

    var mimoMode by remember { mutableStateOf(ReceiverSettingsManager.getMimoMode(context)) }
    var syncConfidence by remember { mutableStateOf(ReceiverSettingsManager.getSyncConfidence(context).toFloat()) }
    var coreInset by remember { mutableStateOf(ReceiverSettingsManager.getCoreInset(context).toFloat()) }
    var autoOpenFile by remember { mutableStateOf(ReceiverSettingsManager.isAutoOpenFile(context)) }
    var autoExportLogs by remember { mutableStateOf(ReceiverSettingsManager.isAutoExportLogs(context)) }
    var verboseHud by remember { mutableStateOf(ReceiverSettingsManager.isVerboseHud(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Receiver Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Optical & Storage Configuration", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Group 1: Optical Decoding Engine
            item {
                SettingsCategoryCard(title = "Optical Decoding Engine", icon = Icons.Default.Visibility) {
                    // MIMO Mode Preference
                    Text("MIMO Layout Preference", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Override layout detection or adhere to QR code handshake.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("AUTO", "1x1", "2x2", "4x4").forEach { mode ->
                            FilterChip(
                                selected = mimoMode == mode,
                                onClick = {
                                    mimoMode = mode
                                    ReceiverSettingsManager.setMimoMode(context, mode)
                                },
                                label = { Text(mode, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Sync Confidence Cutoff Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Link Lock Cutoff", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Minimum confidence to trigger decoding", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${syncConfidence.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = syncConfidence,
                        onValueChange = {
                            syncConfidence = it
                            ReceiverSettingsManager.setSyncConfidence(context, it.toInt())
                        },
                        valueRange = 50f..95f,
                        steps = 8
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    // Center-Cell Inset Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cell Core Inset (Margin)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Percentage of border cropped per cell", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${coreInset.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = coreInset,
                        onValueChange = {
                            coreInset = it
                            ReceiverSettingsManager.setCoreInset(context, it.toInt())
                        },
                        valueRange = 15f..35f,
                        steps = 3
                    )
                }
            }

            // Group 2: Storage & Automation
            item {
                SettingsCategoryCard(title = "Storage & Automation", icon = Icons.Default.Folder) {
                    val downloadsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "Unavailable"

                    Text("Save Directory", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = downloadsPath,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-open toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Open Files", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically launch viewer when transfer finishes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoOpenFile,
                            onCheckedChange = {
                                autoOpenFile = it
                                ReceiverSettingsManager.setAutoOpenFile(context, it)
                            }
                        )
                    }
                }
            }

            // Group 3: Diagnostics & Telemetry
            item {
                SettingsCategoryCard(title = "Diagnostics & HUD", icon = Icons.Default.Analytics) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verbose HUD Overlay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Display real-time packet hex & intensity bars", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = verboseHud,
                            onCheckedChange = {
                                verboseHud = it
                                ReceiverSettingsManager.setVerboseHud(context, it)
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Export Telemetry", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Save log file to Downloads on completion", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoExportLogs,
                            onCheckedChange = {
                                autoExportLogs = it
                                ReceiverSettingsManager.setAutoExportLogs(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val path = DiagnosticLogger.exportToFile(context)
                                if (path != null) {
                                    Toast.makeText(context, "Log exported:\n$path", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to export log", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Logs", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                DiagnosticLogger.clear()
                                Toast.makeText(context, "Telemetry log buffer cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Buffer", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Group 4: Device & Profiler Specs
            item {
                SettingsCategoryCard(title = "Device Profiler Info", icon = Icons.Default.PhoneAndroid) {
                    ProfileRow("Target Resolution", "${profile.targetWidth} x ${profile.targetHeight} px")
                    ProfileRow("Optimal Aspect", "${profile.targetHeight}x${profile.targetWidth} Portrait")
                    ProfileRow("OpenCV Native Lib", "Loaded & Active")
                    ProfileRow("CameraX Provider", "Active")
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

