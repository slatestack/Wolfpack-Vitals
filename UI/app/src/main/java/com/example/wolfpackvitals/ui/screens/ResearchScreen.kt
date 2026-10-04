package com.example.wolfpackvitals.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.DashboardUiState
import com.example.wolfpackvitals.data.ResearchPublication
import com.example.wolfpackvitals.data.ResearchStudy
import com.example.wolfpackvitals.ui.VitalsViewModel
import com.example.wolfpackvitals.ui.components.ExportDataDialog
import com.example.wolfpackvitals.ui.components.StudyDetailDialog
import com.example.wolfpackvitals.ui.theme.*

@Composable
fun ResearchScreen(
    uiState: DashboardUiState,
    viewModel: VitalsViewModel
) {
    val context = LocalContext.current

    // State for dialogs
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedStudyForDetail by remember { mutableStateOf<ResearchStudy?>(null) }
    var selectedPublicationForDetail by remember { mutableStateOf<ResearchPublication?>(null) }

    // Sensor permissions state
    var sharePpg by remember { mutableStateOf(true) }
    var shareEda by remember { mutableStateOf(true) }
    var shareTemp by remember { mutableStateOf(true) }
    var shareAcc by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Page Title & Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "The 4 Aces Research",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = NCStateDarkGray
            )
            Text(
                text = "Participate in clinical research trials and export telemetry for clinical assessment.",
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
        }

        // 1. Export Raw Data Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = Color(0xFFF3F4F6),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = NCStateRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Export Raw Telemetry",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                        Text(
                            text = "Download clinical stream in CSV, Parquet, or JSON.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }

                Button(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Configure & Export Dataset",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Active Clinical Trials Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Clinical Research Cohorts",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NCStateDarkGray
            )

            uiState.studies.forEach { study ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = study.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = NCStateDarkGray
                                )
                                Text(
                                    text = study.institution,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            Surface(
                                color = if (study.isEnrolled) StableGreenBg else Color(0xFFF3F4F6),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (study.isEnrolled) "Enrolled" else "Open",
                                    color = if (study.isEnrolled) StableGreen else Color(0xFF6B7280),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = study.description,
                            fontSize = 12.sp,
                            color = Color(0xFF4B5563),
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "IRB: ${study.irbNumber}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            OutlinedButton(
                                onClick = { selectedStudyForDetail = study },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (study.isEnrolled) "Manage Protocol" else "View & Join",
                                    fontSize = 12.sp,
                                    color = NCStateDarkGray
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Sensor Modality Permissions
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sensor Telemetry Stream Access",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NCStateDarkGray
                )
                Text(
                    text = "Control physiological modalities streamed to research pipelines.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                HorizontalDivider(color = Color(0xFFF3F4F6))

                SensorToggleRow(
                    name = "Photoplethysmography (PPG 64Hz)",
                    desc = "Cardiac intervals & heart rate",
                    checked = sharePpg,
                    onCheckedChange = {
                        sharePpg = it
                        Toast.makeText(context, "PPG streaming ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                )

                SensorToggleRow(
                    name = "Electrodermal Activity (EDA 4Hz)",
                    desc = "Galvanic skin conductance & autonomic arousal",
                    checked = shareEda,
                    onCheckedChange = {
                        shareEda = it
                        Toast.makeText(context, "EDA streaming ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                )

                SensorToggleRow(
                    name = "Peripheral Skin Temperature (4Hz)",
                    desc = "Microvascular vasodilation index",
                    checked = shareTemp,
                    onCheckedChange = {
                        shareTemp = it
                        Toast.makeText(context, "Skin temperature streaming ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                )

                SensorToggleRow(
                    name = "Tri-Axial Accelerometer (32Hz)",
                    desc = "Motion artifact cancellation & cadence",
                    checked = shareAcc,
                    onCheckedChange = {
                        shareAcc = it
                        Toast.makeText(context, "Accelerometer streaming ${if (it) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 4. Published Research Whitepapers
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Key Lab Publications",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NCStateDarkGray
            )

            uiState.publications.forEach { pub ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pub.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NCStateDarkGray
                        )
                        Text(
                            text = "${pub.authors} • ${pub.journal} (${pub.year})",
                            fontSize = 12.sp,
                            color = NCStateRed,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DOI: ${pub.doi}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            TextButton(
                                onClick = { selectedPublicationForDetail = pub },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Read Abstract", fontSize = 12.sp, color = NCStateDarkGray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- Dialogs ---
    if (showExportDialog) {
        ExportDataDialog(onDismiss = { showExportDialog = false })
    }

    selectedStudyForDetail?.let { study ->
        StudyDetailDialog(
            study = study,
            onToggleEnrollment = { viewModel.toggleStudyEnrollment(study.id) },
            onDismiss = { selectedStudyForDetail = null }
        )
    }

    selectedPublicationForDetail?.let { pub ->
        AlertDialog(
            onDismissRequest = { selectedPublicationForDetail = null },
            title = {
                Text(pub.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NCStateDarkGray)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pub.authors} — ${pub.journal} (${pub.year})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NCStateRed)
                    Text(pub.abstractText, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF374151))
                    Text("DOI: ${pub.doi}", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedPublicationForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NCStateDarkGray)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun SensorToggleRow(
    name: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NCStateDarkGray)
            Text(desc, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NCStateRed)
        )
    }
}
