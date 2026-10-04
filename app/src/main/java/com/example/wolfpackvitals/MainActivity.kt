package com.example.wolfpackvitals

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.VitalsViewModel
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

                Scaffold(
                    topBar = {
                        TopHeaderBar(
                            onSettingsClick = {
                                Toast.makeText(context, "Settings Clicked", Toast.LENGTH_SHORT).show()
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
                            DashboardScreen(uiState = uiState)
                        }
                        composable("research") {
                            ResearchScreen()
                        }
                        composable("profile") {
                            ProfileScreen(userProfile = uiState.userProfile)
                        }
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heart",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text("Wolfpack Vitals", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                    indicatorColor = Color.White
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
                            indicatorColor = Color.White
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
                            indicatorColor = Color.White
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
                            indicatorColor = Color.White
                        ),
                        onClick = {}
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                DashboardScreen(uiState = DashboardUiState())
            }
        }
    }
}
