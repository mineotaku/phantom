package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    val loginEmail by viewModel.loginEmail.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Text(
                text = "Application Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PhantomTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Card 1: Account
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Account Credentials",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Logged in secure identity session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active Email", fontWeight = FontWeight.Bold, color = PhantomTextPrimary, fontSize = 14.sp)
                            Text(loginEmail, style = MaterialTheme.typography.labelSmall, color = PhantomTextSecondary)
                        }
                    }
                }
            }
        }

        // Card 2: Preference Gates
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Preferences & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enforce biological and local device policy checks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    SecurityGuardToggleRow(
                        title = "Biometric App Lock",
                        description = "Enforce fingerprint or face lock authentication on app startup.",
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
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
                }
            }
        }

        // Card 3: Storage Actions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Storage & Cleanup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )

                    Button(
                        onClick = { viewModel.clearChatHistory() },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSurfaceVariant, contentColor = PhantomSecondary),
                        border = BorderStroke(1.dp, PhantomBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("CLEAR ALL CHAT HISTORY", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.resetApp() },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSurfaceVariant, contentColor = PhantomError),
                        border = BorderStroke(1.dp, PhantomBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("RESET APPLICATION CONTAINER", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Logout Action
        item {
            Button(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = PhantomError, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOG OUT SECURE SESSION", fontWeight = FontWeight.Bold, color = Color.White)
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
