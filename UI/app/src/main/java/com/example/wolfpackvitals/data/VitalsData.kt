package com.example.wolfpackvitals.data

import androidx.compose.ui.graphics.Color
import com.example.wolfpackvitals.ui.theme.*
import com.example.wolfpackvitals.data.replay.Patient16ReplayState
import com.example.wolfpackvitals.data.analysis.ON_DEVICE_MODEL_VERSION
import com.example.wolfpackvitals.data.network.MetricResult
import java.util.Locale

data class HeartRateReading(
    val avgBpm: Int = 0,
    val restingBpm: Int = 61,
    val minBpm: Int = 0,
    val maxBpm: Int = 0,
    val selectedRange: String = "24H",
    val isLineMode: Boolean = true,
    val lastUpdatedHour: String? = null,
    val hourlyHistory: List<Pair<String, Int>> = emptyList(),
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
    val confidenceScore: String = "—",
    val recommendations: List<String> = emptyList(),
    val progress: Float,
    val progressColorHex: Long = 0xFF3B82F6,
    val leftLabel: String,
    val centerLabel: String = "",
    val rightLabel: String,
    val rightLabelIsRed: Boolean = false,
    val result: MetricResult? = null,
    val severity: String? = null,
    val restorativeStart: Float? = null,
    val restorativeEnd: Float? = null,
    val modelVersion: String? = null,
    val thresholdVersion: String? = null,
    val windowId: String? = null,
    val riskProbabilityText: String = "—",

)

data class DatabricksPipelineStatus(
    val pipelineName: String = "Prediabetes Risk Estimate",
    val statusDescription: String = "",
    val riskLabel: String = "Prediabetes risk:",
    val riskLevelText: String = "Collecting data",
    val isSyncing: Boolean = false,
    val lastSyncedText: String = "Collecting data",
    val clusterStatus: String = "Awaiting analysis",
    val workspaceUrl: String = "Unavailable",
    val analysis: BiomarkerAnalysis = initialAnalysis("prediabetes_risk")
)

data class UserProfile(
    val name: String = "Wolfpack User",
    val id: String = "WOLF-9482-B",
    val initials: String = "WP",
    val email: String = "user@ncsu.edu",
    val studyCohort: String = "",
    val databricksConnected: Boolean = false,
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
    val isLiveStreamingEnabled: Boolean = false,
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
        initialAnalysis("hr_eda"), initialAnalysis("glucose_variability"), initialAnalysis("hrv")
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
            lastSyncTime = "Active (Connected)",
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

fun initialAnalysis(metric: String): BiomarkerAnalysis {
    val title = when (metric) {
        "hr_eda" -> "Heart & Skin Patterns"
        "glucose_variability" -> "Glucose Variability"
        "hrv" -> "Heart Rate Variability"
        else -> "Prediabetes Risk Estimate"
    }
    val technical = when (metric) {
        "hr_eda" -> "HR–EDA / Cross-Fuzzy Entropy (X-FuzzEn)"
        "glucose_variability" -> "Glycemic Volatility / Postprandial Slope"
        "hrv" -> "HRV / LF/HF analysis"
        else -> "Databricks prediction workflow"
    }
    return BiomarkerAnalysis(id = metric, title = title, subtitle = technical, badgeText = "Collecting data",
        badgeBg = Color(0xFFF3F4F6), badgeColor = Color(0xFF6B7280),
        description = "Waiting for the first completed analysis window.",
        clinicalInsight = when (metric) {
            "hr_eda" -> "Temporal HR–EDA relationships require synchronized source readings and a validated calculation workflow."
            "glucose_variability" -> "Glucose variability requires an adequate glucose window and a model-defined reference range."
            "hrv" -> "HRV analysis requires IBI sequences and model-defined ranges. LF/HF alone does not establish sympathovagal balance or restorative tone."
            else -> "Prediabetes risk requires a verified prediction workflow with the required sensor coverage and relevant context."
        }, referenceRange = "Unavailable", progress = 0f,
        leftLabel = when (metric) { "hr_eda" -> "Typical pattern"; "hrv" -> "Below range"; else -> "Low" },
        centerLabel = when (metric) { "glucose_variability" -> "Moderate"; "hrv" -> "Validated range"; else -> "" },
        rightLabel = when (metric) { "hr_eda" -> "Elevated risk"; "hrv" -> "Above range"; else -> "High" })
}

/** One category drives severity, badge, color and filter membership. The position is model-configured. */
fun BiomarkerAnalysis.withResult(next: MetricResult): BiomarkerAnalysis {
    if (!next.available) return pendingAnalysis(next.reason ?: "Analysis is unavailable.", unavailable = true)
    val category = requireNotNull(next.category)
    val numeric = String.format(Locale.US, "%.3g %s", next.value, next.unit)
    val source = if (next.modelVersion == ON_DEVICE_MODEL_VERSION) " $ON_DEVICE_MODEL_VERSION." else ""
    val text = if (id == "glucose_variability" && next.supportsPostmealSpikes) {
        "$numeric • ${category.label}. The model indicates greater likelihood of spikes after meals."
    } else "$numeric • ${category.label}.$source"
    return copy(result = next, badgeText = category.badge, badgeBg = Color(category.backgroundHex),
        badgeColor = Color(category.colorHex), severity = category.label,
        description = text, clinicalInsight = next.explanation ?: initialAnalysis(id).clinicalInsight,
        referenceRange = requireNotNull(next.referenceRange),
        confidenceScore = next.confidence?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "—",
        riskProbabilityText = next.riskProbability?.let { String.format(Locale.US, "%.1f%%", it * 100) } ?: "—",
        progress = requireNotNull(next.barPosition), progressColorHex = category.colorHex,
        restorativeStart = next.restorativeStart, restorativeEnd = next.restorativeEnd,
        modelVersion = next.modelVersion, thresholdVersion = next.thresholdVersion, windowId = next.windowId)
}

fun BiomarkerAnalysis.pendingAnalysis(message: String, unavailable: Boolean = false): BiomarkerAnalysis {
    val retained = result?.available == true
    return copy(badgeText = if (retained) "Outdated" else if (unavailable) "Unavailable" else "Collecting data",
        badgeBg = Color(0xFFF3F4F6), badgeColor = Color(0xFF6B7280), progressColorHex = 0xFF9CA3AF,
        progress = if (retained) progress else 0f,
        description = if (retained) "$message Previous result: ${severity ?: "Unavailable"}." else message)
}
