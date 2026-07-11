package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel
import com.example.backup.EncryptedBackupManager
import com.example.ui.components.SecureTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(viewModel: com.example.ui.viewmodel.IdentityViewModel) {
    val identityPublicKey by viewModel.identityPublicKey.collectAsState()
    val identityPrivateKey by viewModel.identityPrivateKey.collectAsState()
    val signedPreKey by viewModel.signedPreKey.collectAsState()
    
    val deviceId by viewModel.deviceId.collectAsState()
    val loginEmail by viewModel.loginEmail.collectAsState()
    
    val userId = loginEmail.substringBefore("@")
    val context = LocalContext.current
    var showKeys by remember { mutableStateOf(false) }

    // Multi-device
    val linkedDevices by viewModel.multiDeviceManager.linkedDevices.collectAsState()

    // Backup states
    var passphraseInput by remember { mutableStateOf("") }
    var backupStatusMsg by remember { mutableStateOf<String?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.multiDeviceManager.loadDevices()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Profile Avatar
        item {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(PhantomSecondary.copy(alpha = 0.1f), shape = CircleShape)
                    .border(2.dp, PhantomSecondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile Avatar",
                    tint = PhantomSecondary,
                    modifier = Modifier.size(96.dp)
                )
            }
        }

        // Profile Details Header
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "@$userId",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PhantomTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = loginEmail,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PhantomTextSecondary
                )
            }
        }

        // Action: Share Profile Link
        item {
            Button(
                onClick = {
                    val shareIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "Connect with me on Phantom (Secure E2EE Messenger)! My User ID is: @$userId. Share Link: https://phantom-pu9t.onrender.com/invite/$userId"
                        )
                    }
                    val chooser = android.content.Intent.createChooser(shareIntent, "Share Profile Link")
                    context.startActivity(chooser)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SHARE PROFILE LINK", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Card: Device & Session Info
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Device Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    
                    ProfileDetailField(label = "User ID", value = "@$userId")
                    ProfileDetailField(label = "Secure Handshake Server", value = "phantom-pu9t.onrender.com")
                    ProfileDetailField(label = "Hardware Device ID", value = deviceId)
                }
            }
        }

        // Card: Cloud Backups & Zero-Knowledge Restore
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Zero-Knowledge Backups",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Text(
                        text = "Encrypt your active session, credentials, and local database keys via Argon2id PBKDF. The server only sees opaque ciphertext.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { showBackupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSurfaceVariant, contentColor = PhantomSecondary),
                        border = BorderStroke(1.dp, PhantomBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PhantomSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BACKUP / RESTORE DATABASE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card: Multi-Device linked auxiliary nodes list
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Linked Secondary Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )

                    if (linkedDevices.isEmpty()) {
                        Text(
                            text = "No linked secondary devices configured for this user identity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomTextSecondary
                        )
                    } else {
                        linkedDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.deviceName, fontWeight = FontWeight.Bold, color = PhantomTextPrimary, fontSize = 13.sp)
                                    Text("Device ID: ${device.deviceId.take(12)}...", style = MaterialTheme.typography.labelSmall, color = PhantomTextSecondary)
                                }
                                if (!device.isRevoked) {
                                    IconButton(
                                        onClick = {
                                            viewModel.viewModelScope.launch {
                                                viewModel.multiDeviceManager.revokeDevice(device.deviceId)
                                                viewModel.addLog("SYS", "Revoked secondary device ${device.deviceName}")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Revoke device", tint = PhantomError)
                                    }
                                } else {
                                    Text("Revoked", color = PhantomTextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expander Card: Cryptographic Credentials (Hidden by default to keep UI simple)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showKeys = !showKeys },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced Cryptographic Keys",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PhantomTextPrimary
                        )
                        Icon(
                            imageVector = if (showKeys) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = PhantomTextSecondary
                        )
                    }

                    if (showKeys) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            KeyDisplayField(title = "Identity Public Key (IK_pub)", key = identityPublicKey, isPrivate = false)
                            KeyDisplayField(title = "Signed Pre-Key (SPK)", key = signedPreKey, isPrivate = false)
                            KeyDisplayField(title = "Identity Private Key (IK_priv)", key = identityPrivateKey, isPrivate = true)
                        }
                    }
                }
            }
        }
    }

    // Cloud Backup / Restore Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false; backupStatusMsg = null },
            title = { Text("Database Sync (Cloud Backup)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Securely backup or restore E2EE sessions. A robust passphrase is required for encryption/decryption keys derivation.")
                    
                    SecureTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = "Passphrase",
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    backupStatusMsg?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = PhantomSecondary)
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Backup Trigger
                        Button(
                            onClick = {
                                if (passphraseInput.length >= 8) {
                                    viewModel.viewModelScope.launch {
                                        backupStatusMsg = "Deriving backup keys via PBKDF2..."
                                        val backupBytes = EncryptedBackupManager.createBackup(viewModel.repository)
                                        val encrypted = EncryptedBackupManager.encryptBackup(backupBytes, passphraseInput)
                                        val base64Backup = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
                                        
                                        // Send backup as POST JSON body (not query params) through TLS-pinned client
                                        val jsonBody = JSONObject().put("backup_data", base64Backup).toString()
                                        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
                                        val request = Request.Builder()
                                            .url(com.example.network.NetworkConfig.getServerUrl("/api/backup/upload"))
                                            .post(requestBody)
                                            .addHeader("Authorization", "Bearer ${viewModel.activeSessionToken.value}")
                                            .build()

                                        val success = withContext(Dispatchers.IO) {
                                            try {
                                                viewModel.client.newCall(request).execute().use { response ->
                                                    response.isSuccessful
                                                }
                                            } catch (e: Exception) {
                                                false
                                            }
                                        }

                                        if (success) {
                                            backupStatusMsg = "Backup successfully uploaded!"
                                            viewModel.addLog("SEC", "Secure identity backup stored in cloud.")
                                        } else {
                                            backupStatusMsg = "Upload failed. Server offline."
                                        }
                                    }
                                }
                            },
                            enabled = passphraseInput.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary)
                        ) {
                            Text("CLOUD BACKUP")
                        }

                        // Restore Trigger
                        Button(
                            onClick = {
                                if (passphraseInput.length >= 8) {
                                    viewModel.viewModelScope.launch {
                                        backupStatusMsg = "Fetching backup files from server..."
                                        val url = com.example.network.NetworkConfig.getServerUrl("/api/backup/download")
                                        val request = Request.Builder()
                                            .url(url)
                                            .addHeader("Authorization", "Bearer ${viewModel.activeSessionToken.value}")
                                            .build()

                                        val base64Data = withContext(Dispatchers.IO) {
                                            try {
                                                viewModel.client.newCall(request).execute().use { response ->
                                                    if (response.isSuccessful) {
                                                        val json = JSONObject(response.body?.string() ?: "")
                                                        json.getString("backupData")
                                                    } else null
                                                }
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }

                                        if (base64Data != null) {
                                            try {
                                                val encrypted = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                                val decrypted = EncryptedBackupManager.decryptBackup(encrypted, passphraseInput)
                                                EncryptedBackupManager.restoreBackup(viewModel.repository, decrypted)
                                                backupStatusMsg = "Restore completed! Please restart app."
                                                viewModel.addLog("SEC", "E2EE session restored from cloud.")
                                            } catch (e: Exception) {
                                                backupStatusMsg = "Decryption failed (Wrong passphrase)."
                                            }
                                        } else {
                                            backupStatusMsg = "No cloud backup found for user."
                                        }
                                    }
                                }
                            },
                            enabled = passphraseInput.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary, contentColor = PhantomSecondary)
                        ) {
                            Text("CLOUD RESTORE")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Export File Trigger
                        Button(
                            onClick = {
                                if (passphraseInput.length >= 8) {
                                    viewModel.viewModelScope.launch {
                                        try {
                                            val backupBytes = EncryptedBackupManager.createBackup(viewModel.repository)
                                            val encrypted = EncryptedBackupManager.encryptBackup(backupBytes, passphraseInput)
                                            val file = java.io.File(context.getExternalFilesDir(null), "phantom_backup.bin")
                                            file.writeBytes(encrypted)
                                            backupStatusMsg = "Exported: ${file.name}"
                                            viewModel.addLog("SEC", "E2EE session exported locally to ${file.name}")
                                        } catch (e: Exception) {
                                            backupStatusMsg = "Export failed."
                                        }
                                    }
                                }
                            },
                            enabled = passphraseInput.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary)
                        ) {
                            Text("EXPORT FILE")
                        }

                        // Import File Trigger
                        Button(
                            onClick = {
                                if (passphraseInput.length >= 8) {
                                    viewModel.viewModelScope.launch {
                                        val file = java.io.File(context.getExternalFilesDir(null), "phantom_backup.bin")
                                        if (file.exists()) {
                                            try {
                                                val encrypted = file.readBytes()
                                                val decrypted = EncryptedBackupManager.decryptBackup(encrypted, passphraseInput)
                                                EncryptedBackupManager.restoreBackup(viewModel.repository, decrypted)
                                                backupStatusMsg = "Imported! Please restart app."
                                                viewModel.addLog("SEC", "E2EE session imported from local file.")
                                            } catch (e: Exception) {
                                                backupStatusMsg = "Decryption failed (Wrong passphrase)."
                                            }
                                        } else {
                                            backupStatusMsg = "File 'phantom_backup.bin' not found."
                                        }
                                    }
                                }
                            },
                            enabled = passphraseInput.length >= 8,
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary, contentColor = PhantomSecondary)
                        ) {
                            Text("IMPORT FILE")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false; backupStatusMsg = null; passphraseInput = "" }) {
                    Text("CLOSE")
                }
            }
        )
    }
}

@Composable
fun ProfileDetailField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PhantomTextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = PhantomTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun KeyDisplayField(title: String, key: String, isPrivate: Boolean) {
    val clipboardManager = LocalClipboardManager.current
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = PhantomTextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhantomTerminalBg, shape = RoundedCornerShape(8.dp))
                .border(1.dp, PhantomBorder, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                    contentDescription = null,
                    tint = if (isPrivate) PhantomError else PhantomTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPrivate) "••••••••••••••••••••••••••••••••" else key,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = PhantomTextPrimary
                )
            }
            
            if (!isPrivate) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = PhantomTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { clipboardManager.setText(AnnotatedString(key)) }
                )
            }
        }
    }
}
