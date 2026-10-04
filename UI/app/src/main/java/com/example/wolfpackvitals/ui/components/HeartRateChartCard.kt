package com.example.wolfpackvitals.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.ui.theme.CardBackground
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateDarkRed
import com.example.wolfpackvitals.ui.theme.NCStateRed

@Composable
fun HeartRateChartCard(
    history: List<Pair<String, Int>>,
    restingBpm: Int = 61,
    selectedRange: String = "24H",
    lastUpdatedHour: String? = null,
    onRangeSelected: (String) -> Unit = {},
    onLogHourClick: (String) -> Unit = {},
    onExpandClick: () -> Unit = {}
) {
    var isLineMode by remember { mutableStateOf(true) }
    var selectedIndex by remember(history, selectedRange) {
        // If an hour was recently updated, highlight that point by default
        val matchedIdx = if (lastUpdatedHour != null) {
            history.indexOfFirst {
                it.first.equals(lastUpdatedHour, ignoreCase = true) ||
                it.first.startsWith(lastUpdatedHour.substringBefore(":"))
            }
        } else -1
        mutableStateOf(if (matchedIdx != -1) matchedIdx else null)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title, Mode Toggle, and Full-Screen Expand
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Heart Rate Telemetry",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NCStateDarkGray
                    )
                    Text(
                        text = "Hourly stream • Tap any hour on graph to inspect / log",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Toggle Line vs Bar View
                    IconButton(
                        onClick = { isLineMode = !isLineMode },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isLineMode) Icons.Default.BarChart else Icons.Default.ShowChart,
                            contentDescription = "Toggle Chart Style",
                            tint = NCStateDarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Full Screen Expand
                    IconButton(
                        onClick = onExpandClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand Chart",
                            tint = NCStateDarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Range Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("1H", "6H", "24H", "7D").forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = {
                            selectedIndex = null
                            onRangeSelected(range)
                        },
                        label = { Text(range, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NCStateRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Functional Custom Telemetry Canvas Graph
            HeartRateTelemetryGraph(
                history = history,
                restingBpm = restingBpm,
                selectedRange = selectedRange,
                lastUpdatedHour = lastUpdatedHour,
                isLineMode = isLineMode,
                selectedIndex = selectedIndex,
                onSelectIndex = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Active Inspection & Quick Edit Banner for Selected Hour
            AnimatedVisibility(
                visible = selectedIndex != null && selectedIndex!! in history.indices,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedIndex?.let { idx ->
                    if (idx in history.indices) {
                        val point = history[idx]
                        val bpm = point.second
                        val hour = point.first
                        val zoneInfo = getHeartRateZone(bpm)

                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(zoneInfo.second, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = "$hour  •  $bpm BPM",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NCStateDarkGray
                                        )
                                        Text(
                                            text = zoneInfo.first,
                                            fontSize = 11.sp,
                                            color = zoneInfo.second,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onLogHourClick(hour) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NCStateRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Hour", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-performance, precision Jetpack Compose Canvas Telemetry Graph
 * Features:
 * - Precise cubic spline curve with gradient fill
 * - Dashed horizontal reference grid lines for clinical thresholds (140, 110, 80, 60 bpm)
 * - Green dashed resting baseline guideline (61 bpm)
 * - Clear formatted bottom milestone time labels (e.g. 12 AM, 4 AM, 8 AM...)
 * - Touch scrubber / tap detection to inspect and highlight any hour point
 */
@Composable
fun HeartRateTelemetryGraph(
    history: List<Pair<String, Int>>,
    restingBpm: Int = 61,
    selectedRange: String = "24H",
    lastUpdatedHour: String? = null,
    isLineMode: Boolean = true,
    selectedIndex: Int? = null,
    onSelectIndex: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val labelPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#9CA3AF")
            textSize = density.run { 10.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
    }

    val restingPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#16A34A")
            textSize = density.run { 9.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
    }

    val bottomTextPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#4B5563")
            textSize = density.run { 10.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val highlightLabelPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#CC0000")
            textSize = density.run { 10.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(history) {
                detectTapGestures { offset ->
                    if (history.isNotEmpty()) {
                        val leftMargin = 12.dp.toPx()
                        val rightMargin = 48.dp.toPx()
                        val plotWidth = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                        val step = plotWidth / (history.size - 1).coerceAtLeast(1)

                        val rawIdx = ((offset.x - leftMargin) / step).toInt()
                        val clamped = rawIdx.coerceIn(0, history.size - 1)
                        onSelectIndex(clamped)
                    }
                }
            }
            .pointerInput(history) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (history.isNotEmpty()) {
                        val leftMargin = 12.dp.toPx()
                        val rightMargin = 48.dp.toPx()
                        val plotWidth = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                        val step = plotWidth / (history.size - 1).coerceAtLeast(1)

                        val rawIdx = ((change.position.x - leftMargin) / step).toInt()
                        val clamped = rawIdx.coerceIn(0, history.size - 1)
                        onSelectIndex(clamped)
                    }
                }
            }
    ) {
        if (history.isEmpty()) return@Canvas

        val leftMargin = 12.dp.toPx()
        val rightMargin = 48.dp.toPx()
        val topMargin = 22.dp.toPx()
        val bottomMargin = 28.dp.toPx()

        val plotWidth = size.width - leftMargin - rightMargin
        val plotHeight = size.height - topMargin - bottomMargin

        val minBpm = 40f
        val maxBpm = 150f

        fun getY(bpm: Float): Float {
            val fraction = (bpm - minBpm) / (maxBpm - minBpm)
            return (topMargin + plotHeight * (1f - fraction)).coerceIn(topMargin, topMargin + plotHeight)
        }

        // 1. Horizontal Reference Grid Lines & Labels
        val guideBpmLevels = listOf(140, 110, 80, 60)
        guideBpmLevels.forEach { level ->
            val gy = getY(level.toFloat())

            // Dashed horizontal line
            drawLine(
                color = Color(0xFFE5E7EB),
                start = Offset(leftMargin, gy),
                end = Offset(size.width - rightMargin, gy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            // Right Y-Axis label
            drawContext.canvas.nativeCanvas.drawText(
                "$level",
                size.width - 12.dp.toPx(),
                gy + 4.dp.toPx(),
                labelPaint
            )
        }

        // 2. Resting Baseline Guideline
        if (restingBpm in 40..150) {
            val ry = getY(restingBpm.toFloat())
            drawLine(
                color = Color(0xFF10B981).copy(alpha = 0.5f),
                start = Offset(leftMargin, ry),
                end = Offset(size.width - rightMargin, ry),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Rest $restingBpm",
                size.width - 4.dp.toPx(),
                ry + 3.dp.toPx(),
                restingPaint
            )
        }

        // 3. Compute (x, y) Coordinates for each data point
        val count = history.size
        val stepX = plotWidth / (count - 1).coerceAtLeast(1)
        val points = history.mapIndexed { idx, pair ->
            Offset(
                x = leftMargin + idx * stepX,
                y = getY(pair.second.toFloat())
            )
        }

        // 4. Draw Line Curve OR Bar Chart
        if (isLineMode) {
            // Smooth Cubic Bezier Curve
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val midX = (prev.x + cur.x) / 2f
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }

            // Area Gradient Fill
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, topMargin + plotHeight)
                lineTo(points.first().x, topMargin + plotHeight)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        NCStateRed.copy(alpha = 0.28f),
                        NCStateRed.copy(alpha = 0.02f)
                    ),
                    startY = topMargin,
                    endY = topMargin + plotHeight
                )
            )

            // Outer Line Stroke
            drawPath(
                path = linePath,
                color = NCStateRed,
                style = Stroke(
                    width = 2.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw Data Points
            points.forEachIndexed { i, pt ->
                val isSelected = (i == selectedIndex)
                val isUpdated = (history[i].first == lastUpdatedHour)

                if (isSelected || isUpdated) {
                    // Glowing outer ring for active selection / recently logged vital
                    drawCircle(
                        color = NCStateRed.copy(alpha = 0.22f),
                        radius = 9.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = NCStateRed,
                        radius = 5.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = pt
                    )
                } else {
                    // Regular subtle point
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = NCStateRed,
                        radius = 1.8.dp.toPx(),
                        center = pt
                    )
                }
            }
        } else {
            // Bar Chart Mode
            val barWidth = (plotWidth / count * 0.65f).coerceIn(4f, 22f)
            points.forEachIndexed { i, pt ->
                val isSelected = (i == selectedIndex)
                val isUpdated = (history[i].first == lastUpdatedHour)

                val barColor = when {
                    isSelected || isUpdated -> NCStateRed
                    else -> NCStateRed.copy(alpha = 0.75f)
                }

                val barTop = pt.y
                val barBottom = topMargin + plotHeight
                val barLeft = pt.x - barWidth / 2f

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, (barBottom - barTop).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        // 5. Vertical Selection Cursor when scrubber/tap active
        selectedIndex?.let { selIdx ->
            if (selIdx in points.indices) {
                val selPt = points[selIdx]
                drawLine(
                    color = NCStateRed.copy(alpha = 0.8f),
                    start = Offset(selPt.x, topMargin),
                    end = Offset(selPt.x, topMargin + plotHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                // Floating BPM value pill directly above point
                val tagText = "${history[selIdx].second}"
                val tagY = (selPt.y - 12.dp.toPx()).coerceAtLeast(topMargin - 4.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    tagText,
                    selPt.x,
                    tagY,
                    highlightLabelPaint
                )
            }
        }

        // 6. X-Axis Bottom Baseline & Milestone Time Labels
        drawLine(
            color = Color(0xFFD1D5DB),
            start = Offset(leftMargin, topMargin + plotHeight),
            end = Offset(size.width - rightMargin, topMargin + plotHeight),
            strokeWidth = 1.dp.toPx()
        )

        // Determine which milestone time labels to display for legibility
        val labelsToShow: List<Pair<Int, String>> = when (selectedRange) {
            "24H" -> {
                // Every 4 hours: 12 AM, 4 AM, 8 AM, 12 PM, 4 PM, 8 PM, 11 PM
                listOf(
                    0 to "12 AM",
                    4 to "4 AM",
                    8 to "8 AM",
                    12 to "12 PM",
                    16 to "4 PM",
                    20 to "8 PM",
                    (count - 1) to "11 PM"
                ).filter { it.first in points.indices }
            }
            "6H" -> {
                history.indices.map { it to (history[it].first.take(5)) }
            }
            "1H" -> {
                // Every other minute point
                history.indices.filter { it % 2 == 0 || it == count - 1 }.map { it to history[it].first }
            }
            "7D" -> {
                history.indices.map { it to history[it].first }
            }
            else -> {
                listOf(0 to (history.firstOrNull()?.first ?: ""), (count - 1) to (history.lastOrNull()?.first ?: ""))
            }
        }

        labelsToShow.forEach { (idx, labelText) ->
            if (idx in points.indices) {
                val tickX = points[idx].x

                // Small tick mark
                drawLine(
                    color = Color(0xFF9CA3AF),
                    start = Offset(tickX, topMargin + plotHeight),
                    end = Offset(tickX, topMargin + plotHeight + 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )

                // Centered text label
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    tickX,
                    size.height - 6.dp.toPx(),
                    if (idx == selectedIndex) highlightLabelPaint else bottomTextPaint
                )
            }
        }
    }
}

private fun getHeartRateZone(bpm: Int): Pair<String, Color> {
    return when {
        bpm < 60 -> "Resting Baseline" to Color(0xFF3B82F6)
        bpm in 60..99 -> "Normal / Fat Burn Zone" to Color(0xFF10B981)
        bpm in 100..139 -> "Cardio Aerobic Zone" to Color(0xFFF59E0B)
        else -> "Peak Sympathetic Zone" to NCStateRed
    }
}
