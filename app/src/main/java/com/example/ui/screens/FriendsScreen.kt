package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.PhantomViewModel
import com.example.ui.models.ChatUser

@Composable
fun FriendsScreen(
    viewModel: PhantomViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController
) {
    var tab by remember { mutableStateOf(0) }
    var allUsers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var acceptedFriends by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var incomingRequests by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    var sentMessage by remember { mutableStateOf<String?>(null) }

    val currentUserName = viewModel.loginEmail.collectAsState().value.substringBefore("@")

    fun refresh() {
        viewModel.getFriendRequests { incoming, outgoing ->
            incomingRequests = incoming
            outgoingRequests = outgoing
        }
        viewModel.getFriendsListFromServer { list ->
            acceptedFriends = list
        }
        viewModel.getAllUsersFromServer { list ->
            allUsers = list
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhantomBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Friends Console",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                showSearch = !showSearch
                if (!showSearch) {
                    searchQuery = ""
                    searchResults = emptyList()
                }
            }) {
                Icon(
                    imageVector = if (showSearch) Icons.Default.Close else Icons.Default.PersonSearch,
                    contentDescription = "Search",
                    tint = PhantomSecondary
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchUsers(it) { results -> searchResults = results }
                },
                placeholder = { Text("Search users by name...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PhantomSurface,
                    unfocusedContainerColor = PhantomSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            if (searchResults.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        searchResults.forEach { (name, pubKey) ->
                            if (name != currentUserName) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(name, color = Color.White, fontSize = 14.sp)
                                        Text(pubKey.take(16) + "...", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    
                                    val isAlreadyFriend = acceptedFriends.any { it.first == name }
                                    val hasSentRequest = outgoingRequests.any { it.second == name }
                                    val hasIncomingRequest = incomingRequests.any { it.second == name }
                                    
                                    if (isAlreadyFriend) {
                                        Text("Friend ✅", color = Color.Green, fontSize = 12.sp)
                                    } else if (hasSentRequest) {
                                        Text("Pending ⏳", color = Color.Yellow, fontSize = 12.sp)
                                    } else if (hasIncomingRequest) {
                                        val reqId = incomingRequests.first { it.second == name }.first
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(onClick = {
                                                viewModel.acceptFriendRequest(reqId) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Green)
                                            }
                                            IconButton(onClick = {
                                                viewModel.rejectFriendRequest(reqId) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.sendFriendRequest(name) { refresh() }
                                                sentMessage = "Request sent to $name"
                                                searchResults = emptyList()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Add Friend", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        sentMessage?.let {
            Text(it, color = Color.Green, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = PhantomSurface,
            contentColor = PhantomPrimary,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Friends (${acceptedFriends.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Discover") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Requests (${incomingRequests.size + outgoingRequests.size})") })
        }

        when (tab) {
            0 -> {
                // Friends List
                if (acceptedFriends.isEmpty()) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = "No friends added yet. Go to Discover to add friends!",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        items(acceptedFriends) { (name, pubKey) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(pubKey.take(24) + "...", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            chatViewModel.selectedChatUser.value = ChatUser(
                                                name = name,
                                                lastMessage = "Secure connection active",
                                                time = "Now",
                                                unreadCount = 0,
                                                avatarColor = Color(0xFF4FC3F7.toInt()),
                                                isOnline = true,
                                                publicKey = pubKey
                                            )
                                            navController.navigate("messages") {
                                                popUpTo("messages") { inclusive = true }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = "Chat", tint = PhantomPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Discover users directory
                val discoverableUsers = allUsers.filter { it.first != currentUserName }
                if (discoverableUsers.isEmpty()) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = "No other users registered on this server.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        items(discoverableUsers) { (name, pubKey) ->
                            val isAlreadyFriend = acceptedFriends.any { it.first == name }
                            val hasSentRequest = outgoingRequests.any { it.second == name }
                            val hasIncomingRequest = incomingRequests.any { it.second == name }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(pubKey.take(24) + "...", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    if (isAlreadyFriend) {
                                        Text("Friend ✅", color = Color.Green, fontSize = 14.sp)
                                    } else if (hasSentRequest) {
                                        Text("Pending ⏳", color = Color.Yellow, fontSize = 14.sp)
                                    } else if (hasIncomingRequest) {
                                        val reqId = incomingRequests.first { it.second == name }.first
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(onClick = {
                                                viewModel.acceptFriendRequest(reqId) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Green)
                                            }
                                            IconButton(onClick = {
                                                viewModel.rejectFriendRequest(reqId) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.sendFriendRequest(name) { refresh() }
                                                sentMessage = "Request sent to $name"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PhantomPrimary),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Add", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                // Requests
                Column(modifier = Modifier.fillMaxSize()) {
                    if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = "No pending incoming or outgoing requests",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        if (incomingRequests.isNotEmpty()) {
                            Text(
                                text = "Incoming Requests",
                                color = PhantomSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            incomingRequests.forEach { (id, fromUser) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(fromUser, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text("Wants to be friends", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(onClick = {
                                                viewModel.acceptFriendRequest(id) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Green)
                                            }
                                            IconButton(onClick = {
                                                viewModel.rejectFriendRequest(id) { refresh() }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (outgoingRequests.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Sent Requests",
                                color = PhantomSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            outgoingRequests.forEach { (_, toUser) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PhantomSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(toUser, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text("Waiting for response", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Text("Pending ⏳", color = Color.Yellow, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
