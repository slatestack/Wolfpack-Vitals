package com.example.wolfpackvitals.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.DatabricksPipelineStatus
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun DatabricksMLCard(
    pipelineStatus: DatabricksPipelineStatus,
    onSync: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth().testTag("analysis-card-prediabetes_risk").clickable(onClick = onClick)
    ) {
        Box(Modifier.background(Brush.verticalGradient(listOf(NCStateDarkGray, Color.Black))).padding(18.dp)) {
            Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.End)) {
                Icon(Icons.Default.Storage, contentDescription = null,
                    tint = Color.White.copy(alpha = 0.06f), modifier = Modifier.size(110.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NCStateRed, modifier = Modifier.size(20.dp))
                        Text(pipelineStatus.pipelineName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(if (pipelineStatus.isSyncing) "Analyzing" else pipelineStatus.lastSyncedText,
                            color = Color(0xFFD1D5DB), fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("ANALYSIS", color = Color(0xFF9CA3AF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = onSync, enabled = !pipelineStatus.isSyncing, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync analysis", tint = Color.White)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).background(pipelineStatus.analysis.badgeColor, CircleShape))
                                Text(pipelineStatus.clusterStatus, color = pipelineStatus.analysis.badgeColor,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(pipelineStatus.riskLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))) {
                                Text(pipelineStatus.riskLevelText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        AnalysisBar(pipelineStatus.analysis)
                        Spacer(Modifier.height(8.dp))
                        Text(pipelineStatus.analysis.description, color = Color(0xFFD1D5DB), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
