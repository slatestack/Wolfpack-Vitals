package com.example.wolfpackvitals.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.BiomarkerAnalysis
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.data.replay.ReplayPhase
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
    // State for interactive dialogs
    var showLogVitalDialog by remember { mutableStateOf(false) }
    var hourToLog by remember { mutableStateOf<String?>(null) }
    var showExpandedChartDialog by remember { mutableStateOf(false) }
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
            // Stored Patient 16 replay controls and observable session status.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PulsatingStreamingBadge(
                    isStreaming = uiState.isStreamingActive,
                    text = uiState.replay.badgeLabel,
                    onClick = { viewModel.togglePatient16Replay() }
                )
            }

            // 1. Average Heart Rate Card
            CurrentHeartRateCard(
                avgBpm = uiState.replay.averages?.heartbeat?.let { kotlin.math.round(it).toInt() }
                    ?: uiState.heartRate.avgBpm,
                restingBpm = uiState.heartRate.restingBpm,
                isStreaming = uiState.isStreamingActive,
                statusText = when (uiState.replay.phase) {
                    ReplayPhase.INACTIVE -> if (uiState.replay.dataError != null) "Source unavailable" else "Collecting data"
                    ReplayPhase.RUNNING -> if (uiState.replay.isLoading) "Loading" else "Collecting"
                    ReplayPhase.PAUSED -> "Paused"
                    ReplayPhase.COMPLETED -> "Complete"
                },
                onLogClick = {
                    hourToLog = null
                    showLogVitalDialog = true
                }
            )

            // 2. Hourly Heart Rate History Chart
            HeartRateChartCard(
                history = uiState.heartRate.hourlyHistory,
                restingBpm = uiState.heartRate.restingBpm,
                selectedRange = uiState.heartRate.selectedRange,
                lastUpdatedHour = uiState.heartRate.lastUpdatedHour,
                isLineMode = uiState.heartRate.isLineMode,
                onToggleStyle = { viewModel.toggleChartStyle() },
                onRangeSelected = { range -> viewModel.setTimeRange(range) },
                onLogHourClick = { hour ->
                    hourToLog = hour
                    showLogVitalDialog = true
                },
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
                            contentDescription = "Methodology info",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Analysis uses timestamped sensor windows and verified model reference ranges.",
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
                    "Stable" -> it.badgeText in listOf("Stable")
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
                onSync = { viewModel.syncPipeline() },
                onClick = { selectedBiomarkerForDetail = uiState.pipelineStatus.analysis }
            )

            Spacer(modifier = Modifier.height(60.dp))
        }

        // Floating Action Button for Quick Vital Logging
        FloatingActionButton(
            onClick = {
                hourToLog = null
                showLogVitalDialog = true
            },
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
            initialHour = hourToLog,
            currentResting = uiState.heartRate.restingBpm,
            onDismiss = {
                showLogVitalDialog = false
                hourToLog = null
            },
            onSaveVital = { hour, bpm ->
                viewModel.logManualVital(hour, bpm)
            }
        )
    }

    if (showExpandedChartDialog) {
        ExpandedChartDialog(
            heartRate = uiState.heartRate,
            onRangeSelected = { range -> viewModel.setTimeRange(range) },
            onToggleStyle = { viewModel.toggleChartStyle() },
            onDismiss = { showExpandedChartDialog = false }
        )
    }

    selectedBiomarkerForDetail?.let { selected ->
        val biomarker = if (selected.id == "prediabetes_risk") uiState.pipelineStatus.analysis
            else uiState.biomarkers.first { it.id == selected.id }
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
                    "Heart and skin patterns require synchronized HR–EDA readings. Heart rate variability uses IBI time series; LF/HF alone does not establish autonomic balance. Reference ranges and classifications are shown only when supplied by a verified model.",
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
