package com.example.wolfpackvitals.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.R
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateDarkRed
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun LandingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0F12),
                        Color(0xFF1B0505),
                        Color(0xFF2B0000),
                        NCStateDarkGray
                    )
                )
            )
    ) {
        // Watermark Background Logo with subtle opacity
        Image(
            painter = painterResource(id = R.drawable.ic_wolf_heart_white),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(420.dp)
                .alpha(0.045f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // NC State Institution Badge
                Surface(
                    color = NCStateDarkRed.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(NCStateRed.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(NCStateRed, CircleShape)
                        )
                        Text(
                            text = "THE 4 ACES • NC STATE UNIVERSITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Hero Logo Medallion
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(130.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(NCStateRed, Color.White.copy(alpha = 0.6f), NCStateRed)
                            ),
                            shape = CircleShape
                        ),
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_wolf_heart_white),
                            contentDescription = "Wolfpack Vitals Logo",
                            modifier = Modifier.size(92.dp)
                        )
                    }
                }

                // App Title & Tagline
                Text(
                    text = "Wolfpack Vitals",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Autonomous Physiological Telemetry &\nDatabricks LLM Predictive Health",
                    fontSize = 14.sp,
                    color = Color(0xFFD1D5DB),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Feature Highlights
                FeatureHighlightPill(
                    icon = Icons.Default.Timeline,
                    title = "Cross-Fuzzy Entropy (X-FuzzEn)",
                    description = "Non-invasive glycemic dysregulation risk detection"
                )

                FeatureHighlightPill(
                    icon = Icons.Default.AutoAwesome,
                    title = "Databricks LLM Intelligence",
                    description = "Real-time autonomic tone & metabolic predictions"
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Action Buttons Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Log In Button
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NCStateRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Log In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. Register Button
                OutlinedButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.2f))
                        ),
                        width = 1.5.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Account / Register",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Security & Privacy Note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Encrypted On-Device Telemetry • SHA-256 Auth",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHighlightPill(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = NCStateRed.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
