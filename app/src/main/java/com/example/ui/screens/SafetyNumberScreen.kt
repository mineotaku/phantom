package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.crypto.SafetyNumbers
import com.example.ui.models.ChatUser
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyNumberScreen(
    viewModel: ChatViewModel,
    partner: ChatUser,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val identityPublicKey by viewModel.identityPublicKey.collectAsState()

    val localPublicKeyBytes = remember(identityPublicKey) {
        android.util.Base64.decode(identityPublicKey, android.util.Base64.DEFAULT)
    }
    val partnerPublicKeyBytes = remember(partner.publicKey) {
        android.util.Base64.decode(partner.publicKey, android.util.Base64.DEFAULT)
    }

    val safetyNumber = remember(localPublicKeyBytes, partnerPublicKeyBytes) {
        SafetyNumbers.computeSafetyNumber(localPublicKeyBytes, partnerPublicKeyBytes)
    }

    var isScanning by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isScanning = true
        } else {
            Toast.makeText(context, "Camera permission is required to scan safety numbers", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Number Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PhantomSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PhantomSurface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PhantomBg)
                .padding(paddingValues)
        ) {
            if (isScanning) {
                CameraScannerView(
                    onCodeScanned = { scannedCode ->
                        isScanning = false
                        if (SafetyNumbers.verifySafetyNumber(scannedCode, safetyNumber)) {
                            isVerified = true
                            verificationError = false
                            viewModel.addLog("SEC", "Safety number verified successfully with ${partner.name}")
                        } else {
                            verificationError = true
                            isVerified = false
                            viewModel.addLog("WARNING", "Safety number mismatch detected for ${partner.name}!")
                        }
                    },
                    onClose = { isScanning = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Text(
                        text = "To verify secure encryption with ${partner.name}, compare the numbers below or scan their QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    // Render beautiful deterministic Canvas QR Code
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, shape = RoundedCornerShape(12.dp))
                            .border(1.dp, PhantomBorder, shape = RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DeterministicQrCanvas(safetyNumber = safetyNumber)
                    }

                    // Numeric fingerprint blocks
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PhantomBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Fingerprint Number",
                                style = MaterialTheme.typography.labelLarge,
                                color = PhantomTextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = SafetyNumbers.formatForDisplay(safetyNumber),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PhantomTextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Short Authentication String (SAS) Words",
                                style = MaterialTheme.typography.labelLarge,
                                color = PhantomTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val sasWords = remember(safetyNumber) {
                                SafetyNumbers.computeSasWords(safetyNumber)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                sasWords.forEach { word ->
                                    Box(
                                        modifier = Modifier
                                            .background(PhantomSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp))
                                            .border(1.dp, PhantomSecondary.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = word.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = PhantomSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Verification status info panel
                    when {
                        isVerified -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                               ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                    Text(
                                        text = "Identity verified! Connection is mathematically secure.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        verificationError -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = PhantomError)
                                    Text(
                                        text = "WARNING: Safety number mismatch! The channel key may have changed or is being intercepted.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PhantomError,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            val permission = Manifest.permission.CAMERA
                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                isScanning = true
                            } else {
                                cameraPermissionLauncher.launch(permission)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SCAN PARTNER QR CODE", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DeterministicQrCanvas(safetyNumber: String) {
    val qrText = "phantom_sn:$safetyNumber"
    val bitMatrix = remember(safetyNumber) {
        try {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                qrText,
                com.google.zxing.BarcodeFormat.QR_CODE,
                256,
                256,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    if (bitMatrix != null) {
        val width = bitMatrix.width
        val height = bitMatrix.height
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / width
            val cellH = size.height / height
            for (row in 0 until height) {
                for (col in 0 until width) {
                    if (bitMatrix.get(col, row)) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * cellW, row * cellH),
                            size = Size(cellW, cellH)
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("QR Code generation failed")
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFinderPattern(
    x: Float,
    y: Float,
    cellW: Float,
    cellH: Float
) {
    // 7x7 outer solid black block
    drawRect(Color.Black, Offset(x, y), Size(7 * cellW, 7 * cellH))
    // 5x5 inner white block
    drawRect(Color.White, Offset(x + cellW, y + cellH), Size(5 * cellW, 5 * cellH))
    // 3x3 inner solid black block
    drawRect(Color.Black, Offset(x + 2 * cellW, y + 2 * cellH), Size(3 * cellW, 3 * cellH))
}

@Composable
fun CameraScannerView(
    onCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val scanner = BarcodeScanning.getClient()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, scanner, onCodeScanned)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScannerView", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Center the safety QR code in the viewfinder",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Transparent viewfinder overlay block
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(2.dp, Color.Green, shape = RoundedCornerShape(12.dp))
            )

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCodeScanned: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val value = barcode.rawValue
                    if (value != null && value.startsWith("phantom_sn:")) {
                        onCodeScanned(value)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // scan fail log
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
