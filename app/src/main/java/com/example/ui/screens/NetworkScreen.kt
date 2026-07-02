package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkScreen(viewModel: PhantomViewModel) {
    val bobOnline by viewModel.bobOnline.collectAsState()
    val serverQueue = viewModel.serverQueue
    val networkLogs = viewModel.networkLogs
    val deviceId by viewModel.deviceId.collectAsState()
    val tokenFCM by viewModel.tokenFCM.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Relay Network Terminal Status
        Card(
            colors = CardDefaults.cardColors(containerColor = PhantomSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PhantomBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NetworkWifi, contentDescription = null, tint = PhantomSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Relay Network Terminal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PhantomTerminalBg, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "WebSocket Channel Tunnel:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomTextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (bobOnline) Color(0xFF81C784) else PhantomError, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (bobOnline) "SECURE" else "TUNNEL_DISCONNECTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (bobOnline) Color(0xFF81C784) else PhantomError
                        )
                    }
                }
            }
        }

        // Card 2: Live Encrypted Packet Trace logs
        Card(
            colors = CardDefaults.cardColors(containerColor = PhantomSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PhantomBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ENCRYPTED PACKET TRACE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PhantomSecondary
                    )

                    // Flush queue button
                    if (serverQueue.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.flushOfflineQueue() },
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                            shape = RoundedCornerShape(8.dp),
                            enabled = bobOnline,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FLUSH QUEUE (${serverQueue.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (networkLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Awaiting network packets...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = PhantomTertiary
                        )
                    }
                } else {
                    LazyColumn(
                        reverseLayout = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        items(networkLogs) { log ->
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
                            val color = when (log.type) {
                                "SEC" -> Color(0xFF81C784)
                                "NET" -> Color(0xFF64B5F6)
                                "SYS" -> PhantomPrimary
                                "IN" -> Color(0xFFFFB74D)
                                else -> Color(0xFFE57373)
                            }
                            Text(
                                text = "[$timeStr] [${log.type}] ${log.description}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
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
