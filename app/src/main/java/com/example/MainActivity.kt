package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.db.PhantomDatabase
import com.example.db.PhantomRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PhantomBg
import com.example.ui.theme.PhantomPrimary
import com.example.ui.theme.PhantomSecondary
import com.example.ui.viewmodel.PhantomViewModel

class MainActivity : FragmentActivity() {
    private lateinit var database: PhantomDatabase
    private lateinit var viewModel: PhantomViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize local secure Room database (Version 3 with fallback)
        database = androidx.room.Room.databaseBuilder(
            applicationContext,
            PhantomDatabase::class.java,
            "phantom_secure.db",
        ).fallbackToDestructiveMigration(true).build()

        // Create Repository & ViewModel
        val repository = PhantomRepository(database.phantomDao())
        viewModel = PhantomViewModel(application, repository)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PhantomBg
                ) {
                    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                    
                    if (!isLoggedIn) {
                        LoginScreen(viewModel = viewModel)
                    } else {
                        MainScaffold(
                            viewModel = viewModel
                        ) { onSuccess ->
                            triggerBiometricAuth(onSuccess)
                        }
                    }
                }
            }
        }
    }

    // Standard AndroidX BiometricPrompt implementation
    private fun triggerBiometricAuth(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                viewModel.addLog("SYS", "Biometric enrollment verification failed: $errString")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Attestation Required")
            .setSubtitle("Authenticate secure credential configuration")
            .setNegativeButtonText("Cancel Verification")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun MainScaffold(
    viewModel: PhantomViewModel,
    onTriggerBiometricAuth: (onSuccess: () -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "messages"

    BackHandler(enabled = currentRoute != "messages") {
        navController.navigate("messages") {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    val isBooted by viewModel.isBooted.collectAsState()
    val bootProgress by viewModel.bootProgress.collectAsState()
    val bootLog by viewModel.bootLog.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                // Tab 0: Identity
                NavigationBarItem(
                    selected = currentRoute == "identity",
                    onClick = { navController.navigate("identity") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "identity") Icons.Filled.VpnKey else Icons.Outlined.VpnKey, contentDescription = "Identity") },
                    label = { Text("Identity") }
                )
                // Tab 1: Messages
                NavigationBarItem(
                    selected = currentRoute == "messages",
                    onClick = { navController.navigate("messages") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "messages") Icons.Filled.Forum else Icons.Outlined.Forum, contentDescription = "Messages") },
                    label = { Text("Messages") }
                )
                // Tab 2: Storage
                NavigationBarItem(
                    selected = currentRoute == "storage",
                    onClick = { navController.navigate("storage") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "storage") Icons.Filled.Storage else Icons.Outlined.Storage, contentDescription = "Storage") },
                    label = { Text("Storage") }
                )
                // Tab 3: Network
                NavigationBarItem(
                    selected = currentRoute == "network",
                    onClick = { navController.navigate("network") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "network") Icons.Filled.NetworkWifi else Icons.Outlined.NetworkWifi, contentDescription = "Network") },
                    label = { Text("Network") }
                )
                // Tab 4: Security
                NavigationBarItem(
                    selected = currentRoute == "security",
                    onClick = { navController.navigate("security") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "security") Icons.Filled.Security else Icons.Outlined.Security, contentDescription = "Security") },
                    label = { Text("Security") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Key rotation / boot rekey blocker screen
            if (!isBooted && (currentRoute != "security")) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "System Rekeying Active",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PhantomSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { bootProgress },
                            color = PhantomSecondary,
                            trackColor = PhantomPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = bootLog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhantomSecondary
                        )
                    }
                }
            } else {
                NavHost(
                    navController = navController,
                    startDestination = "messages",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("identity") {
                        IdentityScreen(viewModel = viewModel)
                    }
                    composable("messages") {
                        MessagingScreen(viewModel = viewModel)
                    }
                    composable("storage") {
                        StorageScreen(viewModel = viewModel)
                    }
                    composable("network") {
                        NetworkScreen(viewModel = viewModel)
                    }
                    composable("security") {
                        SecurityScreen(
                            viewModel = viewModel,
                            onTriggerBiometricAuth = onTriggerBiometricAuth
                        )
                    }
                }
            }
        }
    }
}
