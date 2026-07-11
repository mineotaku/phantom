package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun StorageScreen(viewModel: PhantomViewModel) {
    val sqlCipherLocked by viewModel.sqlCipherLocked.collectAsState()
    val databaseKeyHex by viewModel.databaseKeyHex.collectAsState()
    val selectedMediaType by viewModel.selectedMediaType.collectAsState()
    val isEncryptingMedia by viewModel.isEncryptingMedia.collectAsState()
    val encryptedMediaMetadata by viewModel.encryptedMediaMetadata.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Room Database Encryption
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = PhantomSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Room Encrypted Database",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PhantomSecondary
                                )
                                Text(
                                    text = if (sqlCipherLocked) "Locked & Encrypted (SQLCipher)" else "Decrypted & Connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sqlCipherLocked) PhantomError else Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Switch(
                            checked = !sqlCipherLocked,
                            onCheckedChange = { viewModel.toggleDbLock() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PhantomPrimary,
                                checkedTrackColor = PhantomSecondary
                            ),
                            modifier = Modifier.testTag("sqlcipher_lock_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The application persistence container stores chats, identities, and media metadata using Room database encrypted transparently with SQLCipher. All data is unreadable from disk without deriving the Keystore-wrapped master AES database key.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    KeyDisplayField(title = "Resolved 256-Bit Master AES Database Key", key = if (sqlCipherLocked) "••••••••••••••••••••••••••••••••" else databaseKeyHex, isPrivate = true)
                }
            }
        }

        // Card 2: Hardware Media Attachment Encrypter
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Secure Media Attachment Encrypter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Encrypt local binary assets with dynamic hardware-backed ephemeral keys before uploading to the message relay pipeline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Select Asset Payload Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = PhantomTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Media Type Selector Row
                    val mediaTypes = listOf("Photo", "Voice Note", "Video File")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(mediaTypes) { type ->
                            val selected = selectedMediaType == type
                            val bg = if (selected) PhantomSecondary else PhantomSurfaceVariant
                            val txtColor = if (selected) Color.White else PhantomTextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable { viewModel.selectedMediaType.value = type }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = type,
                                    color = txtColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.runMediaEncryption() },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isEncryptingMedia,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("encrypt_media_button")
                    ) {
                        if (isEncryptingMedia) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("RUN HARDWARE MEDIA ENCRYPTION", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Display Encrypted Media Metadata Output Box
                    encryptedMediaMetadata?.let { meta ->
                        Spacer(modifier = Modifier.height(20.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PhantomTerminalBg, shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PhantomSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Secure Hardware Key Encryption Metadata", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = meta,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = PhantomTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
