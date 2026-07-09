package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.models.ChatMessage
import com.example.ui.models.ChatUser
import com.example.ui.theme.*
import com.example.ui.viewmodel.PhantomViewModel

@Composable
fun MessagingScreen(viewModel: PhantomViewModel) {
    val selectedChatUser by viewModel.selectedChatUser.collectAsState()
    var isChatDetailOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = isChatDetailOpen && selectedChatUser != null) {
        isChatDetailOpen = false
    }

    if (!isChatDetailOpen || selectedChatUser == null) {
        ChatListScreen(
            viewModel = viewModel,
            onOpenChat = { isChatDetailOpen = true }
        )
    } else {
        ChatDetailScreen(
            viewModel = viewModel,
            partner = selectedChatUser!!,
            onCloseChat = { isChatDetailOpen = false }
        )
    }
}

@Composable
fun ChatListScreen(
    viewModel: PhantomViewModel,
    onOpenChat: () -> Unit
) {
    val users by viewModel.mockUsers.collectAsState()
    val selectedChatUser by viewModel.selectedChatUser.collectAsState()
    val selectedFilterPill by viewModel.selectedFilterPill.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val loginEmail by viewModel.loginEmail.collectAsState()

    var showProfileMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
    ) {
        // Search Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search in messages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PhantomTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PhantomSurface,
                    unfocusedContainerColor = PhantomSurface,
                    focusedBorderColor = PhantomBorder,
                    unfocusedBorderColor = PhantomBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Refresh Contacts Button
            IconButton(
                onClick = { viewModel.syncContactsFromServer() },
                modifier = Modifier
                    .size(40.dp)
                    .background(PhantomSurface, shape = CircleShape)
                    .border(1.dp, PhantomBorder, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Contacts",
                    tint = PhantomSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Profile Avatar Button
            Box {
                IconButton(
                    onClick = { showProfileMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(PhantomSecondary, shape = CircleShape)
                ) {
                    Text(
                        text = loginEmail.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                DropdownMenu(
                    expanded = showProfileMenu,
                    onDismissRequest = { showProfileMenu = false }
                ) {
                    val userId = loginEmail.substringBefore("@")
                    val context = LocalContext.current

                    DropdownMenuItem(
                        text = { Text("Session: $loginEmail") },
                        onClick = {},
                        enabled = false
                    )
                    DropdownMenuItem(
                        text = { Text("User ID: @$userId") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = PhantomSecondary) },
                        onClick = {},
                        enabled = false
                    )
                    DropdownMenuItem(
                        text = { Text("Share Profile Link") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PhantomSecondary) },
                        onClick = {
                            showProfileMenu = false
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
                        }
                    )
                    HorizontalDivider(color = PhantomBorder)
                    DropdownMenuItem(
                        text = { Text("Rotate Secure Keys") },
                        onClick = {
                            showProfileMenu = false
                            viewModel.triggerSecureBoot()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Logout Session") },
                        onClick = {
                            showProfileMenu = false
                            viewModel.logout()
                        }
                    )
                }
            }
        }

        // Filter Pills Row
        val pills = listOf("All", "Contacts", "Finance", "Unknown")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pills) { pill ->
                val selected = selectedFilterPill == pill
                val containerColor = if (selected) PhantomSecondary else PhantomSurface
                val contentColor = if (selected) Color.White else PhantomTextSecondary
                val border = if (selected) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, PhantomBorder)

                Card(
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = border,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { viewModel.selectedFilterPill.value = pill }
                ) {
                    Text(
                        text = pill,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter & Search dynamic results
        val filteredUsers = remember(users, selectedFilterPill, searchQuery) {
            users.filter { user ->
                val matchesSearch = user.name.contains(searchQuery, ignoreCase = true) ||
                                    user.lastMessage.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilterPill) {
                    "Contacts" -> true
                    "Finance" -> false // Simulate empty finance category
                    "Unknown" -> false
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No secure conversations found.",
                        color = PhantomTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredUsers) { user ->
                        val isSelected = selectedChatUser?.name == user.name
                        val rowBg = if (isSelected) PhantomSelectedChat else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable {
                                    viewModel.selectedChatUser.value = user
                                    onOpenChat()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with Online Badge
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(user.avatarColor, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.name.take(2).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                if (user.isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(0xFF81C784), shape = CircleShape)
                                            .border(2.dp, PhantomBg, shape = CircleShape)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Name & Msg info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name,
                                    fontWeight = FontWeight.Bold,
                                    color = PhantomTextPrimary,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = user.lastMessage,
                                    color = PhantomTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Timestamp and Badges
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = user.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PhantomTertiary
                                )
                                if (user.unreadCount > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(PhantomSecondary, shape = CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = user.unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Action Button
            ExtendedFloatingActionButton(
                text = { Text("Start Chat", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                onClick = {
                    if (users.isNotEmpty()) {
                        viewModel.selectedChatUser.value = users[0]
                        onOpenChat()
                    }
                },
                containerColor = PhantomPrimary,
                contentColor = PhantomSecondary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("start_chat_fab")
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: PhantomViewModel,
    partner: ChatUser,
    onCloseChat: () -> Unit
) {
    val messageList by viewModel.messageList.collectAsState()
    val isEncryptingInProgress by viewModel.isEncryptingInProgress.collectAsState()
    val activePipelineStep by viewModel.activePipelineStep.collectAsState()
    val bobOnline by viewModel.bobOnline.collectAsState()
    val typingStatus by viewModel.typingStatus.collectAsState()
    val inputMessageText by viewModel.inputMessageText.collectAsState()
    val loginEmail by viewModel.loginEmail.collectAsState()

    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    // Long press dialog state
    var showActionDialogForMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showEditDialogForMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessageText by remember { mutableStateOf("") }

    val context = LocalContext.current
    var showAttachmentMenu by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadMediaAndSendMessage(uri, "image", partner)
        }
    }

    val legacyPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadMediaAndSendMessage(uri, "image", partner)
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadMediaAndSendMessage(uri, "video", partner)
        }
    }

    val legacyVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadMediaAndSendMessage(uri, "video", partner)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhantomSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseChat) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PhantomSecondary)
            }

            // Small avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(partner.avatarColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = partner.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partner.name,
                    fontWeight = FontWeight.Bold,
                    color = PhantomTextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = if (bobOnline) "Active secure connection" else "Offline queue active",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bobOnline) Color(0xFF81C784) else PhantomError
                )
            }

            // Connection action toggles
            IconButton(onClick = { viewModel.bobOnline.value = !bobOnline }) {
                Icon(
                    imageVector = if (bobOnline) Icons.Default.SignalWifiStatusbar4Bar else Icons.Default.WifiOff,
                    contentDescription = "Toggle Network Status",
                    tint = if (bobOnline) Color(0xFF81C784) else PhantomError
                )
            }

            IconButton(onClick = { viewModel.simulateIncomingMessage("Hi, testing simulated incoming reply E2EE packet!", partner.name) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Simulate Message", tint = PhantomSecondary)
            }
        }

        // Message bubbles list
        LazyColumn(
            reverseLayout = false,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(messageList) { msg ->
                val isMe = msg.sender == loginEmail.substringBefore("@")
                val alignment = if (isMe) Alignment.End else Alignment.Start
                val bubbleColor = if (isMe) PhantomSentBubble else PhantomReceivedBubble

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = alignment
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bubbleColor),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        ),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showActionDialogForMessage = msg }
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (msg.text.startsWith("media_pending:")) {
                                val parts = msg.text.split(":")
                                val mediaType = parts.getOrNull(1) ?: "image"
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PhantomSecondary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Decrypting secure $mediaType...", style = MaterialTheme.typography.bodyMedium, color = PhantomTextSecondary)
                                }
                            } else if (msg.text.startsWith("media:image:")) {
                                val localPath = msg.text.substringAfter("media:image:")
                                val bitmap = remember(localPath) {
                                    BitmapFactory.decodeFile(localPath)?.asImageBitmap()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("[Image Unavailable]", color = PhantomError)
                                }
                            } else if (msg.text.startsWith("media:video:")) {
                                val localPath = msg.text.substringAfter("media:video:")
                                val videoFile = File(localPath)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val videoUri = FileProvider.getUriForFile(
                                                    context,
                                                    "com.example.fileprovider",
                                                    videoFile
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(videoUri, "video/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.fromFile(videoFile), "video/*")
                                                }
                                                context.startActivity(intent)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Play Secure Video", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PhantomTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = PhantomSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = msg.timestamp,
                                    fontSize = 9.sp,
                                    color = PhantomTextSecondary.copy(alpha = 0.8f)
                                )
                                if (isMe) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (msg.isDelivered) Icons.Default.DoneAll else Icons.Default.Done,
                                        contentDescription = null,
                                        tint = if (msg.isDelivered) Color(0xFF81C784) else PhantomTertiary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }



        // Typing Status Dot Simulation
        typingStatus?.let { status ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = PhantomSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = status, style = MaterialTheme.typography.labelSmall, color = PhantomTextSecondary)
            }
        }

        // Suggestions chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhantomSurface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf("Yay!", "Congratulations! 👍", "Ok")
            suggestions.forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, PhantomBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            focusManager.clearFocus()
                            viewModel.encryptAndSendMessage(suggestion, partner)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PhantomTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Text input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PhantomSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { showAttachmentMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add attachment", tint = PhantomSecondary)
                }
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false },
                    modifier = Modifier.background(PhantomSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("📷 Send Photo", color = PhantomTextPrimary) },
                        onClick = {
                            showAttachmentMenu = false
                            try {
                                photoLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            } catch (e: Exception) {
                                try {
                                    legacyPhotoLauncher.launch("image/*")
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "No gallery app found on this device", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🎥 Send Video", color = PhantomTextPrimary) },
                        onClick = {
                            showAttachmentMenu = false
                            try {
                                videoLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            } catch (e: Exception) {
                                try {
                                    legacyVideoLauncher.launch("video/*")
                                } catch (e2: Exception) {
                                    android.widget.Toast.makeText(context, "No gallery app found on this device", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            OutlinedTextField(
                value = inputMessageText,
                onValueChange = { viewModel.inputMessageText.value = it },
                placeholder = { Text("Write E2EE message...") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PhantomBorder,
                    unfocusedBorderColor = PhantomBorder,
                    focusedContainerColor = PhantomBg,
                    unfocusedContainerColor = PhantomBg
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputMessageText.trim().isNotEmpty()) {
                        val text = inputMessageText
                        viewModel.inputMessageText.value = ""
                        focusManager.clearFocus()
                        viewModel.encryptAndSendMessage(text, partner)
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputMessageText.trim().isNotEmpty()) {
                        val text = inputMessageText
                        viewModel.inputMessageText.value = ""
                        focusManager.clearFocus()
                        viewModel.encryptAndSendMessage(text, partner)
                    }
                },
                modifier = Modifier
                    .background(PhantomSecondary, shape = CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }

    // Message action dialog (long press options)
    showActionDialogForMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { showActionDialogForMessage = null },
            title = { Text("Secure Message Options") },
            text = { Text("Select cryptographic or management actions on this message envelope.") },
            confirmButton = {
                TextButton(onClick = { showActionDialogForMessage = null }) { Text("CANCEL") }
            },
            dismissButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            showActionDialogForMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy plaintext payload")
                    }

                    val myName = loginEmail.substringBefore("@")
                    if (msg.sender == myName) {
                        Button(
                            onClick = {
                                editMessageText = msg.text.replace(" (Edited)", "")
                                showEditDialogForMessage = msg
                                showActionDialogForMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PhantomTertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit message envelope")
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.deleteMessage(msg.id, partner)
                            showActionDialogForMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomError),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Purge storage trace (Delete)", color = Color.White)
                    }
                }
            }
        )
    }

    // Message Edit Dialog
    showEditDialogForMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { showEditDialogForMessage = null },
            title = { Text("Edit Secure Message") },
            text = {
                Column {
                    Text("Re-sign and encrypt the text payload with rotated pipeline keys.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editMessageText,
                        onValueChange = { editMessageText = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editMessageText.trim().isNotEmpty()) {
                            viewModel.editMessage(msg.id, editMessageText, partner)
                            showEditDialogForMessage = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomSecondary)
                ) {
                    Text("ENCRYPT & SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialogForMessage = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}
