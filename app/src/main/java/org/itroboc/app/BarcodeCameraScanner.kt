package org.itroboc.app

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
internal fun BarcodeCameraScanner(
    consumeScanRequest: () -> Boolean,
    frameDecoder: CameraFrameDecoder,
    onScanProcessed: (CameraScanOutcome) -> Unit,
    modifier: Modifier = Modifier,
    guideSpec: CameraScanGuideSpec = barcodeScanGuideSpec,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val currentConsumeScanRequest by rememberUpdatedState(consumeScanRequest)
    val currentOnScanProcessed by rememberUpdatedState(onScanProcessed)

    DisposableEffect(
        lifecycleOwner,
        cameraProviderFuture,
        analysisExecutor,
        frameDecoder,
        guideSpec,
    ) {
        var reusableRoiPixels = ByteArray(0)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                imageProxy.use {
                    if (!currentConsumeScanRequest()) {
                        return@setAnalyzer
                    }

                    val roi = centeredBarcodeRoi(
                        imageWidth = imageProxy.width,
                        imageHeight = imageProxy.height,
                        guideSpec = guideSpec,
                    )
                    val requiredSize = roi.width * roi.height
                    if (reusableRoiPixels.size != requiredSize) {
                        reusableRoiPixels = ByteArray(requiredSize)
                    }

                    val scanOutcome = frameDecoder.decode(
                        imageProxy = imageProxy,
                        roi = roi,
                        reusablePixels = reusableRoiPixels,
                    )
                    mainExecutor.execute {
                        currentOnScanProcessed(scanOutcome)
                    }
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (error: Exception) {
                Log.e(CAMERA_PREVIEW_LOG_TAG, "Use case binding failed", error)
            }
        }, mainExecutor)

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }.onFailure { error ->
                Log.w(CAMERA_PREVIEW_LOG_TAG, "Failed to unbind camera on dispose", error)
            }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        BarcodeGuideOverlay(guideSpec = guideSpec)
    }
}

@Composable
private fun BarcodeGuideOverlay(guideSpec: CameraScanGuideSpec) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val guideBounds = centeredGuideRectBounds(
            containerWidth = size.width,
            containerHeight = size.height,
            guideSpec = guideSpec,
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(guideBounds.left, guideBounds.top),
            size = Size(guideBounds.width, guideBounds.height),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private const val CAMERA_PREVIEW_LOG_TAG = "CameraPreview"
