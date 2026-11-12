$files = @(
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/example/mimo_optical_receiver/MainActivity.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/ui/AppNavigation.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/ui/ReceiverUiState.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/ui/screens/HomeScreen.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/ui/screens/SettingsScreen.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/ui/screens/ScannerScreen.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/CameraManager.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/FrameAnalyzer.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/OpticalFeedbackController.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/QrMetadataParser.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/ScreenDetector.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/PerspectiveCorrector.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/RegionDetector.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/SyncDetector.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/camera/SymbolDecoder.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/SessionMetadata.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/ProtocolConstants.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/Packet.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/PacketDecoder.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/PacketAssembler.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/protocol/SequenceManager.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/profiler/DeviceProfiler.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/util/FileSaver.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/util/DiagnosticLogger.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/util/ReceiverSettingsManager.kt",
    "app/src/main/java/com/example/mimo_optical_receiver/util/TransferHistoryManager.kt"
)

$outPath = "C:/Users/pratik/AndroidStudioProjects/MIMO_Optical_Receiver/SOURCE_CODE_REFERENCE.md"
Clear-Content -Path $outPath -ErrorAction SilentlyContinue

$header = "# MIMO Optical Receiver - Complete Source Code Reference`n`nThis file contains the complete, up-to-date source code for all project files, organized by file path.`n`n---`n`n"
Set-Content -Path $outPath -Value $header -Encoding utf8

foreach ($f in $files) {
    $absPath = "C:/Users/pratik/AndroidStudioProjects/MIMO_Optical_Receiver/" + $f
    if (Test-Path $absPath) {
        $content = Get-Content $absPath -Raw
        $ext = [System.IO.Path]::GetExtension($f).TrimStart('.')
        if ($ext -eq "kts") { $ext = "kotlin" }
        if ($ext -eq "kt") { $ext = "kotlin" }
        if ($ext -eq "xml") { $ext = "xml" }

        $block = "### File: " + $f + "`n`n``````" + $ext + "`n" + $content + "`n```````n`n---`n`n"
        Add-Content -Path $outPath -Value $block -Encoding utf8
    }
}
