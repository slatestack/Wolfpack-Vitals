package com.example.wolfpackvitals.data

import androidx.compose.ui.graphics.Color
import com.example.wolfpackvitals.ui.theme.*
import com.example.wolfpackvitals.data.replay.Patient16ReplayState

data class HeartRateReading(
    val avgBpm: Int = 70,
    val restingBpm: Int = 61,
    val minBpm: Int = 58,
    val maxBpm: Int = 118,
    val selectedRange: String = "24H",
    val lastUpdatedHour: String? = null,
    val hourlyHistory: List<Pair<String, Int>> = listOf(
        "00:00" to 62, "01:00" to 60, "02:00" to 58, "03:00" to 59,
        "04:00" to 61, "05:00" to 65, "06:00" to 72, "07:00" to 85,
        "08:00" to 98, "09:00" to 88, "10:00" to 92, "11:00" to 105,
        "12:00" to 118, "13:00" to 110, "14:00" to 95, "15:00" to 88,
        "16:00" to 82, "17:00" to 78, "18:00" to 92, "19:00" to 106,
        "20:00" to 85, "21:00" to 76, "22:00" to 70, "23:00" to 65
    ),
)

data class BiomarkerAnalysis(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val badgeText: String,
    val badgeBg: Color,
    val badgeColor: Color,
    val description: String,
    val clinicalInsight: String = "",
    val referenceRange: String = "",
    val confidenceScore: String = "94%",
    val recommendations: List<String> = emptyList(),
    val progress: Float,
    val progressColorHex: Long = 0xFF3B82F6,
    val leftLabel: String,
    val centerLabel: String = "",
    val rightLabel: String,
    val rightLabelIsRed: Boolean = false,
)

data class DatabricksPipelineStatus(
    val pipelineName: String = "Databricks LLM",
    val statusDescription: String = "",
    val riskLabel: String = "Pre-Diabetes Risk:",
    val riskLevelText: String = "Low (X-FuzzEn < 0.1)",
    val isSyncing: Boolean = false,
    val lastSyncedText: String = "Active",
    val modelAccuracy: String = "91.2%",
    val treeDepth: Int = 8,
    val inferenceLatencyMs: Int = 14,
    val sampleRateHz: Int = 64,
    val activeWorkers: Int = 8,
    val clusterStatus: String = "Running (Online)",
    val workspaceUrl: String = "dbc-wolfpack-vitals.cloud.databricks.com"
)

data class UserProfile(
    val name: String = "Wolfpack User",
    val id: String = "WOLF-9482-B",
    val initials: String = "WP",
    val email: String = "user@ncsu.edu",
    val studyCohort: String = "",
    val databricksConnected: Boolean = true,
)

data class HealthProfile(
    val age: Int = 28,
    val sex: String = "Male",
    val heightCm: Float = 180f,
    val weightKg: Float = 76.5f,
    val bmi: Float = 23.6f,
    val restingBpmBaseline: Int = 61,
    val fastingGlucoseMgDl: Int = 88,
    val hba1cPercent: Float = 5.3f,
)

data class ConnectedDevice(
    val id: String,
    val name: String,
    val type: String,
    val isConnected: Boolean,
    val batteryPercent: Int,
    val lastSyncTime: String,
    val modalities: String,
)

data class AppSettings(
    val streamingFrequencyHz: Int = 64,
    val isLiveStreamingEnabled: Boolean = true,
    val isOfflineCachingEnabled: Boolean = true,
    val isTelemetryDeidentified: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val anomalousSpikeAlerts: Boolean = true,
    val riskThresholdAlerts: Boolean = true,
    val hapticFeedback: Boolean = true,
)

