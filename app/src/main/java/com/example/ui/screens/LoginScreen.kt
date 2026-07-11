package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel
import com.example.ui.components.SecureTextField
import com.example.security.DuressPin
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: com.example.ui.viewmodel.AuthViewModel) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
    val loginEmail by viewModel.loginEmail.collectAsState()
    val serverHost by com.example.network.NetworkConfig.serverHost.collectAsState()
    val loginOtpInput by viewModel.loginOtpInput.collectAsState()
    val isSendingOtp by viewModel.isSendingOtp.collectAsState()
    val otpStepActive by viewModel.otpStepActive.collectAsState()
    val loginErrorMsg by viewModel.loginErrorMsg.collectAsState()
    val loginSuccessSplash by viewModel.loginSuccessSplash.collectAsState()
    val otpTimerSeconds by viewModel.otpTimerSeconds.collectAsState()
    
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
                                text = "Configure your relay server host and enter your secure email address to start registration.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PhantomTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))

                            SecureTextField(
                                value = serverHost,
                                onValueChange = {
                                    com.example.network.NetworkConfig.serverHost.value = it
                                    context.getSharedPreferences("phantom_security", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("server_host", it).apply()
                                },
                                label = "Relay Server Host",
                                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = PhantomTertiary) },
                                singleLine = true,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("server_host_field")
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            SecureTextField(
                                value = loginEmail,
                                onValueChange = { viewModel.loginEmail.value = it.trim().lowercase() },
                                label = "Secure Email Address",
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PhantomTertiary) },
                                singleLine = true,
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
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
                                enabled = loginEmail.isNotBlank() && serverHost.isNotBlank() && !isSendingOtp,
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
                            
                            SecureTextField(
                                value = loginOtpInput,
                                onValueChange = { viewModel.loginOtpInput.value = it },
                                label = "Secure Code or PIN",
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PhantomTertiary) },
                                singleLine = true,
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
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
                                enabled = loginOtpInput.length >= 4,
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
    }
}
