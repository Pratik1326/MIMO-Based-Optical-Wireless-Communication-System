# MIMO Optical Receiver - Complete Source Code Reference

This file contains the complete, up-to-date source code for all project files, organized by file path.

---


### File: app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mimo_optical_receiver"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mimo_optical_receiver"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // CameraX
    val cameraxVersion = "1.3.3"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // OpenCV for Android
    implementation("com.quickbirdstudios:opencv-contrib:4.5.3.0")

    // ZXing for high-performance screen QR code decoding
    implementation("com.google.zxing:core:3.5.3")

    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

---


### File: app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MIMO_Optical_Receiver">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.MIMO_Optical_Receiver"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>

</manifest>

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/MainActivity.kt

```kotlin
package com.example.mimo_optical_receiver

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.example.mimo_optical_receiver.ui.AppNavigation
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize OpenCV
        if (OpenCVLoader.initDebug()) {
            Log.i("OpenCV", "OpenCV initialization successful.")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed.")
        }

        enableEdgeToEdge()
        setContent {
            // Force dark theme as requested
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNavigation()
            }
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/ui/AppNavigation.kt

```kotlin
package com.example.mimo_optical_receiver.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mimo_optical_receiver.ui.screens.HomeScreen
import com.example.mimo_optical_receiver.ui.screens.ScannerScreen
import com.example.mimo_optical_receiver.ui.screens.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Scanner : Screen("scanner", "Scanner", Icons.Default.PlayArrow)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Scanner, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Screen.Scanner.route) { ScannerScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/ui/ReceiverUiState.kt

```kotlin
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

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/ui/screens/HomeScreen.kt

```kotlin
package com.example.mimo_optical_receiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mimo_optical_receiver.util.TransferHistoryManager
import com.example.mimo_optical_receiver.util.TransferRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToScanner: () -> Unit = {}) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(TransferHistoryManager.getHistory(context)) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Refresh history whenever HomeScreen is composed
    LaunchedEffect(Unit) {
        history = TransferHistoryManager.getHistory(context)
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Transfer History?") },
            text = { Text("This will clear all records from your recent transfer history. The files will remain stored in your Downloads folder.") },
            confirmButton = {
                TextButton(onClick = {
                    TransferHistoryManager.clearHistory(context)
                    history = emptyList()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MIMO Optical Receiver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Optical Wireless Communication",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                        }
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

            // 1. Hero Action Banner: Launch Scanner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToScanner() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "READY TO CAPTURE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Optical Scanner",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Align camera with PC screen to scan QR and stream optical packets.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onNavigateToScanner() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LAUNCH SCANNER", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Cumulative Dashboard Metrics
            item {
                val totalBytes = history.sumOf { it.fileSizeBytes }
                val totalFiles = history.size
                val lastMimo = history.firstOrNull()?.mimoMode ?: "--"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Received Files",
                        value = totalFiles.toString(),
                        icon = Icons.Default.CloudDownload,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Data",
                        value = TransferHistoryManager.formatFileSize(totalBytes),
                        icon = Icons.Default.DataUsage,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Last Mode",
                        value = lastMimo,
                        icon = Icons.Default.Speed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Section Header: Recent Transfers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Transfers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (history.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${history.size} items",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 4. History List / Empty State
            if (history.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Optical Transfers Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Transferred files will automatically show up here with options to open and share them directly.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(history, key = { it.id }) { record ->
                    TransferRecordCard(
                        record = record,
                        onOpen = { TransferHistoryManager.openFile(context, record) },
                        onShare = { TransferHistoryManager.shareFile(context, record) },
                        onDelete = {
                            TransferHistoryManager.deleteRecord(context, record.id)
                            history = TransferHistoryManager.getHistory(context)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TransferRecordCard(
    record: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Icon Box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${TransferHistoryManager.formatFileSize(record.fileSizeBytes)} • ${TransferHistoryManager.formatTimestamp(record.timestamp)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // MIMO Mode Badge
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = record.mimoMode,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: OPEN, SHARE, DELETE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 13.sp)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/ui/screens/SettingsScreen.kt

```kotlin
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


```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/ui/screens/ScannerScreen.kt

```kotlin
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
                DiagRow("Throughput", "${uiState.throughputKbs} KB/s")

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
                            currentState = "READY"
                        )
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
                    val isObstructed = statusText.contains("OBSTRUCTED") || statusText.startsWith("âš ï¸")
                    val obstructionReason = if (isObstructed) {
                        statusText.substringAfter("OBSTRUCTED:").trim().takeIf { it.isNotEmpty() }
                            ?: "Camera view blocked. Please remove object in front of screen."
                    } else null

                    uiState = uiState.copy(
                        isPerspectiveLocked = locked,
                        arucoMarkersDetected = markers,
                        syncConfidenceScore = confidence,
                        validPacketsReceived = validCount,
                        duplicatePackets = duplicateCount,
                        crcFailedPackets = crcFailed,
                        transferProgress = progress,
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
                    uiState = uiState.copy(currentState = "RECEIVING_DATA")
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
                    uiState = uiState.copy(
                        qrSessionId = null,
                        qrFilename = null,
                        qrTotalPackets = 0,
                        qrStatus = "Pending",
                        currentState = "WAITING_FOR_QR",
                        validPacketsReceived = 0,
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
                            uiState.currentState == "RECEIVING_DATA" -> Color(0xFF29B6F6)
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
                            uiState.currentState == "RECEIVING_DATA" -> "Receiving Data â€¢ ${uiState.qrMimoMode ?: "1x1"}"
                            uiState.qrSessionId != null -> "QR Handshake Synced â€¢ Ready"
                            uiState.isPerspectiveLocked -> "Screen Locked â€¢ Scanning QR..."
                            uiState.arucoMarkersDetected > 0 -> "Aligning ArUco (${uiState.arucoMarkersDetected}/4)"
                            else -> "Point Camera at PC Screen"
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
                            if (uiState.currentState == "RECEIVING_DATA") {
                                Text(
                                    text = "${uiState.throughputKbs} KB/s",
                                    color = Color(0xFF00E676),
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
                uiState.qrSessionId != null -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START RECEIVING (${uiState.qrFilename ?: "FILE"})", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
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
                                uiState.isPerspectiveLocked -> "SCREEN LOCKED â€¢ SCANNING QR METADATA..."
                                uiState.arucoMarkersDetected > 0 -> "ALIGNING ARUCO (${uiState.arucoMarkersDetected}/4)"
                                else -> "POINT CAMERA AT PC SCREEN"
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

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/CameraManager.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.mimo_optical_receiver.profiler.DeviceProfile
import com.example.mimo_optical_receiver.protocol.SessionMetadata
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraManager(
    deviceProfile: DeviceProfile,
    analyzerMode: FrameAnalyzer.AnalyzerMode,
    sessionTotalPackets: Int?,
    sessionPacketSize: Int? = null,
    sessionMimoMode: String? = null,
    onFpsUpdated: (Double) -> Unit,
    onQrScanned: (SessionMetadata) -> Unit,
    onTransferComplete: (ByteArray) -> Unit,
    onStatusUpdated: (Boolean, String, Int, Int, Int, Int, Float, List<Pair<Float, Float>>, List<Double>) -> Unit,
    onCameraControlReady: ((androidx.camera.core.CameraControl) -> Unit)? = null,
    onRetransmitRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Preserve a single instance of FrameAnalyzer across recompositions
    val analyzer = remember {
        FrameAnalyzer(
            onFpsUpdated = onFpsUpdated,
            onQrScanned = onQrScanned,
            onTransferComplete = onTransferComplete,
            onStatusUpdated = onStatusUpdated,
            onRetransmitRequested = onRetransmitRequested
        )
    }

    // Reactively push mode state changes down to the analyzer
    LaunchedEffect(analyzerMode) {
        analyzer.setMode(analyzerMode)
    }

    // Reactively initialize the SequenceManager once metadata is scanned
    LaunchedEffect(sessionTotalPackets, sessionPacketSize, sessionMimoMode) {
        if (sessionTotalPackets != null && sessionTotalPackets > 0) {
            analyzer.initializeSession(
                totalPackets = sessionTotalPackets,
                packetSize = sessionPacketSize ?: 64,
                mimoMode = sessionMimoMode ?: "1x1"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER

                // Bind camera hardware ONCE in factory - NEVER in update block to eliminate lifecycle churn
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(deviceProfile.targetWidth, deviceProfile.targetHeight),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }

                    // Use the native target resolution (1280x720 / 1920x1080) for high-fidelity QR and grid scanning
                    val imageAnalysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(resolutionSelector)

                    // CAMERA2 INTEROP: Continuous autofocus picture mode, auto exposure, disable stabilization blur
                    val ext = Camera2Interop.Extender(imageAnalysisBuilder)
                    ext.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    ext.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    ext.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                    ext.setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)

                    val imageAnalysis = imageAnalysisBuilder.build()
                    imageAnalysis.setAnalyzer(executor, analyzer)

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )

                        camera.cameraControl.enableTorch(false)
                        onCameraControlReady?.invoke(camera.cameraControl)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        update = {
            // Keep empty: Camera session lifecycle is bound once in factory. No lifecycle churn on recomposition!
        }
    )
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/FrameAnalyzer.kt

```kotlin
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
            val normalizedBounds = if (detectionResult.markerCount >= 4) {
                val frameWidth = grayMat!!.cols().toFloat()
                val frameHeight = grayMat!!.rows().toFloat()
                detectionResult.screenCorners.map { Pair((it.x / frameWidth).toFloat(), (it.y / frameHeight).toFloat()) }
            } else emptyList()

            // 2. ALWAYS Attempt Perspective Warp if 4 corners found
            if (detectionResult.markerCount >= 4) {
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
                    onQrScanned(metadata)
                }
                
                val isLocked = warpedMat != null
                val statusMsg = when {
                    sequenceManager != null -> "METADATA SYNCED (${sequenceManager?.packetSize}B ${activeMimoMode})"
                    detectionResult.isObstructed -> "âš ï¸ VIEW OBSTRUCTED: ${detectionResult.obstructionReason ?: "Clear camera path"}"
                    isLocked -> "SCREEN LOCKED â€¢ SCANNING QR..."
                    else -> "ALIGN ARUCO (${detectionResult.markerCount}/4)"
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

                // DECODE: Decode whenever link is locked and frame is not mid-tear (confidence >= 30)
                if (needsMorePackets && confidence >= 30) {
                    channelMats = regionDetector.extractChannels(warpedMat!!, activeMimoMode)
                    val decodeResult = symbolDecoder.decode(channelMats, activePacketSize)
                    currentIntensities = decodeResult.intensities

                    // Decode each quadrant channel independently without serial concatenating or bit-slip
                    val validPackets = packetAssembler.processChannelBits(decodeResult.channelBits)

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
                    "âš ï¸ VIEW OBSTRUCTED: ${detectionResult.obstructionReason ?: "Clear camera path"}"
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


```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/OpticalFeedbackController.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import android.util.Log
import androidx.camera.core.CameraControl
import kotlinx.coroutines.*

class OpticalFeedbackController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cameraControl: CameraControl? = null
    private var isPulsing = false

    fun attachCameraControl(control: CameraControl?) {
        this.cameraControl = control
        Log.d("OPTICAL_FEEDBACK", "CameraControl attached successfully: " + control)
    }

    /**
     * Pattern 1: Receiver Locked & Ready (1 pulse, 280ms)
     */
    fun sendReadySignal() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 1: READY / LOCKED")
        pulse(count = 1, pulseDurationMs = 280, gapDurationMs = 200)
    }

    /**
     * Pattern 2: NACK / Retransmit Missing Packet (2 pulses, 220ms ON / 250ms OFF)
     */
    fun sendRetransmitSignal() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 2: NACK / RETRANSMIT")
        pulse(count = 2, pulseDurationMs = 220, gapDurationMs = 250)
    }

    /**
     * Pattern 3: Transfer Complete & SHA-256 Verified ACK
     * Sends an 850ms sustained strobe (triggering immediate PC auto-off at >=650ms),
     * followed by two verification pulses (200ms ON / 220ms OFF).
     */
    fun sendCompletionAck() {
        Log.i("OPTICAL_FEEDBACK", "Sending Pattern 3: COMPLETE ACK (Sustained Strobe + Verification)")
        val control = cameraControl ?: run {
            Log.w("OPTICAL_FEEDBACK", "Cannot pulse flash: CameraControl is null")
            return
        }
        if (isPulsing) return

        scope.launch {
            isPulsing = true
            try {
                // 1. Sustained strobe (>= 650ms threshold on PC triggers instant auto-stop)
                control.enableTorch(true)
                delay(850)
                control.enableTorch(false)
                delay(250)

                // 2. Verification pulses
                for (i in 0 until 2) {
                    control.enableTorch(true)
                    delay(200)
                    control.enableTorch(false)
                    if (i < 1) delay(220)
                }
            } catch (e: Exception) {
                Log.e("OPTICAL_FEEDBACK", "Error in sendCompletionAck", e)
            } finally {
                try {
                    control.enableTorch(false)
                } catch (_: Exception) {}
                isPulsing = false
            }
        }
    }

    private fun pulse(count: Int, pulseDurationMs: Long, gapDurationMs: Long) {
        val control = cameraControl ?: run {
            Log.w("OPTICAL_FEEDBACK", "Cannot pulse flash: CameraControl is null")
            return
        }
        if (isPulsing) return

        scope.launch {
            isPulsing = true
            try {
                for (i in 0 until count) {
                    control.enableTorch(true)
                    delay(pulseDurationMs)
                    control.enableTorch(false)
                    if (i < count - 1) {
                        delay(gapDurationMs)
                    }
                }
            } catch (e: Exception) {
                Log.e("OPTICAL_FEEDBACK", "Error toggling flash torch", e)
            } finally {
                try {
                    control.enableTorch(false)
                } catch (_: Exception) {}
                isPulsing = false
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/QrMetadataParser.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import android.util.Log
import com.example.mimo_optical_receiver.protocol.SessionMetadata
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.json.JSONObject
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector

class QrMetadataParser {
    
    private val qrDetector = QRCodeDetector()

    private val zxingReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    /**
     * Attempts to detect and decode a QR code.
     * Uses ZXing with HybridBinarizer on warpedMat (unskewed screen area) as primary,
     * then raw camera frame, adaptive thresholding, and OpenCV QRCodeDetector as fallback.
     */
    fun detectAndParse(primaryMat: Mat, secondaryMat: Mat? = null): SessionMetadata? {
        // 1. Tier 1 (Best): ZXing on un-skewed warped screen matrix
        if (secondaryMat != null && !secondaryMat.empty()) {
            val res = decodeMatZxing(secondaryMat)
            if (res != null) return parseJsonMetadata(res)
        }

        // 2. Tier 2: ZXing on raw camera frame
        if (!primaryMat.empty()) {
            val res = decodeMatZxing(primaryMat)
            if (res != null) return parseJsonMetadata(res)
        }

        // 3. Tier 3: ZXing on Otsu-thresholded warped matrix (cuts through glare / backlight bleed)
        if (secondaryMat != null && !secondaryMat.empty()) {
            val otsuWarped = Mat()
            try {
                Imgproc.threshold(secondaryMat, otsuWarped, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                val res = decodeMatZxing(otsuWarped)
                if (res != null) return parseJsonMetadata(res)
            } catch (_: Exception) {
            } finally {
                otsuWarped.release()
            }
        }

        // 4. Tier 4: OpenCV QRCodeDetector fallback on warped matrix
        if (secondaryMat != null && !secondaryMat.empty()) {
            try {
                val cvDecoded = qrDetector.detectAndDecode(secondaryMat)
                if (!cvDecoded.isNullOrEmpty()) {
                    val meta = parseJsonMetadata(cvDecoded)
                    if (meta != null) return meta
                }
            } catch (_: Exception) {}
        }

        // 5. Tier 5: OpenCV QRCodeDetector fallback on primary frame
        if (!primaryMat.empty()) {
            try {
                val cvDecoded = qrDetector.detectAndDecode(primaryMat)
                if (!cvDecoded.isNullOrEmpty()) {
                    val meta = parseJsonMetadata(cvDecoded)
                    if (meta != null) return meta
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun decodeMatZxing(mat: Mat): String? {
        if (mat.empty()) return null
        return try {
            val width = mat.cols()
            val height = mat.rows()
            val buffer = ByteArray(width * height)
            mat.get(0, 0, buffer)

            val source = PlanarYUVLuminanceSource(
                buffer, width, height, 0, 0, width, height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            synchronized(zxingReader) {
                try {
                    zxingReader.decodeWithState(bitmap).text
                } finally {
                    zxingReader.reset()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonMetadata(jsonString: String): SessionMetadata? {
        if (jsonString.isEmpty()) return null
        return try {
            Log.i("QR_PARSER", "Successfully decoded QR: $jsonString")
            val json = JSONObject(jsonString)

            val filename = when {
                json.has("filename") -> json.optString("filename", "unknown")
                json.has("f") -> json.optString("f", "unknown")
                else -> "unknown"
            }.trim()

            val totalPackets = when {
                json.has("total_packets") -> json.optInt("total_packets", 0)
                json.has("p") -> json.optInt("p", 0)
                else -> 0
            }

            val fileSize = when {
                json.has("file_size") -> json.optInt("file_size", 0)
                json.has("s") -> json.optInt("s", 0)
                else -> 0
            }

            val mimoMode = when {
                json.has("mimo_mode") -> json.optString("mimo_mode", "1x1")
                json.has("m") -> json.optString("m", "1x1")
                else -> "1x1"
            }.trim()

            val symbolMode = when {
                json.has("symbol_mode") -> json.optString("symbol_mode", "BINARY")
                json.has("y") -> json.optString("y", "BINARY")
                else -> "BINARY"
            }.trim()

            val packetSize = when {
                json.has("packet_size") -> json.optInt("packet_size", 64)
                json.has("z") -> json.optInt("z", 64)
                else -> 64
            }

            val sessionId = when {
                json.has("session_id") -> json.optString("session_id", "UNKNOWN")
                json.has("id") -> json.optString("id", "UNKNOWN")
                else -> "UNKNOWN"
            }.trim()

            val version = when {
                json.has("protocol_version") -> json.optInt("protocol_version", 1)
                json.has("v") -> json.optInt("v", 1)
                else -> 1
            }

            if (totalPackets <= 0 || filename == "unknown") {
                return null
            }

            SessionMetadata(
                protocol_version = version,
                filename = filename,
                file_size = fileSize,
                total_packets = totalPackets,
                mimo_mode = mimoMode,
                symbol_mode = symbolMode,
                packet_size = packetSize,
                session_id = sessionId
            )
        } catch (e: Exception) {
            Log.d("QR_PARSER", "Failed parsing metadata JSON: ${e.message}")
            null
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/ScreenDetector.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import org.opencv.aruco.Aruco
import org.opencv.aruco.DetectorParameters
import org.opencv.aruco.Dictionary
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

data class ScreenDetectionResult(
    val markerCount: Int,
    val screenCorners: List<Point>,
    val isObstructed: Boolean = false,
    val obstructionReason: String? = null
)

class ScreenDetector {
    private val arucoDict: Dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_4X4_50)
    
    private var prevGrayMat: Mat? = null
    private var prevCornersMat: MatOfPoint2f? = null
    private var isTracking = false
    private var trackingFrameCount = 0

    fun detect(grayMat: Mat): ScreenDetectionResult {
        val totalFrameArea = grayMat.cols().toDouble() * grayMat.rows().toDouble()
        val minRequiredArea = totalFrameArea * 0.10 // 10% minimum frame area

        // 1. OPTICAL FLOW TRACKING (60 FPS Fast Path)
        if (isTracking && prevGrayMat != null && prevCornersMat != null) {
            trackingFrameCount++
            
            // Re-validate against ArUco / Contours every 5 frames (~100ms) to prevent hand-tremor drift
            if (trackingFrameCount > 5) {
                resetTracking()
            } else {
                val nextCornersMat = MatOfPoint2f()
                val status = MatOfByte()
                val err = MatOfFloat()

                try {
                    val winSize = Size(31.0, 31.0)
                    val maxLevel = 3
                    val criteria = TermCriteria(TermCriteria.EPS or TermCriteria.COUNT, 20, 0.03)

                    Video.calcOpticalFlowPyrLK(
                        prevGrayMat!!,
                        grayMat,
                        prevCornersMat!!,
                        nextCornersMat,
                        status,
                        err,
                        winSize,
                        maxLevel,
                        criteria
                    )

                    val statusArray = status.toArray()
                    val allTracked = statusArray.size == 4 && statusArray.all { it.toInt() == 1 }

                    if (allTracked) {
                        val trackedPoints = nextCornersMat.toList()
                        val currentArea = calculatePolygonArea(trackedPoints)

                        // GUARDRAIL: If tracked box shrinks below 10% frame area, force reset
                        if (currentArea >= minRequiredArea) {
                            prevCornersMat?.release()
                            prevCornersMat = nextCornersMat

                            prevGrayMat?.release()
                            prevGrayMat = grayMat.clone()

                            return ScreenDetectionResult(4, trackedPoints)
                        } else {
                            resetTracking()
                        }
                    } else {
                        resetTracking()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    resetTracking()
                } finally {
                    status.release()
                    err.release()
                }
            }
        }

        // 2. PRIMARY STRATEGY: ARUCO ANCHOR DETECTION (IDs 0, 1, 2, 3 with Expanded Window)
        var ids: Mat? = null
        val corners = ArrayList<Mat>()

        try {
            ids = Mat()

            val parameters = DetectorParameters.create().apply {
                set_adaptiveThreshWinSizeMin(3)
                set_adaptiveThreshWinSizeMax(55)
                set_adaptiveThreshWinSizeStep(8)
                set_adaptiveThreshConstant(7.0)
                set_polygonalApproxAccuracyRate(0.05)
            }

            Aruco.detectMarkers(grayMat, arucoDict, corners, ids, parameters)

            if (ids.rows() >= 3) {
                val detectedMarkers = mutableMapOf<Int, Mat>()
                for (i in 0 until ids.rows()) {
                    val markerId = ids.get(i, 0)[0].toInt()
                    if (markerId in 0..3) {
                        detectedMarkers[markerId] = corners[i]
                    }
                }

                fun getCenterAndCorners(mat: Mat): Pair<Point, List<Point>> {
                    val pts = FloatArray(8)
                    mat.get(0, 0, pts)
                    val cx = (pts[0] + pts[2] + pts[4] + pts[6]) / 4.0
                    val cy = (pts[1] + pts[3] + pts[5] + pts[7]) / 4.0
                    val cornerList = listOf(
                        Point(pts[0].toDouble(), pts[1].toDouble()),
                        Point(pts[2].toDouble(), pts[3].toDouble()),
                        Point(pts[4].toDouble(), pts[5].toDouble()),
                        Point(pts[6].toDouble(), pts[7].toDouble())
                    )
                    return Pair(Point(cx, cy), cornerList)
                }

                val parsedMarkers = mutableMapOf<Int, Pair<Point, List<Point>>>()
                for ((id, mat) in detectedMarkers) {
                    parsedMarkers[id] = getCenterAndCorners(mat)
                }

                if (parsedMarkers.size >= 3) {
                    // INNER CORNERS: Isolate pure 608x608 Central Data Area (0% ArUco marker pixel bleeding)
                    // Marker corner indexing in OpenCV: 0 = TL, 1 = TR, 2 = BR, 3 = BL
                    var dataTL: Point? = parsedMarkers[0]?.second?.get(2) // Marker 0 (TL) -> Inner BR corner
                    var dataTR: Point? = parsedMarkers[1]?.second?.get(3) // Marker 1 (TR) -> Inner BL corner
                    var dataBR: Point? = parsedMarkers[2]?.second?.get(0) // Marker 2 (BR) -> Inner TL corner
                    var dataBL: Point? = parsedMarkers[3]?.second?.get(1) // Marker 3 (BL) -> Inner TR corner

                    // GLARE RECOVERY: Reconstruct missing 4th inner corner geometrically if 3 are seen
                    if (dataTL == null && dataTR != null && dataBR != null && dataBL != null) {
                        dataTL = Point(dataTR.x + dataBL.x - dataBR.x, dataTR.y + dataBL.y - dataBR.y)
                    } else if (dataTR == null && dataTL != null && dataBR != null && dataBL != null) {
                        dataTR = Point(dataTL.x + dataBR.x - dataBL.x, dataTL.y + dataBR.y - dataBL.y)
                    } else if (dataBL == null && dataTL != null && dataTR != null && dataBR != null) {
                        dataBL = Point(dataTL.x + dataBR.x - dataTR.x, dataTL.y + dataBR.y - dataTR.y)
                    } else if (dataBR == null && dataTL != null && dataTR != null && dataBL != null) {
                        dataBR = Point(dataTR.x + dataBL.x - dataTL.x, dataTR.y + dataBL.y - dataTL.y)
                    }

                    if (dataTL != null && dataTR != null && dataBR != null && dataBL != null) {
                        val sortedCorners = listOf(dataTL, dataTR, dataBR, dataBL)
                        val detectedArea = calculatePolygonArea(sortedCorners)

                        if (detectedArea >= minRequiredArea) {
                            prevCornersMat?.release()
                            prevCornersMat = MatOfPoint2f(*sortedCorners.toTypedArray())

                            prevGrayMat?.release()
                            prevGrayMat = grayMat.clone()

                            isTracking = true
                            trackingFrameCount = 0

                            return ScreenDetectionResult(4, sortedCorners)
                        }
                    }
                }
            } else if (ids.rows() in 1..2) {
                val detected = ids.rows()
                return ScreenDetectionResult(
                    markerCount = detected,
                    screenCorners = emptyList(),
                    isObstructed = true,
                    obstructionReason = "Screen obstructed: Only $detected/4 corner anchors visible. Clear camera view."
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ids?.release()
            for (mat in corners) {
                mat.release()
            }
            corners.clear()
        }

        // 3. SECONDARY STRATEGY: OUTER CONTOUR WITH SYMMETRY SANITIZATION
        val binaryMat = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()

        try {
            Imgproc.threshold(grayMat, binaryMat, 160.0, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.findContours(
                binaryMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            var bestCorners: List<Point>? = null
            var maxArea = 0.0

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val area = Imgproc.contourArea(contour2f)

                if (area >= minRequiredArea && area > maxArea) {
                    val peri = Imgproc.arcLength(contour2f, true)
                    val approx2f = MatOfPoint2f()
                    Imgproc.approxPolyDP(contour2f, approx2f, 0.02 * peri, true)

                    val approxPoints = approx2f.toList()
                    val approxMatOfPoint = MatOfPoint(*approxPoints.toTypedArray())

                    if (approxPoints.size == 4 && Imgproc.isContourConvex(approxMatOfPoint)) {
                        maxArea = area
                        
                        val tl = approxPoints.minByOrNull { it.x + it.y } ?: approxPoints[0]
                        val br = approxPoints.maxByOrNull { it.x + it.y } ?: approxPoints[0]
                        var tr = approxPoints.maxByOrNull { it.x - it.y } ?: approxPoints[0]
                        var bl = approxPoints.minByOrNull { it.x - it.y } ?: approxPoints[0]
                        
                        // SYMMETRY SANITIZATION: Prevent TR or BL corner from pulling inward when data cells flash white
                        val topDist = distance(tl, tr)
                        val botDist = distance(bl, br)

                        if (topDist < botDist * 0.82) {
                            tr = Point(tl.x + (br.x - bl.x), tl.y + (br.y - bl.y))
                        }

                        bestCorners = listOf(tl, tr, br, bl)
                    }
                    
                    approx2f.release()
                    approxMatOfPoint.release()
                }
                contour2f.release()
            }

            if (bestCorners != null) {
                prevCornersMat?.release()
                prevCornersMat = MatOfPoint2f(*bestCorners.toTypedArray())

                prevGrayMat?.release()
                prevGrayMat = grayMat.clone()

                isTracking = true
                trackingFrameCount = 0

                return ScreenDetectionResult(4, bestCorners)
            }

            return ScreenDetectionResult(0, emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            return ScreenDetectionResult(0, emptyList())
        } finally {
            binaryMat.release()
            hierarchy.release()
            for (c in contours) {
                c.release()
            }
            contours.clear()
        }
    }

    private fun distance(p1: Point, p2: Point): Double {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2.0) + Math.pow(p2.y - p1.y, 2.0))
    }

    private fun calculatePolygonArea(corners: List<Point>): Double {
        if (corners.size < 3) return 0.0
        val matOfPoint = MatOfPoint2f(*corners.toTypedArray())
        val area = Imgproc.contourArea(matOfPoint)
        matOfPoint.release()
        return area
    }

    fun resetTracking() {
        isTracking = false
        trackingFrameCount = 0
        prevGrayMat?.release()
        prevGrayMat = null
        prevCornersMat?.release()
        prevCornersMat = null
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/PerspectiveCorrector.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class PerspectiveCorrector {

    companion object {
        // Physical geometry alignment between PC Transmitter and Android Receiver:
        // PC Transmitter frame: 800x800 px.
        // ArUco marker size = 44 px, margin = 6 px.
        // Inner corners of ArUco markers span 50 px to 750 px (width = 700 px).
        // Central data area spans 56 px to 744 px (width = 688 px).
        // The 6 px quiet margin on each side corresponds to:
        // 6.0 * (512.0 / 688.0) = 4.465116 px in 512x512 warped space.
        private const val INNER_MARKER_SPAN = 700.0
        private const val DATA_AREA_SPAN = 688.0
        private const val TARGET_WARP_SIZE = 512.0
        private const val MARGIN_OFFSET = (INNER_MARKER_SPAN - DATA_AREA_SPAN) / 2.0 * (TARGET_WARP_SIZE / DATA_AREA_SPAN)
    }

    fun correct(grayMat: Mat, corners: List<Point>): Mat? {
        if (corners.size != 4) return null

        var transform: Mat? = null
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null
        var warpedMat: Mat? = null

        try {
            // Construct source points array (TL, TR, BR, BL) from detected ArUco marker inner corners
            srcPts = MatOfPoint2f(corners[0], corners[1], corners[2], corners[3])

            // Map ArUco inner corners outward by MARGIN_OFFSET (~4.465 px) so that the
            // 512x512 warped output matrix aligns 100% to the active data grid cells (0 px offset).
            dstPts = MatOfPoint2f(
                Point(-MARGIN_OFFSET, -MARGIN_OFFSET),
                Point(TARGET_WARP_SIZE + MARGIN_OFFSET, -MARGIN_OFFSET),
                Point(TARGET_WARP_SIZE + MARGIN_OFFSET, TARGET_WARP_SIZE + MARGIN_OFFSET),
                Point(-MARGIN_OFFSET, TARGET_WARP_SIZE + MARGIN_OFFSET)
            )

            // Compute Perspective Transform and Warp
            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            warpedMat = Mat()
            Imgproc.warpPerspective(grayMat, warpedMat, transform, Size(TARGET_WARP_SIZE, TARGET_WARP_SIZE))

            return warpedMat
        } catch (e: Exception) {
            e.printStackTrace()
            warpedMat?.release() // Ensure we don't leak an incomplete Mat on failure
            return null
        } finally {
            // Explicitly release intermediate transformation matrices
            transform?.release()
            srcPts?.release()
            dstPts?.release()
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/RegionDetector.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import org.opencv.core.Mat
import org.opencv.core.Rect

class RegionDetector {

    /**
     * Slices a 512x512 perspective-corrected matrix into individual MIMO spatial channels based on mode.
     * Uses 0 margin to ensure the outer data grid cells and padding cells are preserved at exact pixel boundaries.
     * 
     * Supported Modes: "1x1", "2x2", "4x4"
     * 
     * @param warpedMat The 512x512 un-skewed screen matrix.
     * @param mimoMode Spatial channel layout configuration ("1x1", "2x2", "4x4").
     * @return List of isolated Mat quadrants for each MIMO spatial channel.
     */
    fun extractChannels(warpedMat: Mat, mimoMode: String): List<Mat> {
        val channels = mutableListOf<Mat>()
        when (mimoMode.lowercase()) {
            "1x1" -> {
                // Full 100% inner data grid (Zero margin cropping)
                channels.add(warpedMat.clone())
            }
            "2x2" -> {
                val halfW = warpedMat.cols() / 2
                val halfH = warpedMat.rows() / 2
                channels.add(warpedMat.submat(Rect(0, 0, halfW, halfH))) // Top-Left
                channels.add(warpedMat.submat(Rect(halfW, 0, halfW, halfH))) // Top-Right
                channels.add(warpedMat.submat(Rect(0, halfH, halfW, halfH))) // Bottom-Left
                channels.add(warpedMat.submat(Rect(halfW, halfH, halfW, halfH))) // Bottom-Right
            }
            "4x4" -> {
                val quarterW = warpedMat.cols() / 4
                val quarterH = warpedMat.rows() / 4
                for (r in 0 until 4) {
                    for (c in 0 until 4) {
                        channels.add(warpedMat.submat(Rect(c * quarterW, r * quarterH, quarterW, quarterH)))
                    }
                }
            }
            else -> {
                channels.add(warpedMat.clone())
            }
        }
        return channels
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/SyncDetector.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import org.opencv.core.Core
import org.opencv.core.Mat

class SyncDetector(
    private val settlingThreshold: Double = 26.0,
    private val stableThreshold: Double = 18.0,
    private val requiredSettledFrames: Int = 1
) {
    
    private var prevMat: Mat? = null
    private var isSettling = false
    private var settledConsecutiveFrames = 0

    /**
     * Compares the current warped matrix against the previous one to drop duplicate frames
     * and filter out LCD screen tearing (half-drawn frames).
     * 
     * Handheld camera shake typically has meanDiff between 6.0 and 15.0, whereas a monitor frame
     * swap causes a transient jump > 26.0.
     * 
     * @return Pair<Boolean, Int> where Boolean is `isNewFrame` and Int is `syncConfidenceScore` (0-100).
     */
    fun isNewFrame(currentMat: Mat): Pair<Boolean, Int> {
        if (prevMat == null) {
            prevMat = currentMat.clone()
            return Pair(true, 100)
        }

        val diffMat = Mat()
        return try {
            // Calculate absolute difference between current frame (Ft) and previous frame (Ft-1)
            Core.absdiff(currentMat, prevMat, diffMat)
            
            val meanDiff = Core.mean(diffMat).`val`[0]

            // Always update prevMat so diffMat measures consecutive inter-frame delta
            prevMat?.release()
            prevMat = currentMat.clone()

            if (meanDiff > settlingThreshold) {
                // SCREEN FLIPPING / TEARING DETECTED: Monitor is mid-draw or switching frames
                isSettling = true
                settledConsecutiveFrames = 0
                Pair(false, 15) // Mid-draw tearing
            } else if (isSettling) {
                if (meanDiff < stableThreshold) {
                    settledConsecutiveFrames++
                    if (settledConsecutiveFrames >= requiredSettledFrames) {
                        isSettling = false
                        settledConsecutiveFrames = 0
                        Pair(true, 100) // Clean, un-torn fresh frame ready for decoding!
                    } else {
                        Pair(false, 60) // Settling in progress
                    }
                } else {
                    settledConsecutiveFrames = 0
                    Pair(false, 25) // Still fluctuating/tearing
                }
            } else {
                // Steady state: Frame is stable and settled (handheld micro-shake stays <= 18.0)
                Pair(false, 100) // 100% link lock confidence during settled frames!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, 0)
        } finally {
            diffMat.release()
        }
    }

    /**
     * Should be called when the pipeline is destroyed or reset to prevent memory leaks.
     */
    fun release() {
        prevMat?.release()
        prevMat = null
        isSettling = false
        settledConsecutiveFrames = 0
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/camera/SymbolDecoder.kt

```kotlin
package com.example.mimo_optical_receiver.camera

import com.example.mimo_optical_receiver.protocol.ProtocolConstants
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class SymbolDecoder {

    var lastMin: Double = 0.0
        private set
    var lastMax: Double = 0.0
        private set
    var lastThreshold: Double = 0.0
        private set

    data class DecodeOutput(
        val channelBits: List<ByteArray>,
        val flatBits: ByteArray,
        val intensities: List<Double>
    )

    /**
     * Decodes binary values from each independent MIMO spatial channel.
     * Uses zero-allocation direct byte sampling and 2-Means dynamic clustering thresholding
     * with CLAHE contrast equalization to eliminate bit flips caused by monitor angle gradients,
     * backlight bleed, and room reflections.
     */
    fun decode(channelMats: List<Mat>, payloadSizeBytes: Int): DecodeOutput {
        // PC Transmitter sends: Header (11B) + Payload (NB) + CRC32 (4B) = N + 15 bytes total
        val totalBytes = payloadSizeBytes + ProtocolConstants.OVERHEAD_BYTES
        val totalBits = totalBytes * 8

        // Unpadded data side length
        val dataSide = Math.ceil(Math.sqrt(totalBits.toDouble())).toInt()

        // Total grid side including PC's 1-cell outer black padding (padding = 1)
        val gridSide = dataSide + 2

        val bitsPerQuadrant = dataSide * dataSide
        val channelBitsList = mutableListOf<ByteArray>()
        val allBits = mutableListOf<Byte>()
        val intensities = mutableListOf<Double>()

        for (chIndex in channelMats.indices) {
            val quadrantMat = channelMats[chIndex]
            val width = quadrantMat.cols()
            val height = quadrantMat.rows()

            // CLAHE Equalization: Restores crisp contrast across low-contrast top quadrants
            val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
            val equalizedMat = Mat()
            clahe.apply(quadrantMat, equalizedMat)

            // Direct buffer access: 0 JNI allocations per cell
            val quadBytes = ByteArray(width * height)
            equalizedMat.get(0, 0, quadBytes)
            equalizedMat.release()

            val cellWidth = width / gridSide.toDouble()
            val cellHeight = height / gridSide.toDouble()

            // Center-Cell Core Sampling Insets (25% margin on all 4 sides of each cell)
            val insetX = cellWidth * 0.25
            val insetY = cellHeight * 0.25

            var sampleMin = 255.0
            var sampleMax = 0.0
            val cellMeans = DoubleArray(bitsPerQuadrant)
            var cellIdx = 0

            for (row in 1..dataSide) {
                val yStart = (row * cellHeight + insetY).toInt().coerceIn(0, height - 1)
                val yEnd = ((row + 1) * cellHeight - insetY).toInt().coerceAtLeast(yStart + 1).coerceAtMost(height)

                for (col in 1..dataSide) {
                    val xStart = (col * cellWidth + insetX).toInt().coerceIn(0, width - 1)
                    val xEnd = ((col + 1) * cellWidth - insetX).toInt().coerceAtLeast(xStart + 1).coerceAtMost(width)

                    var sum = 0
                    var count = 0
                    for (y in yStart until yEnd) {
                        val rowOffset = y * width
                        for (x in xStart until xEnd) {
                            sum += quadBytes[rowOffset + x].toInt() and 0xFF
                            count++
                        }
                    }

                    val meanVal = if (count > 0) sum.toDouble() / count else 0.0
                    cellMeans[cellIdx++] = meanVal
                    if (meanVal < sampleMin) sampleMin = meanVal
                    if (meanVal > sampleMax) sampleMax = meanVal
                }
            }

            // High-precision 2-Means dynamic cluster thresholding
            val localThreshold = compute2MeansThreshold(cellMeans, sampleMin, sampleMax)
            lastMin = sampleMin
            lastMax = sampleMax
            lastThreshold = localThreshold

            // Binarize symbols against precise data-core dynamic range for this channel
            val chBits = ByteArray(bitsPerQuadrant)
            for (i in cellMeans.indices) {
                val meanVal = cellMeans[i]
                if (i == 0) {
                    intensities.add(meanVal)
                }
                val bit = if (meanVal > localThreshold) 1.toByte() else 0.toByte()
                chBits[i] = bit
                allBits.add(bit)
            }
            channelBitsList.add(chBits)
        }

        return DecodeOutput(
            channelBits = channelBitsList,
            flatBits = allBits.toByteArray(),
            intensities = intensities
        )
    }

    /**
     * 2-Means clustering to find the optimal decision boundary between 0 and 1 symbols.
     * Converges in 3-5 iterations and is immune to LCD backlight falloff and glare outliers.
     */
    private fun compute2MeansThreshold(values: DoubleArray, minVal: Double, maxVal: Double): Double {
        if (maxVal - minVal < 15.0 || values.isEmpty()) {
            return (minVal + maxVal) / 2.0
        }

        // Initialize centers near bottom 25% and top 75%
        var c0 = minVal + (maxVal - minVal) * 0.25
        var c1 = minVal + (maxVal - minVal) * 0.75

        for (iter in 0 until 8) {
            var sum0 = 0.0
            var count0 = 0
            var sum1 = 0.0
            var count1 = 0

            for (v in values) {
                if (Math.abs(v - c0) <= Math.abs(v - c1)) {
                    sum0 += v
                    count0++
                } else {
                    sum1 += v
                    count1++
                }
            }

            val newC0 = if (count0 > 0) sum0 / count0 else c0
            val newC1 = if (count1 > 0) sum1 / count1 else c1

            if (Math.abs(newC0 - c0) < 0.5 && Math.abs(newC1 - c1) < 0.5) {
                c0 = newC0
                c1 = newC1
                break
            }
            c0 = newC0
            c1 = newC1
        }

        return (c0 + c1) / 2.0
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/SessionMetadata.kt

```kotlin
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

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/ProtocolConstants.kt

```kotlin
package com.example.mimo_optical_receiver.protocol

object ProtocolConstants {
    val MAGIC_HEADER = byteArrayOf(0x4D, 0x4F) // "MO"
    const val PROTOCOL_VERSION: Byte = 0x01
    const val HEADER_SIZE_BYTES = 11
    const val CRC_SIZE_BYTES = 4
    const val OVERHEAD_BYTES = HEADER_SIZE_BYTES + CRC_SIZE_BYTES
    const val DEFAULT_FEC_PARITY = 32
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/Packet.kt

```kotlin
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

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/PacketDecoder.kt

```kotlin
package com.example.mimo_optical_receiver.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class PacketDecoder {

    /**
     * Parses a raw byte array into a verified Packet.
     * Validates the structure, protocol version, and standard IEEE CRC-32 integrity.
     * 
     * @param bytes The raw frame byte array captured from the optical transmission.
     * @return Packet if successfully decoded and validated.
     * @throws IllegalArgumentException if the packet length or magic header is invalid.
     * @throws SecurityException if the CRC checksum fails.
     */
    fun decode(bytes: ByteArray): Packet {
        if (bytes.size < ProtocolConstants.OVERHEAD_BYTES) {
            throw IllegalArgumentException("Packet too short. Received ${bytes.size} bytes, min overhead is ${ProtocolConstants.OVERHEAD_BYTES}")
        }

        // Buffer wraps the byte array using BIG_ENDIAN standard
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // 1. Validate Magic Bytes (2 bytes)
        val magic1 = buffer.get()
        val magic2 = buffer.get()
        if (magic1 != ProtocolConstants.MAGIC_HEADER[0] || magic2 != ProtocolConstants.MAGIC_HEADER[1]) {
            throw IllegalArgumentException("Invalid magic header: [${magic1.toInt()}, ${magic2.toInt()}]")
        }

        // 2. Read Metadata (3 bytes)
        val version = buffer.get().toInt()
        val frameTypeInt = buffer.get().toInt()
        val frameType = FrameType.fromInt(frameTypeInt)
        val channelId = buffer.get().toInt()

        // 3. Read Sequence ID (4 bytes, unsigned 32-bit handled as Kotlin Long)
        val sequenceId = buffer.getInt().toUInt().toLong()

        // 4. Read Payload Length (2 bytes, unsigned 16-bit)
        val payloadLength = buffer.getShort().toUShort().toInt()

        // 5. Length Validation
        val expectedSize = ProtocolConstants.HEADER_SIZE_BYTES + payloadLength + ProtocolConstants.CRC_SIZE_BYTES
        if (bytes.size != expectedSize) {
            throw IllegalArgumentException("Size mismatch. Expected $expectedSize bytes, received ${bytes.size}")
        }

        // 6. Extract Payload
        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        // 7. Extract Received CRC (4 bytes, unsigned 32-bit)
        val receivedCrc = buffer.getInt().toUInt().toLong()

        // 8. Calculate Actual CRC over (Header + Payload)
        val crc32 = CRC32()
        crc32.update(bytes, 0, bytes.size - ProtocolConstants.CRC_SIZE_BYTES)
        val calculatedCrc = crc32.value // Returns a long

        // 9. Verify CRC Integrity
        if (calculatedCrc != receivedCrc) {
            throw SecurityException("CRC Mismatch! Expected: $calculatedCrc, Received: $receivedCrc")
        }

        // 10. Success! Return parsed packet.
        return Packet(
            frameType = frameType,
            channelId = channelId,
            sequenceId = sequenceId,
            payload = payload,
            version = version
        )
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/PacketAssembler.kt

```kotlin
package com.example.mimo_optical_receiver.protocol

import android.util.Log
import com.example.mimo_optical_receiver.util.DiagnosticLogger

class PacketAssembler {

    private val packetDecoder = PacketDecoder()

    var crcFailedCount = 0
        private set

    var lastMagicFound = false
        private set

    var lastMagicBitOffset = -1
        private set

    /**
     * Decodes independent spatial channels per frame with zero inter-channel bit drift.
     * Each channel quadrant is parsed independently starting at bit 0.
     */
    fun processChannelBits(channels: List<ByteArray>): List<Packet> {
        val validPackets = mutableListOf<Packet>()
        val magic16 = 0x4D4F // 'M' (0x4D), 'O' (0x4F)
        var anyMagicFound = false
        var firstOffset = -1

        for ((chIdx, chBits) in channels.withIndex()) {
            if (chBits.size < (ProtocolConstants.HEADER_SIZE_BYTES * 8)) continue

            // Each quadrant has packet starting at bit 0. Allow minor optical jitter search window up to 32 bits.
            val maxScan = (chBits.size - (ProtocolConstants.HEADER_SIZE_BYTES * 8)).coerceAtMost(32)

            for (bitIdx in 0..maxScan) {
                if (bitIdx + 16 > chBits.size) break

                var window16 = 0
                for (k in 0 until 16) {
                    val bitVal = chBits.getOrNull(bitIdx + k)?.toInt() ?: 0
                    window16 = (window16 shl 1) or bitVal
                }

                if (window16 == magic16) {
                    anyMagicFound = true
                    if (firstOffset == -1) firstOffset = bitIdx

                    // Extract 11-byte header
                    val headerBytes = extractBytes(chBits, bitIdx, ProtocolConstants.HEADER_SIZE_BYTES)
                    val b1 = headerBytes[9].toInt() and 0xFF
                    val b2 = headerBytes[10].toInt() and 0xFF
                    val payloadLength = (b1 shl 8) or b2

                    if (payloadLength <= 0 || payloadLength > 512) {
                        continue
                    }

                    val totalPacketBytes = ProtocolConstants.HEADER_SIZE_BYTES + payloadLength + ProtocolConstants.CRC_SIZE_BYTES
                    val totalPacketBits = totalPacketBytes * 8

                    if (bitIdx + totalPacketBits > chBits.size) {
                        continue
                    }

                    val packetBytes = extractBytes(chBits, bitIdx, totalPacketBytes)
                    try {
                        val packet = packetDecoder.decode(packetBytes)
                        validPackets.add(packet)
                        DiagnosticLogger.log("MIMO_DECODER", "SUCCESS: Decoded Ch=$chIdx, Seq=${packet.sequenceId}, Len=${packet.payload.size}, BitOffset=$bitIdx")
                        break // Clean packet decoded for this quadrant
                    } catch (e: Exception) {
                        crcFailedCount++
                        DiagnosticLogger.log("MIMO_DECODER", "CRC mismatch on Ch=$chIdx at bit $bitIdx: ${e.message}")
                    }
                }
            }
        }

        lastMagicFound = anyMagicFound
        lastMagicBitOffset = firstOffset
        return validPackets
    }

    private fun extractBytes(bits: ByteArray, bitOffset: Int, byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var b = 0
            val base = bitOffset + (i * 8)
            for (bit in 0..7) {
                val bitVal = bits.getOrNull(base + bit)?.toInt() ?: 0
                b = (b shl 1) or bitVal
            }
            result[i] = b.toByte()
        }
        return result
    }

    fun clear() {
        crcFailedCount = 0
        lastMagicFound = false
        lastMagicBitOffset = -1
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/protocol/SequenceManager.kt

```kotlin
package com.example.mimo_optical_receiver.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

class SequenceManager(
    private val totalExpectedPackets: Int,
    val packetSize: Int = 64
) {
    
    val receivedPackets = mutableMapOf<Long, Packet>()
    
    var uniqueCount = 0
        private set
        
    var duplicateCount = 0
        private set

    private val FILE_MAGIC = "FILE".toByteArray(Charsets.US_ASCII)
    private val ZFIL_MAGIC = "ZFIL".toByteArray(Charsets.US_ASCII)

    /**
     * Processes incoming packets, dropping duplicates and storing unique ones.
     */
    fun processPackets(packets: List<Packet>) {
        for (packet in packets) {
            if (receivedPackets.containsKey(packet.sequenceId)) {
                duplicateCount++
            } else {
                receivedPackets[packet.sequenceId] = packet
                uniqueCount++
            }
        }
    }

    /**
     * Returns the completion progress as a float between 0.0 and 1.0.
     */
    fun getProgress(): Float {
        if (totalExpectedPackets <= 0) return 0f
        return (uniqueCount.toFloat() / totalExpectedPackets.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Checks if all expected packets have been received.
     */
    fun isComplete(): Boolean {
        return uniqueCount >= totalExpectedPackets
    }

    fun getMissingSequenceIds(): List<Long> {
        val missing = mutableListOf<Long>()
        for (i in 0 until totalExpectedPackets) {
            val seq = i.toLong()
            if (!receivedPackets.containsKey(seq)) {
                missing.add(seq)
            }
        }
        return missing
    }

    fun getMissingSummary(): String {
        val missing = getMissingSequenceIds()
        if (missing.isEmpty()) return ""
        val top = missing.take(3).map { "#$it" }.joinToString(", ")
        return if (missing.size > 3) "$top (+${missing.size - 3})" else top
    }

    /**
     * Reconstructs the complete file payload.
     * Verifies completion, sorts by sequenceId, parses & strips the 44-byte FileMetadata
     * header from Packet #0, decompresses if ZFIL stream, extracts expected file size,
     * and truncates to the exact original file size.
     */
    fun assembleFileBytes(): ByteArray {
        if (!isComplete()) {
            throw IllegalStateException("Cannot assemble file: Not all packets received.")
        }

        val outputStream = ByteArrayOutputStream()
        val sortedKeys = receivedPackets.keys.sorted()
        var expectedFileSize = -1L
        var isCompressed = false
        
        for ((index, key) in sortedKeys.withIndex()) {
            val packet = receivedPackets[key]
            if (packet != null) {
                var payload = packet.payload
                // PC Packetizer prepends 44-byte FileMetadata ("FILE" or "ZFIL" magic + uint64 file_size + 32B sha256) to Packet #0
                if (index == 0 && payload.size >= 44) {
                    val magic = payload.take(4).toByteArray()
                    if (magic.contentEquals(FILE_MAGIC)) {
                        val sizeBuffer = ByteBuffer.wrap(payload, 4, 8)
                        expectedFileSize = sizeBuffer.long
                        payload = payload.copyOfRange(44, payload.size)
                    } else if (magic.contentEquals(ZFIL_MAGIC)) {
                        isCompressed = true
                        val sizeBuffer = ByteBuffer.wrap(payload, 4, 8)
                        expectedFileSize = sizeBuffer.long
                        payload = payload.copyOfRange(44, payload.size)
                    }
                }
                outputStream.write(payload)
            }
        }
        
        val fullBytes = outputStream.toByteArray()
        val resultBytes = if (isCompressed) {
            decompressZlib(fullBytes)
        } else {
            fullBytes
        }

        return if (expectedFileSize > 0 && resultBytes.size > expectedFileSize) {
            resultBytes.copyOf(expectedFileSize.toInt())
        } else {
            resultBytes
        }
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return data
        } finally {
            inflater.end()
        }
        val decompressed = outputStream.toByteArray()
        return if (decompressed.isNotEmpty()) decompressed else data
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/profiler/DeviceProfiler.kt

```kotlin
package com.example.mimo_optical_receiver.profiler

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier {
    LOW_TIER,
    BALANCED_TIER,
    HIGH_TIER
}

data class DeviceProfile(
    val tier: DeviceTier,
    val totalRamGb: Double,
    val targetWidth: Int,
    val targetHeight: Int
)

object DeviceProfiler {
    fun getProfile(context: Context): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        return when {
            totalRamGb <= 4.0 -> DeviceProfile(DeviceTier.LOW_TIER, totalRamGb, 1280, 720)
            totalRamGb <= 8.0 -> DeviceProfile(DeviceTier.BALANCED_TIER, totalRamGb, 1280, 720)
            else -> DeviceProfile(DeviceTier.HIGH_TIER, totalRamGb, 1920, 1080)
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/util/FileSaver.kt

```kotlin
package com.example.mimo_optical_receiver.util

import android.content.Context
import android.os.Environment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

object FileSaver {

    /**
     * Computes the SHA-256 hexadecimal string of a ByteArray.
     */
    fun computeSha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 of an existing File on disk.
     */
    fun computeFileSha256(file: File): String? {
        return try {
            if (!file.exists() || !file.isFile) return null
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the payload is GZIP compressed (Magic bytes 0x1F, 0x8B).
     */
    fun isGzipCompressed(data: ByteArray): Boolean {
        return data.size >= 2 &&
                (data[0].toInt() and 0xFF == 0x1F) &&
                (data[1].toInt() and 0xFF == 0x8B)
    }

    /**
     * Transparently decompresses GZIP data in background. Returns decompressed bytes,
     * or original data if decompression fails.
     */
    fun decompressIfGzip(data: ByteArray): ByteArray {
        if (!isGzipCompressed(data)) return data
        return try {
            GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
                gzip.readBytes()
            }
        } catch (e: Exception) {
            data
        }
    }

    /**
     * Saves raw byte data to the application's external Downloads directory.
     * Features:
     * 1. Transparent decompression: If data was gzipped in transit and target is not .gz,
     *    automatically decompresses to restore original file.
     * 2. Storage Deduplication: If an existing file with identical content already exists,
     *    reuses the clean path without redundant duplicate disk writes.
     * 3. Collision protection: If same filename exists with different content, avoids overwriting.
     * 
     * @param context The Android context used to resolve the external directory.
     * @param filename The name of the file to be saved.
     * @param rawData The raw contiguous ByteArray payload to write.
     * @return The absolute path of the saved file, or null if an error occurred.
     */
    fun saveFile(context: Context, filename: String, rawData: ByteArray): String? {
        return try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && !downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Transparent decompression if gzipped and filename does not explicitly request .gz
            val isGzFile = filename.endsWith(".gz", ignoreCase = true)
            val finalData = if (!isGzFile && isGzipCompressed(rawData)) {
                decompressIfGzip(rawData)
            } else {
                rawData
            }

            val finalSha256 = computeSha256(finalData)

            // Storage Deduplication Check:
            // Check if exact file with same name and identical SHA-256 already exists
            val primaryFile = File(downloadsDir, filename)
            if (primaryFile.exists() && primaryFile.length() == finalData.size.toLong()) {
                val existingHash = computeFileSha256(primaryFile)
                if (existingHash == finalSha256) {
                    DiagnosticLogger.log("FILE_SAVER", "Deduplication: Identical file already exists on disk: ${primaryFile.name}")
                    return primaryFile.absolutePath
                }
            }

            // Also check all files in downloadsDir for byte deduplication to prevent repeated storage clutter
            downloadsDir?.listFiles()?.forEach { file ->
                if (file.isFile && file.length() == finalData.size.toLong()) {
                    if (computeFileSha256(file) == finalSha256) {
                        DiagnosticLogger.log("FILE_SAVER", "Deduplication: Exact payload already stored as: ${file.name}")
                        return file.absolutePath
                    }
                }
            }

            // Resolve target file name (handle collision if different file has same name)
            var targetFile = primaryFile
            if (targetFile.exists()) {
                val baseName = filename.substringBeforeLast(".")
                val extension = if (filename.contains(".")) ".${filename.substringAfterLast(".")}" else ""
                var counter = 1
                while (targetFile.exists()) {
                    targetFile = File(downloadsDir, "${baseName}_$counter$extension")
                    counter++
                }
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(finalData)
                fos.flush()
            }
            DiagnosticLogger.log("FILE_SAVER", "Saved ${finalData.size} bytes to ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/util/DiagnosticLogger.kt

```kotlin
package com.example.mimo_optical_receiver.util

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

object DiagnosticLogger {
    private const val MAX_LOG_ENTRIES = 500
    private val logBuffer = LinkedList<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp][$tag] $message"
        Log.d(tag, message)
        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeFirst()
        }
    }

    @Synchronized
    fun logFrameDecode(
        frameIndex: Int,
        corners: List<Pair<Double, Double>>,
        minVal: Double,
        maxVal: Double,
        threshold: Double,
        bits: ByteArray,
        foundMagic: Boolean,
        magicBitOffset: Int,
        crcFailures: Int,
        validPackets: Int
    ) {
        val timestamp = dateFormat.format(Date())
        val first64Bits = bits.take(64).joinToString("") { if (it > 0) "1" else "0" }
        
        // Convert first 8 bytes to hex
        val hexBuilder = StringBuilder()
        for (byteIdx in 0 until minOf(8, bits.size / 8)) {
            var b = 0
            for (bit in 0 until 8) {
                val idx = byteIdx * 8 + bit
                val bitVal = if (idx < bits.size) bits[idx].toInt() and 1 else 0
                b = (b shl 1) or bitVal
            }
            hexBuilder.append(String.format("%02X ", b))
        }

        val entry = buildString {
            append("[$timestamp][FRAME #$frameIndex] ")
            append("Corners: ${corners.size} | ")
            append(String.format(Locale.US, "Range: [%.1f - %.1f], Thresh: %.1f | ", minVal, maxVal, threshold))
            append("First8BytesHex: [${hexBuilder.toString().trim()}] | ")
            append("MagicFound: $foundMagic (offset=$magicBitOffset) | ")
            append("Stats(Valid=$validPackets, CRC_Fail=$crcFailures)\n")
            append("   Bits[0..63]: $first64Bits")
        }

        logBuffer.add(entry)
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeFirst()
        }
    }

    @Synchronized
    fun getLogCount(): Int = logBuffer.size

    @Synchronized
    fun exportToFile(context: Context): String? {
        return try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, "MIMO_Receiver_Telemetry.log")
            PrintWriter(FileWriter(file, false)).use { writer ->
                writer.println("=================================================================")
                writer.println("        MIMO OPTICAL RECEIVER - LIVE TELEMETRY REPORT            ")
                writer.println("  Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                writer.println("  Total Log Entries: ${logBuffer.size}")
                writer.println("=================================================================")
                writer.println()
                for (line in logBuffer) {
                    writer.println(line)
                }
                writer.println()
                writer.println("========================== END OF LOG ==========================")
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun clear() {
        logBuffer.clear()
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/util/ReceiverSettingsManager.kt

```kotlin
package com.example.mimo_optical_receiver.util

import android.content.Context
import android.content.SharedPreferences

object ReceiverSettingsManager {
    private const val PREFS_NAME = "mimo_receiver_settings"

    const val KEY_MIMO_MODE = "pref_mimo_mode"
    const val KEY_SYNC_CONFIDENCE = "pref_sync_confidence"
    const val KEY_AUTO_OPEN_FILE = "pref_auto_open_file"
    const val KEY_AUTO_EXPORT_LOGS = "pref_auto_export_logs"
    const val KEY_VERBOSE_HUD = "pref_verbose_hud"
    const val KEY_CORE_INSET = "pref_core_inset"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMimoMode(context: Context): String {
        return getPrefs(context).getString(KEY_MIMO_MODE, "AUTO") ?: "AUTO"
    }

    fun setMimoMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_MIMO_MODE, mode).apply()
    }

    fun getSyncConfidence(context: Context): Int {
        return getPrefs(context).getInt(KEY_SYNC_CONFIDENCE, 80)
    }

    fun setSyncConfidence(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_SYNC_CONFIDENCE, value).apply()
    }

    fun isAutoOpenFile(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_OPEN_FILE, false)
    }

    fun setAutoOpenFile(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_OPEN_FILE, enabled).apply()
    }

    fun isAutoExportLogs(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_EXPORT_LOGS, true)
    }

    fun setAutoExportLogs(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_EXPORT_LOGS, enabled).apply()
    }

    fun isVerboseHud(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VERBOSE_HUD, true)
    }

    fun setVerboseHud(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VERBOSE_HUD, enabled).apply()
    }

    fun getCoreInset(context: Context): Int {
        return getPrefs(context).getInt(KEY_CORE_INSET, 25)
    }

    fun setCoreInset(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_CORE_INSET, value).apply()
    }
}

```

---


### File: app/src/main/java/com/example/mimo_optical_receiver/util/TransferHistoryManager.kt

```kotlin
package com.example.mimo_optical_receiver.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class TransferRecord(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val savedPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mimoMode: String = "1x1",
    val sessionId: String = "",
    val packetCount: Int = 0
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("fileName", fileName)
            put("fileSizeBytes", fileSizeBytes)
            put("savedPath", savedPath)
            put("timestamp", timestamp)
            put("mimoMode", mimoMode)
            put("sessionId", sessionId)
            put("packetCount", packetCount)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): TransferRecord {
            return TransferRecord(
                id = json.optString("id", UUID.randomUUID().toString()),
                fileName = json.optString("fileName", "unknown.bin"),
                fileSizeBytes = json.optLong("fileSizeBytes", 0L),
                savedPath = json.optString("savedPath", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                mimoMode = json.optString("mimoMode", "1x1"),
                sessionId = json.optString("sessionId", ""),
                packetCount = json.optInt("packetCount", 0)
            )
        }
    }
}

object TransferHistoryManager {
    private const val PREFS_NAME = "mimo_transfer_history"
    private const val KEY_HISTORY = "history_records"

    fun getHistory(context: Context): List<TransferRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<TransferRecord>()
            for (i in 0 until jsonArray.length()) {
                list.add(TransferRecord.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            // Most recent first
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun addRecord(context: Context, record: TransferRecord) {
        val currentList = getHistory(context).toMutableList()
        // Deduplicate by same savedPath or identical file name and size
        currentList.removeAll { it.savedPath == record.savedPath || (it.fileName == record.fileName && it.fileSizeBytes == record.fileSizeBytes) }
        currentList.add(0, record)

        // Keep maximum 50 recent records
        val trimmed = if (currentList.size > 50) currentList.subList(0, 50) else currentList

        val jsonArray = JSONArray()
        for (item in trimmed) {
            jsonArray.put(item.toJsonObject())
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, jsonArray.toString())
            .apply()
    }

    fun deleteRecord(context: Context, id: String) {
        val currentList = getHistory(context).filterNot { it.id == id }
        val jsonArray = JSONArray()
        for (item in currentList) {
            jsonArray.put(item.toJsonObject())
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, jsonArray.toString())
            .apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    fun openFile(context: Context, record: TransferRecord) {
        val file = File(record.savedPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk: ${record.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension.lowercase(Locale.getDefault())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, record: TransferRecord) {
        val file = File(record.savedPath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist on disk", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension.lowercase(Locale.getDefault())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${record.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

```

---


