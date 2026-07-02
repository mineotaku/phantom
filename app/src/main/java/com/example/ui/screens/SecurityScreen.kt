package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun SecurityScreen(
    viewModel: PhantomViewModel,
    onTriggerBiometricAuth: (onSuccess: () -> Unit) -> Unit
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val playIntegrityVerified by viewModel.playIntegrityVerified.collectAsState()
    val certificatePinningActive by viewModel.certificatePinningActive.collectAsState()
    val threatLevel by viewModel.threatLevel.collectAsState()
    val sqlCipherLocked by viewModel.sqlCipherLocked.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Threat Assessment Module
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = PhantomSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Threat Assessment Module",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PhantomTextPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    val (barColor, text, icon, desc) = when (threatLevel) {
                        "SECURE" -> Quadruple(
                            Color(0xFF81C784),
                            "SANDBOX STATE: SECURE",
                            Icons.Default.GppGood,
                            "Play Integrity confirmed. SSL certificate pins verified. CrytoRatchet keys operating normally."
                        )
                        "WARNING" -> Quadruple(
                            Color(0xFFFFB74D),
                            "SANDBOX STATE: WARNING",
                            Icons.Default.Warning,
                            "Certificate pinning disabled. Intermediate relay connections might be susceptible to interception."
                        )
                        else -> Quadruple(
                            PhantomError,
                            "SANDBOX STATE: COMPROMISED",
                            Icons.Default.GppBad,
                            "Google Play Integrity verification failed. Operating environment signature integrity is untrusted."
                        )
                    }

                    // Threat status bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(barColor, shape = RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = barColor, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = text,
                            fontWeight = FontWeight.ExtraBold,
                            color = barColor,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                }
            }
        }

        // Card 2: Security Profile Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Security Profile Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure device hardware policy gates and network verification boundaries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Biometric lock Screen Row
                    SecurityGuardToggleRow(
                        title = "Biometric Lock Screen",
                        description = "Enforce fingerprint or face lock authentication on app resume gates.",
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Trigger actual device biometric prompt verification before turning on
                                onTriggerBiometricAuth {
                                    viewModel.biometricEnabled.value = true
                                    viewModel.addLog("SEC", "Biometric unlock enrolled successfully.")
                                }
                            } else {
                                viewModel.biometricEnabled.value = false
                                viewModel.addLog("SYS", "Biometric screen lock disabled.")
                            }
                        }
                    )

                    HorizontalDivider(color = PhantomBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Google Play Integrity Row
                    SecurityGuardToggleRow(
                        title = "Google Play Integrity Attestation",
                        description = "Verify local binary signature and system integrity checks before server handshake.",
                        checked = playIntegrityVerified,
                        onCheckedChange = { viewModel.togglePlayIntegrity(it) }
                    )

                    HorizontalDivider(color = PhantomBorder, modifier = Modifier.padding(vertical = 12.dp))

                    // Certificate Pinning Row
                    SecurityGuardToggleRow(
                        title = "Certificate Pinning Layer",
                        description = "Hardcode server certificate hash prints to mitigate Man-In-The-Middle network attacks.",
                        checked = certificatePinningActive,
                        onCheckedChange = { viewModel.toggleCertPinning(it) }
                    )
                }
            }
        }

        // Rotate Ratchets FAB Button row
        item {
            Button(
                onClick = { viewModel.triggerSecureBoot() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhantomSurfaceVariant,
                    contentColor = PhantomSecondary
                ),
                border = BorderStroke(1.dp, PhantomBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("rotate_keys_bottom_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = PhantomSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ROTATE SYSTEM ENCRYPTION RATCHETS", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SecurityGuardToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = PhantomTextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = PhantomTextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PhantomPrimary,
                checkedTrackColor = PhantomSecondary
            )
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
