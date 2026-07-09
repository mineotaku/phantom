package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun IdentityScreen(viewModel: PhantomViewModel) {
    val identityPublicKey by viewModel.identityPublicKey.collectAsState()
    val identityPrivateKey by viewModel.identityPrivateKey.collectAsState()
    val signedPreKey by viewModel.signedPreKey.collectAsState()
    
    val deviceId by viewModel.deviceId.collectAsState()
    val tokenFCM by viewModel.tokenFCM.collectAsState()
    val loginEmail by viewModel.loginEmail.collectAsState()
    
    val userId = loginEmail.substringBefore("@")
    val context = LocalContext.current
    var showKeys by remember { mutableStateOf(false) }

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
            Image(
                painter = painterResource(id = com.example.R.drawable.logo),
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .border(3.dp, PhantomSecondary, CircleShape)
            )
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
                        }
                    }
                }
            }
        }
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
