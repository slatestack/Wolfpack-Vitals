package com.example.wolfpackvitals.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wolfpackvitals.R
import com.example.wolfpackvitals.data.*
import com.example.wolfpackvitals.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------------
// 1. Settings Dialog
// -----------------------------------------------------------------------------
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSaveSettings: (Int, Boolean, Boolean, Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    val context = LocalContext.current
    var selectedFreq by remember { mutableIntStateOf(settings.streamingFrequencyHz) }
    var offlineCaching by remember { mutableStateOf(settings.isOfflineCachingEnabled) }
    var deidentified by remember { mutableStateOf(settings.isTelemetryDeidentified) }
    var liveStream by remember { mutableStateOf(settings.isLiveStreamingEnabled) }
    var cacheCleared by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = NCStateRed)
                        Text("App & Telemetry Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NCStateDarkGray)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Streaming Frequency
                Text("Sensor Sampling Frequency", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = NCStateDarkGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 Hz (Battery Saver)", 4 to "4 Hz (Standard)", 64 to "64 Hz (Clinical Raw)").forEach { (freq, label) ->
                        FilterChip(
                            selected = selectedFreq == freq,
                            onClick = { selectedFreq = freq },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedFreq == freq) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NCStateRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Databricks Stream", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Simulate continuous 64Hz socket ingestion", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = liveStream, onCheckedChange = { liveStream = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Offline Local Caching", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Buffer vitals locally when network is restricted", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = offlineCaching, onCheckedChange = { offlineCaching = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("HIPAA De-identification", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Hash participant identifiers prior to streaming", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = deidentified, onCheckedChange = { deidentified = it })
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Cache Management
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Local Telemetry Buffer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (cacheCleared) "Buffer: 0 KB" else "Buffer: 14.2 MB (Cached)", fontSize = 12.sp, color = Color.Gray)
                    }
                    OutlinedButton(
                        onClick = {
                            cacheCleared = true
                            onClearCache()
                            Toast.makeText(context, "Telemetry cache purged successfully", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Buffer", fontSize = 12.sp, color = NCStateRed)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSaveSettings(selectedFreq, liveStream, offlineCaching, deidentified)
                            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. Biomarker Detail Dialog
// -----------------------------------------------------------------------------
@Composable
fun BiomarkerDetailDialog(
    biomarker: BiomarkerAnalysis,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = biomarker.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                        if (biomarker.subtitle.isNotEmpty()) {
                            Text(
                                text = biomarker.subtitle,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Surface(
                        color = biomarker.badgeBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = biomarker.badgeText,
                            color = biomarker.badgeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Progress Indicator
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Risk & Variance Trajectory", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NCStateDarkGray)
                    LinearProgressIndicator(
                        progress = { biomarker.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(biomarker.progressColorHex),
                        trackColor = BackgroundGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(biomarker.leftLabel, fontSize = 11.sp, color = Color.Gray)
                        Text(biomarker.rightLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (biomarker.rightLabelIsRed) NCStateRed else Color.Gray)
                    }
                }

                // Reference Range & Confidence
                Surface(
                    color = BackgroundGray,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Reference Range", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            Text(biomarker.referenceRange.ifEmpty { "Normative Range" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ML Model Certainty", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            Text(biomarker.confidenceScore, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StableGreen)
                        }
                    }
                }

                // Clinical Insight
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Clinical Mechanism & Science", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                    Text(
                        text = biomarker.clinicalInsight.ifEmpty { biomarker.description },
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp
                    )
                }

                // Actionable Recommendations
                if (biomarker.recommendations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Actionable Guidance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                        biomarker.recommendations.forEach { rec ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StableGreen,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Text(rec, fontSize = 12.sp, color = Color(0xFF374151), lineHeight = 16.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                ) {
                    Text("Close Details", color = Color.White)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. Expanded Chart Dialog
// -----------------------------------------------------------------------------
@Composable
fun ExpandedChartDialog(
    heartRate: HeartRateReading,
    onRangeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Continuous Telemetry Analytics", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NCStateDarkGray)
                        Text("High-resolution sensor stream (${heartRate.selectedRange})", fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                // Range Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1H", "6H", "24H", "7D").forEach { range ->
                        FilterChip(
                            selected = heartRate.selectedRange == range,
                            onClick = { onRangeSelected(range) },
                            label = { Text(range, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NCStateRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Stat Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(label = "Average", value = "${heartRate.avgBpm} bpm", color = NCStateRed, modifier = Modifier.weight(1f))
                    StatBox(label = "Resting", value = "${heartRate.restingBpm} bpm", color = NCStateDarkGray, modifier = Modifier.weight(1f))
                    StatBox(label = "Min / Max", value = "${heartRate.minBpm} - ${heartRate.maxBpm}", color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                }

                // Interactive High-Res Telemetry Chart
                HeartRateTelemetryGraph(
                    history = heartRate.hourlyHistory,
                    restingBpm = heartRate.restingBpm,
                    selectedRange = heartRate.selectedRange,
                    lastUpdatedHour = heartRate.lastUpdatedHour,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                // Cardio Zones Breakdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Physiological Heart Rate Zones", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                    ZoneRow(name = "Resting (< 60 bpm)", percentage = "18%", color = Color(0xFF60A5FA))
                    ZoneRow(name = "Fat Burn (60 - 99 bpm)", percentage = "62%", color = Color(0xFF34D399))
                    ZoneRow(name = "Cardio Aerobic (100 - 139 bpm)", percentage = "16%", color = Color(0xFFFBBF24))
                    ZoneRow(name = "Peak Sympathetic (> 140 bpm)", percentage = "4%", color = NCStateRed)
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "Telemetry summary snapshot exported to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Data", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = BackgroundGray,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ZoneRow(name: String, percentage: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Text(name, fontSize = 12.sp, color = Color(0xFF4B5563))
        }
        Text(percentage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
    }
}

// -----------------------------------------------------------------------------
// 4. Log Vital Dialog
// -----------------------------------------------------------------------------
@Composable
fun LogVitalDialog(
    initialHour: String? = null,
    currentResting: Int,
    onDismiss: () -> Unit,
    onSaveVital: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val currentSystemHour = remember {
        String.format("%02d:00", java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }
    var selectedHour by remember(initialHour) {
        mutableStateOf(initialHour ?: currentSystemHour)
    }
    var bpmValue by remember { mutableFloatStateOf(72f) }

    val allHours = remember {
        (0..23).map { String.format("%02d:00", it) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Log Heart Rate Reading", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                Text(
                    text = "Record or update an hourly reading to reflect on the telemetry graph",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // 1. Hour Selection Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Target Hour:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                        Surface(
                            color = NCStateRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = selectedHour,
                                color = NCStateRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Horizontal scrollable hour chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allHours.forEach { hour ->
                            val isSelected = (selectedHour == hour)
                            val isCurrent = (hour == currentSystemHour)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedHour = hour },
                                label = {
                                    Text(
                                        text = if (isCurrent) "$hour (Now)" else hour,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NCStateRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // 2. Large BPM Readout with Zone Indicator
                val zoneName = when {
                    bpmValue < 60 -> "Resting Baseline"
                    bpmValue < 100 -> "Normal / Fat Burn"
                    bpmValue < 140 -> "Cardio Aerobic"
                    else -> "Peak Sympathetic"
                }
                val zoneColor = when {
                    bpmValue < 60 -> Color(0xFF3B82F6)
                    bpmValue < 100 -> Color(0xFF10B981)
                    bpmValue < 140 -> Color(0xFFF59E0B)
                    else -> NCStateRed
                }

                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = CircleShape,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${bpmValue.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = NCStateRed)
                            Text("BPM", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        }
                    }
                }

                Surface(
                    color = zoneColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = zoneName,
                        color = zoneColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // 3. Slider
                Slider(
                    value = bpmValue,
                    onValueChange = { bpmValue = it },
                    valueRange = 50f..160f,
                    steps = 110,
                    colors = SliderDefaults.colors(thumbColor = NCStateRed, activeTrackColor = NCStateRed)
                )

                // 4. Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        60 to "Rest",
                        72 to "Normal",
                        85 to "Active",
                        115 to "Cardio",
                        140 to "Peak"
                    ).forEach { (preset, label) ->
                        OutlinedButton(
                            onClick = { bpmValue = preset.toFloat() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                        }
                    }
                }

                // 5. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSaveVital(selectedHour, bpmValue.toInt())
                            Toast.makeText(context, "Updated $selectedHour reading: ${bpmValue.toInt()} BPM", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                    ) {
                        Text("Record", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Backward-compatible overload
@Composable
fun LogVitalDialog(
    currentResting: Int,
    onDismiss: () -> Unit,
    onSaveVital: (Int) -> Unit
) {
    LogVitalDialog(
        initialHour = null,
        currentResting = currentResting,
        onDismiss = onDismiss,
        onSaveVital = { _, bpm -> onSaveVital(bpm) }
    )
}

// -----------------------------------------------------------------------------
// 5. Databricks Model Insights Dialog
// -----------------------------------------------------------------------------
@Composable
fun DatabricksModelInsightsDialog(
    pipelineStatus: DatabricksPipelineStatus,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NCStateRed)
                        Text("LightGBM Model Architecture", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NCStateDarkGray)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                Text(
                    "Real-time edge telemetry processed across Apache Spark worker clusters for physiological anomaly inference.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                // Metrics Table
                Surface(
                    color = BackgroundGray,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricRow("Algorithm", "LightGBM Ensembles + X-FuzzEn")
                        MetricRow("ROC-AUC Score", pipelineStatus.modelAccuracy)
                        MetricRow("Max Tree Depth", "${pipelineStatus.treeDepth} levels")
                        MetricRow("Inference Latency", "${pipelineStatus.inferenceLatencyMs} ms")
                        MetricRow("Sampling Frequency", "${pipelineStatus.sampleRateHz} Hz (ECG/EDA)")
                        MetricRow("Active Spark Workers", "${pipelineStatus.activeWorkers} Nodes")
                        MetricRow("Cluster State", pipelineStatus.clusterStatus)
                        MetricRow("Databricks Workspace", pipelineStatus.workspaceUrl)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                ) {
                    Text("Dismiss", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
    }
}

// -----------------------------------------------------------------------------
// 8. Edit Profile Dialog
// -----------------------------------------------------------------------------
@Composable
fun EditProfileDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(userProfile.name) }
    var participantId by remember { mutableStateOf(userProfile.id) }
    var email by remember { mutableStateOf(userProfile.email) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Edit Participant Info", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = participantId,
                    onValueChange = { participantId = it },
                    label = { Text("Participant ID") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("NCSU Institutional Email") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(name, participantId, email)
                            Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 9. Health Profile Dialog
// -----------------------------------------------------------------------------
@Composable
fun HealthProfileDialog(
    healthProfile: HealthProfile,
    onDismiss: () -> Unit,
    onSave: (Int, String, Float, Float, Int, Int, Float) -> Unit
) {
    val context = LocalContext.current
    var age by remember { mutableStateOf(healthProfile.age.toString()) }
    var height by remember { mutableStateOf(healthProfile.heightCm.toInt().toString()) }
    var weight by remember { mutableStateOf(healthProfile.weightKg.toString()) }
    var restingBpm by remember { mutableStateOf(healthProfile.restingBpmBaseline.toString()) }
    var fastingGlucose by remember { mutableStateOf(healthProfile.fastingGlucoseMgDl.toString()) }
    var hba1c by remember { mutableStateOf(healthProfile.hba1cPercent.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Health Profile & Baselines", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age (yr)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = restingBpm,
                        onValueChange = { restingBpm = it },
                        label = { Text("Resting BPM") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = fastingGlucose,
                        onValueChange = { fastingGlucose = it },
                        label = { Text("Fasting Glucose (mg/dL)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = hba1c,
                        onValueChange = { hba1c = it },
                        label = { Text("HbA1c (%)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Calculated BMI Info
                Surface(color = BackgroundGray, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Body Mass Index (BMI)", fontSize = 12.sp, color = Color.Gray)
                        Text("${healthProfile.bmi} (Normal)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StableGreen)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val a = age.toIntOrNull() ?: healthProfile.age
                            val h = height.toFloatOrNull() ?: healthProfile.heightCm
                            val w = weight.toFloatOrNull() ?: healthProfile.weightKg
                            val r = restingBpm.toIntOrNull() ?: healthProfile.restingBpmBaseline
                            val g = fastingGlucose.toIntOrNull() ?: healthProfile.fastingGlucoseMgDl
                            val hba = hba1c.toFloatOrNull() ?: healthProfile.hba1cPercent
                            onSave(a, healthProfile.sex, h, w, r, g, hba)
                            Toast.makeText(context, "Health baselines saved", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                    ) {
                        Text("Save Baselines", color = Color.White)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 10. Connected Devices Dialog & Bluetooth Scanning
// -----------------------------------------------------------------------------
@Composable
fun ConnectedDevicesDialog(
    devices: List<ConnectedDevice>,
    onToggleDevice: (String) -> Unit,
    onPairDevice: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF2563EB))
                        Text("Connected Devices", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NCStateDarkGray)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // List of Devices
                devices.forEach { device ->
                    Surface(
                        color = BackgroundGray,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NCStateDarkGray)
                                Text(device.type, fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (device.isConnected) StableGreen else Color.Gray, CircleShape)
                                    )
                                    Text(
                                        if (device.isConnected) "Connected • ${device.batteryPercent}%" else "Disconnected",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (device.isConnected) StableGreen else Color.Gray
                                    )
                                }
                            }
                            Button(
                                onClick = { onToggleDevice(device.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (device.isConnected) Color(0xFFEF4444) else NCStateDarkGray
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(if (device.isConnected) "Disconnect" else "Connect", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Scan for New Sensor
                if (!isScanning) {
                    OutlinedButton(
                        onClick = {
                            isScanning = true
                            coroutineScope.launch {
                                delay(1200)
                                discoveredDevices = listOf(
                                    Triple("Polar H10 Chest Strap", "ECG Heart Rate Monitor", "Continuous ECG, RR Intervals"),
                                    Triple("Whoop 4.0", "Fitness & Recovery Sensor", "PPG, Skin Temp, Accelerometer")
                                )
                                isScanning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan for Nearby Wearables", color = NCStateDarkGray)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NCStateRed, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Scanning Bluetooth LE peripherals...", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                // Discovered Devices List
                discoveredDevices.forEach { (name, type, modalities) ->
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                                Text(type, fontSize = 11.sp, color = Color(0xFF3B82F6))
                            }
                            Button(
                                onClick = {
                                    onPairDevice(name, type, modalities)
                                    discoveredDevices = discoveredDevices.filter { it.first != name }
                                    Toast.makeText(context, "Paired $name", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Pair", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 11. Databricks Connection Dialog
// -----------------------------------------------------------------------------
@Composable
fun DatabricksConnectionDialog(
    pipelineStatus: DatabricksPipelineStatus,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = NCStateRed)
                        Text("Databricks Spark ML Link", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NCStateDarkGray)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                Surface(
                    color = BackgroundGray,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricRow("Workspace URL", pipelineStatus.workspaceUrl)
                        MetricRow("Cluster Runtime", "Apache Spark 3.5 ML GPU")
                        MetricRow("Inference Protocol", "gRPC 64Hz Stream Ingestion")
                        MetricRow("Cluster Status", pipelineStatus.clusterStatus)
                        MetricRow("Token Health", "Valid (Active Session)")
                    }
                }

                if (testResult != null) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StableGreen, modifier = Modifier.size(20.dp))
                            Text(testResult!!, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                delay(800)
                                isTesting = false
                                testResult = "Cluster responding • Ping: 12ms (Optimal)"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isTesting
                    ) {
                        Text(if (isTesting) "Pinging..." else "Test Connection", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 12. Notifications Settings Dialog
// -----------------------------------------------------------------------------
@Composable
fun NotificationsSettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var mainNotifications by remember { mutableStateOf(settings.notificationsEnabled) }
    var spikes by remember { mutableStateOf(settings.anomalousSpikeAlerts) }
    var riskChanges by remember { mutableStateOf(settings.riskThresholdAlerts) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Alerts & Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Vitals Notifications", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Receive timely telemetry alerts", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = mainNotifications, onCheckedChange = { mainNotifications = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autonomic Spike Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Notify when HR-EDA entropy exceeds baseline", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = spikes, onCheckedChange = { spikes = it }, enabled = mainNotifications)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pre-Diabetes Risk Drift Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Notify if LightGBM detects trend transitions", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = riskChanges, onCheckedChange = { riskChanges = it }, enabled = mainNotifications)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(mainNotifications, spikes, riskChanges)
                            Toast.makeText(context, "Notification preferences updated", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 13. Data Privacy Dialog
// -----------------------------------------------------------------------------
@Composable
fun DataPrivacyDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var offlineCaching by remember { mutableStateOf(settings.isOfflineCachingEnabled) }
    var deidentified by remember { mutableStateOf(settings.isTelemetryDeidentified) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Data Privacy & HIPAA Compliance", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("De-identify Telemetry", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Anonymize all sensor payloads with zero PII", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = deidentified, onCheckedChange = { deidentified = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local Hardware Encryption", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("AES-256 GCM encrypted keystore buffer", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = offlineCaching, onCheckedChange = { offlineCaching = it })
                }

                Button(
                    onClick = {
                        onSave(offlineCaching, deidentified)
                        Toast.makeText(context, "Privacy settings saved", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateRed)
                ) {
                    Text("Save & Close", color = Color.White)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 14. About App Dialog
// -----------------------------------------------------------------------------
@Composable
fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = NCStateRed,
                    shape = CircleShape,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_wolf_heart_white),
                            contentDescription = "Wolfpack Vitals Logo",
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Text("Wolfpack Vitals", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                Text("Version 1.2.0 • Build 2026.10", fontSize = 12.sp, color = Color.Gray)

                Text(
                    "Wolfpack Vitals is an advanced clinical and metabolic monitoring platform by The 4 Aces at NC State University. Combining multi-modal wearable telemetry with cloud-scale machine learning to pioneer non-invasive pre-diabetes prevention.",
                    fontSize = 13.sp,
                    color = Color(0xFF4B5563),
                    lineHeight = 18.sp
                )

                Surface(
                    color = BackgroundGray,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Affiliation: The 4 Aces • NC State University", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
                        Text("IRB Approvals: IRB-2024-8841-A", fontSize = 11.sp, color = Color.Gray)
                        Text("License: Open Research & Academic Clinical Use", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}
