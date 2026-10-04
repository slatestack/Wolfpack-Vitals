package com.example.wolfpackvitals.data

import androidx.compose.ui.graphics.Color
import com.example.wolfpackvitals.ui.theme.*

data class HeartRateReading(
    val avgBpm: Int = 70,
    val restingBpm: Int = 61,
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
    val progress: Float,
    val progressColorHex: Long = 0xFF3B82F6,
    val leftLabel: String,
    val centerLabel: String = "",
    val rightLabel: String,
    val rightLabelIsRed: Boolean = false,
)

data class DatabricksPipelineStatus(
    val pipelineName: String = "Databricks Spark ML",
    val statusDescription: String = "Processing real-time LightGBM ensemble models on 64Hz stream to assess physiological resilience.",
    val riskLabel: String = "Pre-Diabetes Risk:",
    val riskLevelText: String = "Low (X-FuzzEn < 0.1)",
    val isSyncing: Boolean = false,
    val lastSyncedText: String = "Synced 1m ago",
)

data class UserProfile(
    val name: String = "Wolfpack User",
    val id: String = "WOLF-9482-B",
    val initials: String = "WP",
    val databricksConnected: Boolean = true,
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
            progress = 0.35f,
            progressColorHex = 0xFF3B82F6,
            leftLabel = "Normoglycemic Pattern",
            rightLabel = "Elevated Risk",
            rightLabelIsRed = true,
        ),
        BiomarkerAnalysis(
            id = "glycemic_volatility",
            title = "Glycemic Volatility",
            subtitle = "",
            badgeText = "Monitoring",
            badgeBg = VolatilityYellowBg,
            badgeColor = VolatilityYellow,
            description = "Fused multimodal features (HR, ACC, Temp, EDA) predicting postprandial interstitial glucose slopes.",
            progress = 0.65f,
            progressColorHex = 0xFFEAB308,
            leftLabel = "Low Volatility",
            centerLabel = "Moderate",
            rightLabel = "High Variance",
            rightLabelIsRed = false,
        )
    ),
    val pipelineStatus: DatabricksPipelineStatus = DatabricksPipelineStatus(),
    val userProfile: UserProfile = UserProfile(),
)
