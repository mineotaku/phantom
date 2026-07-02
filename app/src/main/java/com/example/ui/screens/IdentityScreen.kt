package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun IdentityScreen(viewModel: PhantomViewModel) {
    val isBooted by viewModel.isBooted.collectAsState()
    val isRegistered by viewModel.isRegistered.collectAsState()
    val registrationPhone by viewModel.registrationPhone.collectAsState()
    val registrationOtp by viewModel.registrationOtp.collectAsState()
    val verificationStep by viewModel.verificationStep.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    
    val identityPublicKey by viewModel.identityPublicKey.collectAsState()
    val identityPrivateKey by viewModel.identityPrivateKey.collectAsState()
    val signedPreKey by viewModel.signedPreKey.collectAsState()
    val oneTimePreKeysCount by viewModel.oneTimePreKeysCount.collectAsState()
    
    val deviceId by viewModel.deviceId.collectAsState()
    val tokenFCM by viewModel.tokenFCM.collectAsState()
    val activeSessionToken by viewModel.activeSessionToken.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Identity Directory Setup
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Identity Directory Registration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Link your public identity keys to a verified phone number on the routing directory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (!isRegistered) {
                        if (!verificationStep) {
                            OutlinedTextField(
                                value = registrationPhone,
                                onValueChange = { viewModel.registrationPhone.value = it },
                                label = { Text("Secure Phone Number") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomSecondary,
                                    unfocusedBorderColor = PhantomBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.sendVerificationSms() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("REQUEST VERIFICATION SMS", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedTextField(
                                value = registrationOtp,
                                onValueChange = { viewModel.registrationOtp.value = it },
                                label = { Text("Verification OTP Code") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomSecondary,
                                    unfocusedBorderColor = PhantomBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Simulated OTP Sent: Enter '4839'",
                                style = MaterialTheme.typography.labelSmall,
                                color = PhantomSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.verifySmsCode() },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                } else {
                                    Text("VERIFY OTP CODE", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Phone Verified Securely",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Keys signed and published to Phantom Directory.",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Card 2: Identity Key Vault
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
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = PhantomSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Identity Key Vault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PhantomTextPrimary
                            )
                        }
                        
                        // Badge count of prekeys
                        Box(
                            modifier = Modifier
                                .background(PhantomSelectedChat, shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$oneTimePreKeysCount Pre-Keys",
                                style = MaterialTheme.typography.labelSmall,
                                color = PhantomSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Curve25519 identity keypair and Signed Pre-Keys managed in secure storage sandbox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KeyDisplayField(title = "Identity Public Key (IK_pub)", key = identityPublicKey, isPrivate = false)
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDisplayField(title = "Identity Private Key (IK_priv)", key = identityPrivateKey, isPrivate = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDisplayField(title = "Signed Pre-Key (SPK)", key = signedPreKey, isPrivate = false)
                }
            }
        }
        
        // Card 3: Platform Routing Metadata
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Platform Routing Metadata",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ephemeral registration keys and notification FCM routing identifiers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KeyDisplayField(title = "Hardware Device ID", key = deviceId, isPrivate = false)
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDisplayField(title = "FCM Notification Channel Token", key = tokenFCM, isPrivate = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDisplayField(title = "Active Directory Session JWT", key = activeSessionToken, isPrivate = true)
                }
            }
        }
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
