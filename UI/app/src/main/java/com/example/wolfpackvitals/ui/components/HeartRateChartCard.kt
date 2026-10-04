package com.example.wolfpackvitals.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.ui.theme.CardBackground
import com.example.wolfpackvitals.ui.theme.NCStateDarkGray
import com.example.wolfpackvitals.ui.theme.NCStateRed
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun HeartRateChartCard(
    history: List<Pair<String, Int>>,
    selectedRange: String = "24H",
    onRangeSelected: (String) -> Unit = {},
    onExpandClick: () -> Unit = {}
) {
    val chartModel = entryModelOf(*history.map { it.second }.toTypedArray())
    val valueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val raw = history.getOrNull(value.toInt())?.first ?: ""
        if (raw.contains(":")) raw.substringBefore(":") + "h" else raw
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
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
                        text = "Real-time wearable sensor stream • $selectedRange view",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expand Chart",
                        tint = NCStateDarkGray,
                        modifier = Modifier.size(18.dp)
                    )
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
                        onClick = { onRangeSelected(range) },
                        label = { Text(range, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NCStateRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontally Scrollable / Slidable Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Chart(
                    chart = columnChart(),
                    model = chartModel,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(valueFormatter = valueFormatter),
                    modifier = Modifier
                        .width((history.size * 54).dp.coerceAtLeast(340.dp))
                        .height(200.dp)
                )
            }
        }
    }
}
