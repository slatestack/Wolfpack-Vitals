package com.example.wolfpackvitals.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.ui.theme.CardBackground
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun CurrentHeartRateCard(
    avgBpm: Int,
    restingBpm: Int,
    isStreaming: Boolean = true,
    onLogClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartBeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLogClick() }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = NCStateRed,
                        modifier = Modifier
                            .size(16.dp)
                            .scale(if (isStreaming) scale else 1.0f)
                    )
                    Text(
                        text = "Average HR (over an hour)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280)
                    )
                }

                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isStreaming) NCStateRed else Color.Gray, CircleShape)
                        )
                        Text(
                            text = if (isStreaming) "Live Stream" else "Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStreaming) NCStateRed else Color.Gray
                        )
                    }
                }
            }

            // Stats and Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$avgBpm",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = NCStateRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "bpm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Resting",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "$restingBpm bpm",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NCStateDarkGray
                        )
                    }

                    FilledTonalButton(
                        onClick = onLogClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFF3F4F6),
                            contentColor = NCStateDarkGray
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Log Vital", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Vital", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
