package com.example.wolfpackvitals

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.VitalsViewModel
import com.example.wolfpackvitals.ui.components.SettingsDialog
import com.example.wolfpackvitals.ui.screens.DashboardScreen
import com.example.wolfpackvitals.ui.screens.ProfileScreen
import com.example.wolfpackvitals.ui.screens.ResearchScreen
import com.example.wolfpackvitals.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            WolfpackVitalsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val vitalsViewModel: VitalsViewModel = viewModel()
                val uiState by vitalsViewModel.uiState.collectAsState()
                var showSettingsDialog by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopHeaderBar(
                            isSyncing = uiState.pipelineStatus.isSyncing,
                            onSyncClick = {
                                vitalsViewModel.syncPipeline {
                                    Toast.makeText(context, "Stream synced with Databricks ML", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSettingsClick = {
                                showSettingsDialog = true
                            }
                        )
                    },
                    bottomBar = { BottomNavigationBar(navController = navController) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                uiState = uiState,
                                viewModel = vitalsViewModel
                            )
                        }
                        composable("research") {
                            ResearchScreen(
                                uiState = uiState,
                                viewModel = vitalsViewModel
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                uiState = uiState,
                                viewModel = vitalsViewModel
                            )
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsDialog(
                            settings = uiState.settings,
                            onDismiss = { showSettingsDialog = false },
                            onSaveSettings = { freq, live, caching, deid ->
                                vitalsViewModel.updateSettings(
                                    streamingFrequencyHz = freq,
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
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerRotation"
    )

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
            IconButton(onClick = onSyncClick) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Stream",
                    tint = Color.White,
                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                )
            }
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
        NavItem("Research", "research", Icons.Default.Science),
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
                        icon = { Icon(Icons.Default.Science, contentDescription = "Research") },
                        label = { Text("Research", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        selected = false,
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
                    viewModel = viewModel()
                )
            }
        }
    }
}
