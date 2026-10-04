package com.example.wolfpackvitals.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.wolfpackvitals.data.BiomarkerAnalysis
import com.example.wolfpackvitals.ui.theme.BackgroundGray

/** Shared by cards and details. Zero progress draws only the unfilled track. */
@Composable
fun AnalysisBar(analysis: BiomarkerAnalysis, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(BackgroundGray, cornerRadius = radius)
        val start = analysis.restorativeStart
        val end = analysis.restorativeEnd
        if (start != null && end != null) {
            drawRect(Color(0xFF10B981).copy(alpha = 0.25f),
                topLeft = Offset(size.width * start, 0f), size = Size(size.width * (end - start), size.height))
        }
        if (analysis.progress > 0f) drawRoundRect(Color(analysis.progressColorHex),
            size = Size(size.width * analysis.progress.coerceIn(0f, 1f), size.height), cornerRadius = radius)
        if (start != null && end != null) {
            listOf(start, end).forEach { position ->
                drawLine(Color(0xFF10B981), Offset(size.width * position, 0f), Offset(size.width * position, size.height), 2.dp.toPx())
            }
        }
    }
}
