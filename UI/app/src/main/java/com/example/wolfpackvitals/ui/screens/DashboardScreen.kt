package com.example.wolfpackvitals.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.BiomarkerAnalysis
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.VitalsViewModel
import com.example.wolfpackvitals.ui.components.*
import com.example.wolfpackvitals.ui.theme.BackgroundGray
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    viewModel: VitalsViewModel
) {
    val context = LocalContext.current

    // State for interactive dialogs
    var showLogVitalDialog by remember { mutableStateOf(false) }
    var showExpandedChartDialog by remember { mutableStateOf(false) }
    var showModelInsightsDialog by remember { mutableStateOf(false) }
    var selectedBiomarkerForDetail by remember { mutableStateOf<BiomarkerAnalysis?>(null) }
    var showScienceInfoDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGray)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Ingestion Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsatingStreamingBadge(
                    isStreaming = uiState.isStreamingActive,
                    onClick = {
                        viewModel.toggleStreaming()
                        val msg = if (!uiState.isStreamingActive) "Streaming resumed (64Hz)" else "Streaming paused"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Cohort: ${uiState.userProfile.id}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                    }
                }
            }

            // 1. Average Heart Rate Card
            CurrentHeartRateCard(
                avgBpm = uiState.heartRate.avgBpm,
                restingBpm = uiState.heartRate.restingBpm,
                isStreaming = uiState.isStreamingActive,
                onLogClick = { showLogVitalDialog = true }
            )

            // 2. Hourly Heart Rate History Chart
            HeartRateChartCard(
                history = uiState.heartRate.hourlyHistory,
                selectedRange = uiState.heartRate.selectedRange,
                onRangeSelected = { range -> viewModel.setTimeRange(range) },
                onExpandClick = { showExpandedChartDialog = true }
            )

            // 3. Metabolic & Autonomic Analysis Header & Interactive Filters
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Metabolic & Autonomic Analysis",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                    }
                    IconButton(
                        onClick = { showScienceInfoDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Research science",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Continuous analysis inspired by The 4 Aces • NC State Wearable data. Detecting pre-diabetic markers via cross-fuzzy entropy (X-FuzzEn) and sensor modalities.",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 17.sp
                )

                // Biomarker Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Monitoring", "Stable").forEach { filter ->
                        FilterChip(
                            selected = uiState.selectedBiomarkerFilter == filter,
                            onClick = { viewModel.setBiomarkerFilter(filter) },
                            label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NCStateDarkGray,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 4. Pre-Diabetes Feature Cards (Filtered)
            val filteredBiomarkers = uiState.biomarkers.filter {
                when (uiState.selectedBiomarkerFilter) {
                    "Monitoring" -> it.badgeText == "Monitoring"
                    "Stable" -> it.badgeText in listOf("Stable", "Optimal")
                    else -> true
                }
            }

            filteredBiomarkers.forEach { biomarker ->
                AnalysisCard(
                    biomarker = biomarker,
                    onClick = { selectedBiomarkerForDetail = biomarker }
                )
            }

            // 5. Databricks ML Status Card
            DatabricksMLCard(
                pipelineStatus = uiState.pipelineStatus,
                onSyncClick = {
                    viewModel.syncPipeline {
                        Toast.makeText(context, "Pipeline execution complete: Risk status updated", Toast.LENGTH_SHORT).show()
                    }
                },
                onInsightsClick = { showModelInsightsDialog = true }
            )

            Spacer(modifier = Modifier.height(60.dp))
        }

        // Floating Action Button for Quick Vital Logging
        FloatingActionButton(
            onClick = { showLogVitalDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = NCStateRed,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Log Vital")
        }
    }

    // --- Dialogs ---
    if (showLogVitalDialog) {
        LogVitalDialog(
            currentResting = uiState.heartRate.restingBpm,
            onDismiss = { showLogVitalDialog = false },
            onSaveVital = { bpm ->
                viewModel.logManualVital(bpm)
            }
        )
    }

    if (showExpandedChartDialog) {
        ExpandedChartDialog(
            heartRate = uiState.heartRate,
            onRangeSelected = { range -> viewModel.setTimeRange(range) },
            onDismiss = { showExpandedChartDialog = false }
        )
    }

    if (showModelInsightsDialog) {
        DatabricksModelInsightsDialog(
            pipelineStatus = uiState.pipelineStatus,
            onDismiss = { showModelInsightsDialog = false }
        )
    }

    selectedBiomarkerForDetail?.let { biomarker ->
        BiomarkerDetailDialog(
            biomarker = biomarker,
            onDismiss = { selectedBiomarkerForDetail = null }
        )
    }

    if (showScienceInfoDialog) {
        AlertDialog(
            onDismissRequest = { showScienceInfoDialog = false },
            title = {
                Text("The 4 Aces • NC State Science", fontWeight = FontWeight.Bold, color = NCStateDarkGray)
            },
            text = {
                Text(
                    "Our mathematical models calculate Cross-Fuzzy Entropy (X-FuzzEn) between photoplethysmography (PPG) pulse waves and electrodermal conductance (EDA). Early autonomic blunting and sympathetic overdrive can precede laboratory-detectable fasting hyperglycemia by months.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF4B5563)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showScienceInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                ) {
                    Text("Understood", color = Color.White)
                }
            }
        )
    }
}
