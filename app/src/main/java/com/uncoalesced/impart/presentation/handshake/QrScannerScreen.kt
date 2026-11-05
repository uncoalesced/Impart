// Engineered by uncoalesced
package com.uncoalesced.impart.presentation.handshake

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uncoalesced.impart.core.util.QrFrameDecoder
import com.uncoalesced.impart.presentation.theme.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScannerScreen"

// scanGeneration re-arms the single-shot guard: the parent bumps it when a scan is rejected
// so the user can try again without leaving the tab.
@Composable
fun QrScannerScreen(
    scanGeneration: Int,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ImpartGround)
    ) {
        if (cameraGranted) {
            CameraPreview(scanGeneration = scanGeneration, onQrScanned = onQrScanned)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CAMERA ACCESS REQUIRED",
                    color = ImpartTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scanning a handshake QR is the only way to trust another node.",
                    color = ImpartTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImpartAccent,
                        contentColor = ImpartGround
                    )
                ) {
                    Text("GRANT CAMERA ACCESS", fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ImpartGround.copy(alpha = 0.85f))
                .padding(24.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SCAN NODE",
                color = ImpartTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ALIGN QR CODE WITHIN FRAME",
                color = ImpartTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun CameraPreview(scanGeneration: Int, onQrScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScanned by rememberUpdatedState(onQrScanned)

    val fired = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    // ZXing on the luminance plane, on this phone. No ML Kit, no Play services (T21).
    val decoder = remember { QrFrameDecoder() }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(scanGeneration) { fired.set(false) }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            if (cameraProviderFuture.isDone) {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.w(TAG, "Camera unbind failed on dispose", e)
                }
            }
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                if (disposed.get()) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    processImageProxy(decoder, imageProxy) { value ->
                        if (fired.compareAndSet(false, true)) latestOnScanned(value)
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

// Runs on the single analyzer thread. Only the Y (luminance) plane is read; the frame is closed
// on every path so CameraX keeps delivering.
private fun processImageProxy(
    decoder: QrFrameDecoder,
    imageProxy: ImageProxy,
    onQrScanned: (String) -> Unit
) {
    try {
        val plane = imageProxy.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val luminance = QrFrameDecoder.packPlane(bytes, imageProxy.width, imageProxy.height, plane.rowStride, plane.pixelStride)
        decoder.decode(luminance, imageProxy.width, imageProxy.height)?.let(onQrScanned)
    } catch (e: Exception) {
        Log.w(TAG, "QR analysis failed for a frame", e)
    } finally {
        imageProxy.close()
    }
}
