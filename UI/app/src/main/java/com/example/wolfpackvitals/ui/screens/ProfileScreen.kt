package com.example.wolfpackvitals.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.VitalsViewModel
import com.example.wolfpackvitals.ui.components.*
import com.example.wolfpackvitals.ui.theme.*

@Composable
fun ProfileScreen(
    uiState: DashboardUiState,
    viewModel: VitalsViewModel,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current

    // State for interactive dialogs
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showHealthProfileDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showDatabricksDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    val userProfile = uiState.userProfile
    val connectedDevicesCount = uiState.devices.count { it.isConnected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Participant Profile",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = NCStateDarkGray
        )

        // 1. User Avatar & Info Card (Clickable to Edit)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEditProfileDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = NCStateRed,
                        shape = CircleShape,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = userProfile.initials,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = userProfile.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                        Text(
                            text = "Participant ID: ${userProfile.id}",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = userProfile.email,
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                IconButton(onClick = { showEditProfileDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = NCStateDarkGray
                    )
                }
            }
        }

        // 2. Health & Physiological Parameters Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileOptionRow(
                    icon = Icons.Default.MedicalServices,
                    title = "Health Profile & Baselines",
                    badge = {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BMI: ${uiState.healthProfile.bmi}",
                                color = Color(0xFF2563EB),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    onClick = { showHealthProfileDialog = true }
                )

                HorizontalDivider(color = Color(0xFFF3F4F6))

                ProfileOptionRow(
                    icon = Icons.Default.Bluetooth,
                    title = "Connected Wearables",
                    badge = {
                        Surface(
                            color = if (connectedDevicesCount > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "$connectedDevicesCount Active",
                                color = if (connectedDevicesCount > 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    onClick = { showDevicesDialog = true }
                )

                HorizontalDivider(color = Color(0xFFF3F4F6))

                ProfileOptionRow(
                    icon = Icons.Default.Storage,
                    title = "Databricks Connection",
                    badge = {
                        Surface(
                            color = if (uiState.userProfile.databricksConnected) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (uiState.userProfile.databricksConnected) "Results available" else "Unverified",
                                color = if (uiState.userProfile.databricksConnected) Color(0xFF16A34A) else Color(0xFF6B7280),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    },
                    onClick = { showDatabricksDialog = true }
                )
            }
        }

        // 3. App Settings, Privacy & Security
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ProfileOptionRow(
                    icon = Icons.Default.Notifications,
                    title = "Alerts & Notifications",
                    onClick = { showNotificationsDialog = true }
                )

                HorizontalDivider(color = Color(0xFFF3F4F6))

                ProfileOptionRow(
                    icon = Icons.Default.Security,
                    title = "Data Privacy & HIPAA",
                    onClick = { showPrivacyDialog = true }
                )

                HorizontalDivider(color = Color(0xFFF3F4F6))

                ProfileOptionRow(
                    icon = Icons.Default.Info,
                    title = "About Wolfpack Vitals",
                    onClick = { showAboutDialog = true }
                )
            }
        }

        // 4. Participant Switch / Sign Out Button
        OutlinedButton(
            onClick = { showSignOutConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out / Switch Participant", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- Interactive Dialogs ---

    if (showEditProfileDialog) {
        EditProfileDialog(
            userProfile = uiState.userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, id, email ->
                viewModel.updateUserProfile(name, id, email)
            }
        )
    }

    if (showHealthProfileDialog) {
        HealthProfileDialog(
            healthProfile = uiState.healthProfile,
            onDismiss = { showHealthProfileDialog = false },
            onSave = { age, sex, height, weight, restingBpm, fastingGlucose, hba1c ->
                viewModel.updateHealthProfile(age, sex, height, weight, restingBpm, fastingGlucose, hba1c)
            }
        )
    }

    if (showDevicesDialog) {
        ConnectedDevicesDialog(
            devices = uiState.devices,
            onToggleDevice = { id -> viewModel.toggleDeviceConnection(id) },
            onPairDevice = { name, type, modalities -> viewModel.addPairedDevice(name, type, modalities) },
            onDismiss = { showDevicesDialog = false }
        )
    }

    if (showDatabricksDialog) {
        DatabricksConnectionDialog(
            pipelineStatus = uiState.pipelineStatus,
            onDismiss = { showDatabricksDialog = false }
        )
    }

    if (showNotificationsDialog) {
        NotificationsSettingsDialog(
            settings = uiState.settings,
            onDismiss = { showNotificationsDialog = false },
            onSave = { main, spikes, risks ->
                viewModel.updateSettings(
                    notificationsEnabled = main,
                    anomalousSpikes = spikes,
                    riskThresholds = risks
                )
            }
        )
    }

    if (showPrivacyDialog) {
        DataPrivacyDialog(
            settings = uiState.settings,
            onDismiss = { showPrivacyDialog = false },
            onSave = { caching, deid ->
                viewModel.updateSettings(
                    offlineCaching = caching,
                    deidentified = deid
                )
            }
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = {
                Text("Log Out?", fontWeight = FontWeight.Bold, color = NCStateDarkGray)
            },
            text = {
                Text("Sign out of active session and return to the login screen?", fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                        showSignOutConfirmation = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                ) {
                    Text("Log Out", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileOptionRow(
    icon: ImageVector? = null,
    title: String,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NCStateDarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = NCStateDarkGray,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (badge != null) {
            badge()
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
