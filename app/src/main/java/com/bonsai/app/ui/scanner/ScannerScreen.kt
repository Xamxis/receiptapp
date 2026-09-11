package com.bonsai.app.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bonsai.app.ui.pro.ProPaywallSheet
import com.bonsai.app.ui.pro.ProViewModel

/**
 * Scanner: Kamera-Vorschau mit Ausrichtungsrahmen, danach OCR + KI-Auswertung.
 *
 * Hinweis: Auf Emulatoren ohne echte Kamera (z. B. Browser-Testdienste) schlägt
 * die Aufnahme fehl – das ist erwartetes Verhalten, kein App-Fehler. Der Screen
 * zeigt dann eine erklärende Meldung statt eines Absturzes.
 */
@Composable
fun ScannerScreen(
    onScanned: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val access by proViewModel.access.collectAsState()
    val context = LocalContext.current
    var showPaywall by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreview(
                enabled = uiState is ScannerUiState.Idle,
                onCaptured = viewModel::onPhotoCaptured,
                onError = viewModel::onCaptureError
            )
        } else {
            PermissionPrompt(
                wasDenied = permissionRequested,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        when (val state = uiState) {
            ScannerUiState.Idle -> Unit
            ScannerUiState.Processing -> ProcessingOverlay()
            is ScannerUiState.Success -> ResultSheet(
                title = "Beleg gespeichert",
                message = "${state.itemCount} Posten erkannt.",
                primaryLabel = "Ansehen",
                onPrimary = { viewModel.reset(); onScanned() },
                onDismiss = viewModel::reset
            )
            is ScannerUiState.NeedsReview -> ResultSheet(
                title = "Gespeichert – bitte prüfen",
                message = "${state.itemCount} Posten erkannt, aber die Erkennung war unsicher " +
                    "(${(state.confidence * 100).toInt()} %). Schau die Werte kurz durch.",
                primaryLabel = "Prüfen",
                onPrimary = { viewModel.reset(); onScanned() },
                onDismiss = viewModel::reset
            )
            is ScannerUiState.Error -> ResultSheet(
                title = "Das hat nicht geklappt",
                message = state.message,
                primaryLabel = "Nochmal versuchen",
                onPrimary = viewModel::reset,
                onDismiss = viewModel::reset
            )
            ScannerUiState.LimitReached -> ResultSheet(
                title = "Kontingent aufgebraucht",
                message = "In der Gratis-Version kannst du ${access.scanLimit} Belege pro Monat scannen. " +
                    "Mit Bonsai Pro scannst du unbegrenzt weiter.",
                primaryLabel = "Pro ansehen",
                onPrimary = { showPaywall = true },
                onDismiss = viewModel::reset
            )
        }

        // Restliche Gratis-Scans dezent einblenden
        if (!access.isPro && uiState is ScannerUiState.Idle) {
            Text(
                "Noch ${access.scansLeft} von ${access.scanLimit} Scans diesen Monat",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            )
        }

        if (showPaywall) {
            ProPaywallSheet(
                reason = "Du hast dein Gratis-Kontingent für diesen Monat aufgebraucht.",
                onDismiss = {
                    showPaywall = false
                    viewModel.reset()
                }
            )
        }
    }
}

@Composable
private fun CameraPreview(
    enabled: Boolean,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        onError(
                            "Die Kamera konnte nicht gestartet werden. Auf Emulatoren ohne " +
                                "echte Kamera ist das normal – auf einem richtigen Handy sollte es klappen."
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Ausrichtungsrahmen als Hilfe beim Fotografieren
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.62f)
                .align(Alignment.Center)
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
        )

        Text(
            "Beleg flach hinlegen und vollständig im Rahmen halten",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 32.dp, end = 32.dp)
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                .clickable(enabled = enabled) {
                    capture(context, imageCapture, onCaptured, onError)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Beleg fotografieren",
                tint = Color.Black,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

private fun capture(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (android.graphics.Bitmap) -> Unit,
    reportError: (String) -> Unit
) {
    try {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toRotatedBitmap()
                        onCaptured(bitmap)
                    } catch (e: Exception) {
                        reportError("Das Foto konnte nicht verarbeitet werden.")
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    reportError("Aufnahme fehlgeschlagen: ${exception.message ?: "unbekannter Fehler"}")
                }
            }
        )
    } catch (e: Exception) {
        reportError("Die Kamera ist auf diesem Gerät nicht verfügbar.")
    }
}

@Composable
private fun PermissionPrompt(wasDenied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Kamerazugriff nötig",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (wasDenied)
                "Ohne Kamerazugriff kann Bonsai keine Belege scannen. Du kannst die " +
                    "Berechtigung in den Android-Einstellungen unter Apps › Bonsai › Berechtigungen erlauben."
            else
                "Bonsai braucht die Kamera, um deine Kassenzettel zu fotografieren. " +
                    "Die Fotos bleiben auf deinem Gerät.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(14.dp)) {
            Text("Zugriff erlauben")
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("Beleg wird ausgewertet…", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Text erkennen und Posten zuordnen",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResultSheet(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Weiter scannen") }
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(primaryLabel) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
