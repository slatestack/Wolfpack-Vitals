package com.example.wolfpackvitals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.VitalsViewModel
import com.example.wolfpackvitals.ui.components.SettingsDialog
import com.example.wolfpackvitals.ui.screens.DashboardScreen
import com.example.wolfpackvitals.ui.screens.LandingScreen
import com.example.wolfpackvitals.ui.screens.LoginScreen
import com.example.wolfpackvitals.ui.screens.ProfileScreen
import com.example.wolfpackvitals.ui.screens.RegisterScreen
import com.example.wolfpackvitals.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            WolfpackVitalsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val vitalsViewModel: VitalsViewModel = viewModel(
                    factory = VitalsViewModel.factory(context.applicationContext.assets)
                )
                val uiState by vitalsViewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, vitalsViewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) vitalsViewModel.setApplicationActive(true)
                        if (event == Lifecycle.Event.ON_PAUSE) vitalsViewModel.setApplicationActive(false)
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    vitalsViewModel.setApplicationActive(
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    )
                    onDispose {
                        vitalsViewModel.setApplicationActive(false)
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                var showSettingsDialog by remember { mutableStateOf(false) }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Only show TopBar and BottomBar on authenticated core screens
                val showAppChrome = currentRoute in listOf("dashboard", "profile")

                Scaffold(
                    topBar = {
                        if (showAppChrome) {
                            TopHeaderBar(
                                onSettingsClick = {
                                    showSettingsDialog = true
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (showAppChrome) {
                            BottomNavigationBar(navController = navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "landing",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // 1. Landing Screen (Hero / Welcome Page)
                        composable("landing") {
                            LandingScreen(
                                onNavigateToLogin = { navController.navigate("login") },
                                onNavigateToRegister = { navController.navigate("register") }
                            )
                        }

                        // 2. Sign In Screen
                        composable("login") {
                            LoginScreen(
                                viewModel = vitalsViewModel,
                                onLoginSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = {
                                    navController.navigate("register") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 3. Register Screen
                        composable("register") {
                            RegisterScreen(
                                viewModel = vitalsViewModel,
                                onRegisterSuccess = {
                                    navController.navigate("dashboard") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 4. Main Telemetry Dashboard
                        composable("dashboard") {
                            DashboardScreen(
                                uiState = uiState,
                                viewModel = vitalsViewModel
                            )
                        }

                        // 5. User & Wearables Profile
                        composable("profile") {
                            ProfileScreen(
                                uiState = uiState,
                                viewModel = vitalsViewModel,
                                onLogout = {
                                    navController.navigate("landing") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsDialog(
                            settings = uiState.settings,
                            onDismiss = { showSettingsDialog = false },
                            onSaveSettings = { live, caching, deid ->
                                vitalsViewModel.updateSettings(
                                    liveStreaming = live,
                                    offlineCaching = caching,
                                    deidentified = deid
                                )
                            },
                            onClearCache = {
                                vitalsViewModel.clearLocalCache { }
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Top Header Bar ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderBar(
    onSettingsClick: () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = NCStateRed,
            titleContentColor = Color.White
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_wolf_heart_white),
                    contentDescription = "Wolfpack Vitals Logo",
                    modifier = Modifier.size(30.dp)
                )
                Column {
                    Text("Wolfpack Vitals", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("The 4 Aces • NC State", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    )
}

// --- Navigation Bar ---
@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        NavItem("Dashboard", "dashboard", Icons.Default.BarChart),
        NavItem("Profile", "profile", Icons.Default.Person)
    )

    NavigationBar(containerColor = Color.White) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                selected = currentRoute == item.route,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NCStateRed,
                    selectedTextColor = NCStateRed,
                    indicatorColor = Color(0xFFFEE2E2)
                ),
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class NavItem(val title: String, val route: String, val icon: ImageVector)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardScreenPreview() {
    WolfpackVitalsTheme {
        Scaffold(
            topBar = {
                TopHeaderBar(onSettingsClick = {})
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Dashboard") },
                        label = { Text("Dashboard", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        selected = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NCStateRed,
                            selectedTextColor = NCStateRed,
                            indicatorColor = Color(0xFFFEE2E2)
                        ),
                        onClick = {}
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        selected = false,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NCStateRed,
                            selectedTextColor = NCStateRed,
                            indicatorColor = Color(0xFFFEE2E2)
                        ),
                        onClick = {}
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                DashboardScreen(
                    uiState = DashboardUiState(),
                    viewModel = viewModel(factory = VitalsViewModel.factory(LocalContext.current.applicationContext.assets))
                )
            }
        }
    }
}