data class DashboardUiState(
    val heartRate: HeartRateReading = HeartRateReading(),
    val biomarkers: List<BiomarkerAnalysis> = listOf(
        BiomarkerAnalysis(
            id = "hr_eda_entropy",
            title = "HR-EDA",
            subtitle = "Cross-Fuzzy Entropy",
            badgeText = "Stable",
            badgeBg = StableGreenBg,
            badgeColor = StableGreen,
            description = "Elevated HR-EDA coupling in the hypoglycemic range can be an early marker of prediabetic dysregulation.",
            clinicalInsight = "Cross-Fuzzy Entropy evaluates non-linear synchronization between autonomic cardiac acceleration and electrodermal sympathetic bursts. Normal values (< 0.12) signify healthy autonomic tone and glycemic homeostasis.",
            referenceRange = "0.02 - 0.12 (Normal)",
            confidenceScore = "94.6%",
            recommendations = listOf(
                "Autonomic nervous balance is within optimal limits.",
                "Maintain steady hydration to support accurate baseline EDA conductance.",
                "Continue nightly sleep schedule of 7-8 hours to stabilize baseline tone."
            ),
            progress = 0.35f,
            progressColorHex = 0xFF3B82F6,
            leftLabel = "Normoglycemic Pattern",
            rightLabel = "Elevated Risk",
            rightLabelIsRed = true,
        ),
        BiomarkerAnalysis(
            id = "glycemic_volatility",
            title = "Glycemic Volatility",
            subtitle = "Postprandial Slope",
            badgeText = "Monitoring",
            badgeBg = VolatilityYellowBg,
            badgeColor = VolatilityYellow,
            description = "Fused multimodal features (HR, ACC, Temp, EDA) predicting postprandial interstitial glucose slopes.",
            clinicalInsight = "Multimodal physiological sensors detect rapid glycemic excursions following carbohydrate intake before conventional symptoms occur. Moderate volatility suggests postprandial glycemic spikes.",
            referenceRange = "< 15 mg/dL/hr variance",
            confidenceScore = "89.1%",
            recommendations = listOf(
                "Incorporate a 10-15 minute moderate walk following meals.",
                "Prioritize dietary fiber and lean protein with carbohydrate intake.",
                "Log meal timing for correlation with continuous telemetry."
            ),
            progress = 0.65f,
            progressColorHex = 0xFFEAB308,
            leftLabel = "Low Volatility",
            centerLabel = "Moderate",
            rightLabel = "High Variance",
            rightLabelIsRed = false,
        ),
        BiomarkerAnalysis(
            id = "sympathetic_tone",
            title = "Sympathovagal Ratio",
            subtitle = "LF/HF HRV Balance",
            badgeText = "Optimal",
            badgeBg = StableGreenBg,
            badgeColor = StableGreen,
            description = "Frequency-domain heart rate variability index quantifying sympathetic versus parasympathetic autonomic balance.",
            clinicalInsight = "Low-frequency to high-frequency ratio (LF/HF) derived from 64Hz photoplethysmography intervals. An optimal ratio (1.0 - 2.0) reflects restorative parasympathetic tone.",
            referenceRange = "1.0 - 2.0 (Resting)",
            confidenceScore = "96.2%",
            recommendations = listOf(
                "Deep diaphragmatic breathing exercises promote high vagal tone.",
                "Resting intervals throughout intense cognitive work maintain balance."
            ),
            progress = 0.28f,
            progressColorHex = 0xFF10B981,
            leftLabel = "Parasympathetic",
            centerLabel = "Balanced",
            rightLabel = "Sympathetic Overdrive",
            rightLabelIsRed = true,
        )
    ),
    val pipelineStatus: DatabricksPipelineStatus = DatabricksPipelineStatus(),
    val userProfile: UserProfile = UserProfile(),
    val healthProfile: HealthProfile = HealthProfile(),
    val devices: List<ConnectedDevice> = listOf(
        ConnectedDevice(
            id = "emp_e4_01",
            name = "Empatica EmbracePlus",
            type = "Clinical Continuous Wearable",
            isConnected = true,
            batteryPercent = 88,
            lastSyncTime = "Active (Live 64Hz)",
            modalities = "PPG, EDA, Temp, 3-Axis ACC"
        ),
        ConnectedDevice(
            id = "dexcom_g7",
            name = "Dexcom G7 CGM",
            type = "Continuous Glucose Monitor",
            isConnected = true,
            batteryPercent = 94,
            lastSyncTime = "Synced 2m ago",
            modalities = "Interstitial Glucose (5min)"
        ),
        ConnectedDevice(
            id = "apple_watch_09",
            name = "Apple Watch Series 9",
            type = "Consumer Smartwatch",
            isConnected = false,
            batteryPercent = 72,
            lastSyncTime = "Standby (Bluetooth)",
            modalities = "HR, SpO2, Step Cadence"
        )
    ),
    val settings: AppSettings = AppSettings(),
    val selectedBiomarkerFilter: String = "All",
    val replay: Patient16ReplayState = Patient16ReplayState(),
) {
    val isStreamingActive: Boolean get() = replay.isAdvancing
}
