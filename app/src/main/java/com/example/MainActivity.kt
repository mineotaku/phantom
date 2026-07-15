package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ui.viewmodel.*
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var database: PhantomDatabase
    private lateinit var viewModel: PhantomViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var identityViewModel: IdentityViewModel
    private lateinit var securityViewModel: SecurityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log all uncaught errors to Android Logcat to aid troubleshooting

        // Prevent screenshots / video recording
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as PhantomApplication
        database = app.database
        val repository = app.repository

        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[PhantomViewModel::class.java]
        authViewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]
        chatViewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        identityViewModel = ViewModelProvider(this, factory)[IdentityViewModel::class.java]
        securityViewModel = ViewModelProvider(this, factory)[SecurityViewModel::class.java]

        // Schedule periodic background signed prekey rotation (runs every 14 days)
        val rotationRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.sync.PreKeyRotationWorker>(
            14, java.util.concurrent.TimeUnit.DAYS
        ).build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "PreKeyRotation",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            rotationRequest
        )

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
                    val isAppLocked by viewModel.isAppLocked.collectAsState()
                    
                    if (!isLoggedIn) {
                        LoginScreen(viewModel = authViewModel)
                    } else if (isAppLocked) {
                        AppLockScreen(viewModel = viewModel)
                    } else {
                        MainScaffold(
                            viewModel = viewModel,
                            chatViewModel = chatViewModel,
                            identityViewModel = identityViewModel,
                            securityViewModel = securityViewModel
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

    override fun onStop() {
        super.onStop()
        if (com.example.security.DuressPin.isAppLockEnabled(applicationContext)) {
            viewModel.isAppLocked.value = true
        }
    }
}

@Composable
fun MainScaffold(
    viewModel: PhantomViewModel,
    chatViewModel: ChatViewModel,
    identityViewModel: IdentityViewModel,
    securityViewModel: SecurityViewModel,
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
                // Tab 1: Friends
                NavigationBarItem(
                    selected = currentRoute == "friends",
                    onClick = { navController.navigate("friends") { launchSingleTop = true } },
                    icon = { Icon(if (currentRoute == "friends") Icons.Filled.PersonAdd else Icons.Outlined.PersonAdd, contentDescription = "Friends") },
                    label = { Text("Friends") }
                )
                // Tab 2: Profile
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
                    composable("friends") {
                        FriendsScreen(viewModel = viewModel, chatViewModel = chatViewModel, navController = navController)
                    }
                    composable("identity") {
                        IdentityScreen(viewModel = identityViewModel)
                    }
                    composable("messages") {
                        MessagingScreen(viewModel = chatViewModel)
                    }
                    composable("storage") {
                        StorageScreen(viewModel = viewModel)
                    }
                    composable("network") {
                        NetworkScreen(viewModel = chatViewModel)
                    }
                    composable("security") {
                        SecurityScreen(
                            viewModel = securityViewModel,
                            onTriggerBiometricAuth = onTriggerBiometricAuth,
                            onNavigateToNetwork = { navController.navigate("network") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(viewModel: PhantomViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.PhantomBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Icon",
            tint = com.example.ui.theme.PhantomSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Phantom Locked",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Enter your secure PIN to access local E2EE storage",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Custom indicator dots showing PIN length
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val active = pinInput.length > i
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (active) com.example.ui.theme.PhantomSecondary else Color.DarkGray,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Standard numeric keyboard grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            for (row in keys) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    for (key in row) {
                        TextButton(
                            onClick = {
                                errorMessage = null
                                when (key) {
                                    "C" -> pinInput = ""
                                    "⌫" -> if (pinInput.isNotEmpty()) pinInput = pinInput.substring(0, pinInput.length - 1)
                                    else -> {
                                        if (pinInput.length < 4) {
                                            pinInput += key
                                            if (pinInput.length == 4) {
                                                // Trigger verification
                                                val duressActive = com.example.security.DuressPin.isDuressEnabled(context)
                                                val realActive = com.example.security.DuressPin.isAppLockEnabled(context)
                                                
                                                if (duressActive && com.example.security.DuressPin.verifyDuressPin(context, pinInput)) {
                                                    viewModel.addLog("SYS", "Security perimeter breached! Panic wipe sequence triggered.")
                                                    viewModel.viewModelScope.launch {
                                                        com.example.security.DuressPin.executePanicWipe(context, viewModel.repository)
                                                    }
                                                } else if (realActive && com.example.security.DuressPin.verifyRealPin(context, pinInput)) {
                                                    viewModel.isAppLocked.value = false
                                                } else {
                                                    errorMessage = "Incorrect PIN code. Access denied."
                                                    pinInput = ""
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color.DarkGray.copy(alpha = 0.2f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
