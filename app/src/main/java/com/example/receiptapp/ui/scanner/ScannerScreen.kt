package com.example.receiptapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Kamera-Screen: CameraX-Preview + Auslöser. Nach Aufnahme wird das Bitmap
 * an den ViewModel gegeben, der OCR + KI-Parsing + Speicherung anstößt.
 */
@Composable
fun ScannerScreen(viewModel: ScannerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewWithCapture(
                onCaptured = { bitmap -> viewModel.onPhotoCaptured(bitmap) },
                enabled = uiState is ScannerUiState.Idle
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Kamerazugriff wird benötigt, um Belege zu scannen.")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Zugriff erlauben")
                }
            }
        }

        when (val state = uiState) {
            is ScannerUiState.Processing -> ProcessingOverlay()
            is ScannerUiState.Success -> ResultBanner("Beleg gespeichert ✅") { viewModel.reset() }
            is ScannerUiState.NeedsReview -> ResultBanner(
                "Beleg gespeichert, aber KI ist sich unsicher (${(state.confidence * 100).toInt()}%). " +
                    "Bitte im Detail-Screen prüfen."
            ) { viewModel.reset() }
            is ScannerUiState.Error -> ResultBanner("Fehler: ${state.message}") { viewModel.reset() }
            ScannerUiState.Idle -> Unit
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Analysiere Beleg…", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ResultBanner(message: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(message)
            Button(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text("OK") }
        }
    }
}

@Composable
private fun CameraPreviewWithCapture(onCaptured: (android.graphics.Bitmap) -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        FloatingActionButton(
            onClick = { if (enabled) captureBitmap(context, imageCapture, onCaptured) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Beleg fotografieren")
        }
    }
}

private fun captureBitmap(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onCaptured: (android.graphics.Bitmap) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                val bitmap = image.toBitmap() // Erweiterung, siehe ImageProxyExt.kt
                image.close()
                onCaptured(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}
