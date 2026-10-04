package com.example.wolfpackvitals.data.replay

import java.time.LocalDateTime

/** Original source timestamps and raw device units; no timezone or resampling is invented. */
data class SourceReading(val timestamp: String, val values: List<Double>)
data class SourceSeries(val unit: String, val readings: List<SourceReading>)
data class SourceMeal(val timestamp: String, val carbohydratesG: Double?)
data class AnalysisWindow(
    val id: String,
    val start: String,
    val end: String,
    val sensors: Map<String, SourceSeries>,
    val meals: List<SourceMeal> = emptyList(),
    val timeBasis: String = "source_local_unspecified"
)

internal fun sourceTime(timestamp: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(timestamp.replace(' ', 'T'))
}.getOrNull()

/** A cumulative window on ONE source clock. Exhausted recordings remain exhausted. */
fun Patient16Data.analysisWindow(elapsedMs: Long): AnalysisWindow {
    val start = sourceSeries["hr"]?.readings?.firstOrNull()?.timestamp?.let(::sourceTime)
        ?: error("Heart-rate source timestamps are unavailable")
    val end = start.plusNanos(elapsedMs * 1_000_000)
    fun inside(timestamp: String) = sourceTime(timestamp)?.let { it >= start && it < end } == true
    return AnalysisWindow(
        id = "patient-16:$start:$elapsedMs",
        start = start.toString(), end = end.toString(),
        sensors = sourceSeries.mapValues { (_, series) -> series.copy(readings = series.readings.filter { inside(it.timestamp) }) },
        meals = meals.filter { inside(it.timestamp) }
    )
}
