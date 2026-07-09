package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun LoginScreen(viewModel: PhantomViewModel) {
    val focusManager = LocalFocusManager.current
    
    val loginEmail by viewModel.loginEmail.collectAsState()
    val loginOtpInput by viewModel.loginOtpInput.collectAsState()
    val isSendingOtp by viewModel.isSendingOtp.collectAsState()
    val otpStepActive by viewModel.otpStepActive.collectAsState()
    val loginErrorMsg by viewModel.loginErrorMsg.collectAsState()
    val loginSuccessSplash by viewModel.loginSuccessSplash.collectAsState()
    val otpTimerSeconds by viewModel.otpTimerSeconds.collectAsState()
    val showSmtpRelayLogs by viewModel.showSmtpRelayLogs.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PhantomBg, PhantomSurfaceVariant)
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Logo
            Text(
                text = "PHANTOM",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = PhantomSecondary,
                letterSpacing = 4.sp
            )
            
            Text(
                text = "Privacy-First Secure E2EE Messenger",
                style = MaterialTheme.typography.bodyLarge,
                color = PhantomTextSecondary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (loginSuccessSplash) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PhantomSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Identity Verified!",
                            style = MaterialTheme.typography.titleLarge,
                            color = PhantomSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Provisioning Security Core...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomTextSecondary
                        )
                    }
                }
            } else {
                if (!otpStepActive) {
                    // Email Entry Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Device Registration",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PhantomTextPrimary
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Enter your secure email address to receive an ephemeral login token code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PhantomTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            OutlinedTextField(
                                value = loginEmail,
                                onValueChange = { viewModel.loginEmail.value = it },
                                label = { Text("Secure Email Address") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PhantomTertiary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomSecondary,
                                    unfocusedBorderColor = PhantomBorder
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("email_input_field")
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.dispatchEmailOtp(loginEmail)
                                },
                                enabled = loginEmail.isNotBlank() && !isSendingOtp,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("request_otp_button")
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("REQUEST SECURE TOKEN", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // OTP Verification Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Verify Secure Token",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PhantomTextPrimary
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Input the 6-digit cryptographic verification code sent to $loginEmail",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PhantomTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            OutlinedTextField(
                                value = loginOtpInput,
                                onValueChange = { if (it.length <= 6) viewModel.loginOtpInput.value = it },
                                label = { Text("Secure 6-Digit Code") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PhantomTertiary) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PhantomSecondary,
                                    unfocusedBorderColor = PhantomBorder
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_input_field")
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (otpTimerSeconds > 0) "Resend token in ${otpTimerSeconds}s" else "Token ready to resend",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PhantomTextSecondary
                                )
                                
                                TextButton(
                                    onClick = { viewModel.dispatchEmailOtp(loginEmail) },
                                    enabled = otpTimerSeconds == 0,
                                    colors = ButtonDefaults.textButtonColors(contentColor = PhantomSecondary)
                                ) {
                                    Text("RESEND", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.verifyOtpCode(loginOtpInput)
                                },
                                enabled = loginOtpInput.length == 6,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("verify_otp_button")
                            ) {
                                Text("VERIFY IDENTITY", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            // Error Display Notification Panel
            loginErrorMsg?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomError,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // SMTP Relay Handshake Terminal Button overlay
        if (showSmtpRelayLogs) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PhantomSurfaceVariant),
                    border = BorderStroke(1.dp, PhantomBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(450.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = PhantomSecondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SMTP Identity Relay Handshake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { viewModel.showSmtpRelayLogs.value = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = PhantomError)
                            ) {
                                Text("CLOSE", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        HorizontalDivider(color = PhantomBorder, modifier = Modifier.padding(vertical = 8.dp))
                        
                        LazyColumn(
                            reverseLayout = false,
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            items(viewModel.smtpRelayLogs) { log ->
                                val color = when {
                                    log.startsWith("ERROR") -> Color(0xFFE57373)
                                    log.startsWith("SUCCESS") || log.startsWith("220") || log.startsWith("250") -> Color(0xFF81C784)
                                    log.startsWith("SEC") -> Color(0xFF64B5F6)
                                    else -> Color(0xFFE0E0E0)
                                }
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = color,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
