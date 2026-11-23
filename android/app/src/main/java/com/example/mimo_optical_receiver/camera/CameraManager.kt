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

    // Reactively push mode state changes and handle clean session resets
    LaunchedEffect(analyzerMode, sessionTotalPackets) {
        analyzer.setMode(analyzerMode)
        if (analyzerMode == FrameAnalyzer.AnalyzerMode.SCANNING_QR && (sessionTotalPackets == null || sessionTotalPackets == 0)) {
            analyzer.resetSession()
        }
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

                        // Phase 5: Ambient Illumination Adaptation & Exposure Clamping
                        // Clamp exposure to -1.0 EV to prevent bright OLED/LCD screens from blooming/overexposing in dark rooms
                        try {
                            val expState = camera.cameraInfo.exposureState
                            if (expState.isExposureCompensationSupported) {
                                val range = expState.exposureCompensationRange
                                val step = expState.exposureCompensationStep.toDouble()
                                if (step > 0.0) {
                                    val targetIndex = (-1.0 / step).toInt().coerceIn(range.lower, range.upper)
                                    camera.cameraControl.setExposureCompensationIndex(targetIndex)
                                }
                            }
                        } catch (_: Exception) {
                            // Non-fatal if exposure compensation is unsupported on specific hardware
                        }

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
