package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel
import com.example.security.DuressPin
import com.example.security.SelfDestructTimer
import com.example.ui.components.SecureTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: com.example.ui.viewmodel.SecurityViewModel,
    onTriggerBiometricAuth: (onSuccess: () -> Unit) -> Unit
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val loginEmail by viewModel.loginEmail.collectAsState()
    val defaultTimer by viewModel.defaultSelfDestructTimer.collectAsState()
    val certificatePinningActive by viewModel.certificatePinningActive.collectAsState()

    val context = LocalContext.current
    var duressEnabled by remember { mutableStateOf(DuressPin.isEnabled(context)) }
    var showDuressDialog by remember { mutableStateOf(false) }
    var duressPinInput by remember { mutableStateOf("") }

    var appLockEnabled by remember { mutableStateOf(DuressPin.isAppLockEnabled(context)) }
    var showRealLockDialog by remember { mutableStateOf(false) }
    var realLockPinInput by remember { mutableStateOf("") }

    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showResetAppConfirm by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Preferences & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Text(
                        text = "Enforce biological and local device policy checks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )

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

                    HorizontalDivider(color = PhantomBorder)

                    SecurityGuardToggleRow(
                        title = "Enforce Certificate Pinning",
                        description = "Verify server SSL leaf cert hashes to prevent man-in-the-middle attacks.",
                        checked = certificatePinningActive,
                        onCheckedChange = { active ->
                            viewModel.toggleCertPinning(active)
                        }
                    )

                    HorizontalDivider(color = PhantomBorder)

                    // Default Disappearing Messages configurations
                    var showTimerDropdown by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disappearing Messages (Default)", fontWeight = FontWeight.Bold, color = PhantomTextPrimary, fontSize = 14.sp)
                            Text("Timer used for newly initiated conversations.", style = MaterialTheme.typography.labelSmall, color = PhantomTextSecondary)
                        }
                        Box {
                            TextButton(onClick = { showTimerDropdown = true }) {
                                Text(defaultTimer.displayName, fontWeight = FontWeight.Bold, color = PhantomSecondary)
                            }
                            DropdownMenu(
                                expanded = showTimerDropdown,
                                onDismissRequest = { showTimerDropdown = false }
                            ) {
                                SelfDestructTimer.values().forEach { timer ->
                                    DropdownMenuItem(
                                        text = { Text(timer.displayName) },
                                        onClick = {
                                            viewModel.defaultSelfDestructTimer.value = timer
                                            showTimerDropdown = false
                                            viewModel.addLog("SYS", "Default message self-destruct timer set to: ${timer.displayName}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card 2a: App Lock PIN Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "App Lock PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Text(
                        text = "Set a secure lock PIN to protect the application from unauthorized access when it goes to background or is opened.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("App Lock Status", fontWeight = FontWeight.Bold, color = PhantomTextPrimary, fontSize = 14.sp)
                            Text(
                                text = if (appLockEnabled) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (appLockEnabled) Color(0xFF81C784) else PhantomError
                            )
                        }
                        
                        if (appLockEnabled) {
                            Button(
                                onClick = {
                                    DuressPin.removeRealPin(context)
                                    appLockEnabled = false
                                    viewModel.addLog("SYS", "App lock PIN disabled.")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomError),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("DISABLE", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { showRealLockDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CONFIGURE PIN", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Card 2b: Duress Panic PIN Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PhantomBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Duress Panic Protocol",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Text(
                        text = "Set a duress entry PIN. Entering this PIN at login instantly wipes all local E2EE keys, messages, and configurations, showing a clean install setup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Duress Protection Status", fontWeight = FontWeight.Bold, color = PhantomTextPrimary, fontSize = 14.sp)
                            Text(
                                text = if (duressEnabled) "Armed (Panic wipe enabled)" else "Unconfigured",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (duressEnabled) Color(0xFF81C784) else PhantomError
                            )
                        }
                        
                        if (duressEnabled) {
                            Button(
                                onClick = {
                                    DuressPin.removePin(context)
                                    duressEnabled = false
                                    viewModel.addLog("SYS", "Duress panic wipe PIN disabled.")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomError),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("DISABLE", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { showDuressDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CONFIGURE PIN", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
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
                        onClick = { showClearChatConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSurfaceVariant, contentColor = PhantomSecondary),
                        border = BorderStroke(1.dp, PhantomBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("CLEAR ALL CHAT HISTORY", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showResetAppConfirm = true },
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

    // Confirmation Dialogs
    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("Purge Chat History?") },
            text = { Text("This will permanently delete all local E2EE messages. This operation is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearChatConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary)
                ) {
                    Text("PURGE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    if (showResetAppConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAppConfirm = false },
            title = { Text("Reset Application Container?") },
            text = { Text("Wipe all local E2EE keys, configurations, linked devices, and message history? Phantom will close to complete the reset.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetApp()
                        showResetAppConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomError)
                ) {
                    Text("RESET")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAppConfirm = false }) { Text("CANCEL") }
            }
        )
    }

    // Configure Real App Lock PIN Dialog
    if (showRealLockDialog) {
        AlertDialog(
            onDismissRequest = { showRealLockDialog = false },
            title = { Text("Configure App Lock PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a secure numeric PIN to lock and unlock the Phantom app.")
                    SecureTextField(
                        value = realLockPinInput,
                        onValueChange = { realLockPinInput = it },
                        label = "Enter App Lock PIN",
                        singleLine = true,
                        keyboardType = KeyboardType.NumberPassword
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (realLockPinInput.length >= 4) {
                            DuressPin.storeRealPin(context, realLockPinInput)
                            appLockEnabled = true
                            showRealLockDialog = false
                            realLockPinInput = ""
                            viewModel.addLog("SEC", "App lock PIN configured successfully.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                    enabled = realLockPinInput.length >= 4
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRealLockDialog = false }) { Text("CANCEL") }
            }
        )
    }

    // Configure Duress PIN Dialog
    if (showDuressDialog) {
        AlertDialog(
            onDismissRequest = { showDuressDialog = false },
            title = { Text("Configure Duress PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select a unique PIN (e.g. 4-digit code) that differs from your main password or lock code.")
                    SecureTextField(
                        value = duressPinInput,
                        onValueChange = { duressPinInput = it },
                        label = "Enter Duress PIN",
                        singleLine = true,
                        keyboardType = KeyboardType.NumberPassword
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (duressPinInput.length >= 4) {
                            DuressPin.storePin(context, duressPinInput)
                            duressEnabled = true
                            showDuressDialog = false
                            duressPinInput = ""
                            viewModel.addLog("SEC", "Duress panic wipe PIN configured successfully.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                    enabled = duressPinInput.length >= 4
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuressDialog = false }) { Text("CANCEL") }
            }
        )
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
