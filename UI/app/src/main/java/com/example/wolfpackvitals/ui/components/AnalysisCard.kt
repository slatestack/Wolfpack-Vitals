package com.example.wolfpackvitals.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.BiomarkerAnalysis
import com.example.wolfpackvitals.ui.theme.BackgroundGray
import com.example.wolfpackvitals.ui.theme.CardBackground
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun AnalysisCard(
    biomarker: BiomarkerAnalysis,
    onClick: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val iconBg = if (biomarker.id == "hr_eda_entropy") Color(0xFFE0F2FE) else Color(0xFFFEF9C3)
                    val iconTint = if (biomarker.id == "hr_eda_entropy") Color(0xFF0284C7) else Color(0xFFCA8A04)
                    val iconVector = if (biomarker.id == "hr_eda_entropy") Icons.Default.Water else Icons.AutoMirrored.Filled.TrendingUp

                    Surface(
                        color = iconBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = biomarker.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = NCStateDarkGray
                        )
                        if (biomarker.subtitle.isNotEmpty()) {
                            Text(
                                text = biomarker.subtitle,
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(color = biomarker.badgeBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = biomarker.badgeText,
                            color = biomarker.badgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Details",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = biomarker.description,
                fontSize = 12.sp,
                color = Color(0xFF4B5563),
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { biomarker.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(biomarker.progressColorHex),
                trackColor = BackgroundGray,
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(biomarker.leftLabel, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                if (biomarker.centerLabel.isNotEmpty()) {
                    Text(biomarker.centerLabel, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                }
                Text(
                    text = biomarker.rightLabel,
                    fontSize = 11.sp,
                    fontWeight = if (biomarker.rightLabelIsRed) FontWeight.Bold else FontWeight.Normal,
                    color = if (biomarker.rightLabelIsRed) NCStateRed else Color(0xFF9CA3AF)
                )
            }
        }
    }
}
