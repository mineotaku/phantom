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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
            }
        }

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 || requestCode == 103) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.onStoragePermissionGranted(if (requestCode == 102) "image" else "video")
            } else {
                android.widget.Toast.makeText(this, "Storage permission is required to select files", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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
                // Tab 0: Chats
                NavigationBarItem(
                    selected = currentRoute == "messages",
                    onClick = { navController.navigate("messages") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "messages") Icons.Filled.Forum else Icons.Outlined.Forum, contentDescription = "Chats") },
                    label = { Text("Chats") }
                )
                // Tab 1: Profile
                NavigationBarItem(
                    selected = currentRoute == "identity",
                    onClick = { navController.navigate("identity") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "identity") Icons.Filled.Person else Icons.Outlined.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
                // Tab 2: Settings
                NavigationBarItem(
                    selected = currentRoute == "security",
                    onClick = { navController.navigate("security") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "security") Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
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
