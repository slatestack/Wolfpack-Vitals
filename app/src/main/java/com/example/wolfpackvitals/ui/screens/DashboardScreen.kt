package com.example.wolfpackvitals.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.ui.components.AnalysisCard
import com.example.wolfpackvitals.ui.components.CurrentHeartRateCard
import com.example.wolfpackvitals.ui.components.DatabricksMLCard
import com.example.wolfpackvitals.ui.components.HeartRateChartCard
import com.example.wolfpackvitals.ui.theme.BackgroundGray
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray

@Composable
fun DashboardScreen(uiState: DashboardUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Average Heart Rate Card
        CurrentHeartRateCard(
            avgBpm = uiState.heartRate.avgBpm,
            restingBpm = uiState.heartRate.restingBpm
        )

        // 2. Hourly Heart Rate History Chart
        HeartRateChartCard(history = uiState.heartRate.hourlyHistory)

        // 3. Metabolic & Autonomic Analysis Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Metabolic & Autonomic Analysis",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NCStateDarkGray
            )
            Text(
                text = "Continuous analysis inspired by Big IDEAs Lab Wearable data. Detecting pre-diabetic markers via cross-fuzzy entropy (X-FuzzEn) and sensor modalities.",
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                lineHeight = 18.sp
            )
        }

        // 4. Pre-Diabetes Feature Cards
        uiState.biomarkers.forEach { biomarker ->
            AnalysisCard(biomarker = biomarker)
        }

        // 5. Databricks ML Status Card
        DatabricksMLCard(pipelineStatus = uiState.pipelineStatus)

        Spacer(modifier = Modifier.height(32.dp))
    }
}
