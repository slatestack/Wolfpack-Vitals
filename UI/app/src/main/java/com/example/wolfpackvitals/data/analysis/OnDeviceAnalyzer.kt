package com.example.wolfpackvitals.data.analysis

import com.example.wolfpackvitals.data.HealthProfile
import com.example.wolfpackvitals.data.network.MetricResult
import com.example.wolfpackvitals.data.network.RiskCategory
import com.example.wolfpackvitals.data.replay.Patient16Data
import com.example.wolfpackvitals.data.replay.REPLAY_SAMPLE_INTERVAL_MS
import com.example.wolfpackvitals.data.replay.ReplaySnapshot
import com.example.wolfpackvitals.data.replay.SourceReading
import com.example.wolfpackvitals.data.replay.sourceTime
import kotlin.math.sqrt

const val ON_DEVICE_MODEL_VERSION = "On-device estimate"
private const val ON_DEVICE_THRESHOLD_VERSION = "wolfpack-on-device-1"

/**
 * Transparent, deterministic estimates computed from the replayed Patient 16 recordings.
 * Used whenever the analysis API cannot return a metric. These are screening heuristics, not diagnoses.
 */
class OnDeviceAnalyzer {

    fun analyze(data: Patient16Data, snapshot: ReplaySnapshot, profile: HealthProfile): Map<String, MetricResult> {
        val windowId = snapshot.sourceWindow?.id ?: "on-device:${snapshot.activeElapsedMs}"
        val samples = (snapshot.activeElapsedMs / REPLAY_SAMPLE_INTERVAL_MS).toInt()

        val ibiSeconds = snapshot.sourceWindow?.sensors?.get("ibi")?.readings?.map { it.values.single() }
            ?: data.interbeatInterval.take(samples)
        // One CGM row per replayed second, matching the replay's cumulative averages.
        val glucose = data.glucose.take(samples.coerceAtMost(data.glucose.size))
        val hrReadings = snapshot.sourceWindow?.sensors?.get("hr")?.readings.orEmpty()
        val edaReadings = snapshot.sourceWindow?.sensors?.get("eda")?.readings.orEmpty()

        val rmssd = rmssdMs(ibiSeconds)
        val glucoseStats = glucoseStats(glucose)
        val coupling = hrEdaCorrelation(hrReadings, edaReadings)
        val meanHr = data.heartbeat.take(samples.coerceAtMost(data.heartbeat.size)).takeIf { it.isNotEmpty() }?.average()

        return mapOf(
            "hrv" to hrvResult(rmssd, windowId),
            "glucose_variability" to glucoseResult(glucoseStats, windowId),
            "hr_eda" to hrEdaResult(coupling, windowId),
            "prediabetes_risk" to riskResult(profile, glucoseStats, rmssd, meanHr, windowId)
        )
    }

    private data class GlucoseStats(val mean: Double, val cvPercent: Double, val count: Int)

    private fun rmssdMs(ibiSeconds: List<Double>): Double? {
        val beats = ibiSeconds.filter { it in 0.3..2.0 }.map { it * 1000 }
        val diffs = beats.zipWithNext { a, b -> b - a }.filter { kotlin.math.abs(it) <= 200 }
        if (diffs.size < 10) return null
        return sqrt(diffs.sumOf { it * it } / diffs.size)
    }

    private fun glucoseStats(values: List<Double>): GlucoseStats? {
        val valid = values.filter { it in 40.0..400.0 }
        if (valid.size < 3) return null
        val mean = valid.average()
        val sd = sqrt(valid.sumOf { (it - mean) * (it - mean) } / (valid.size - 1))
        return GlucoseStats(mean, sd / mean * 100, valid.size)
    }

    /** Pearson correlation of HR and EDA paired on the same source second. */
    private fun hrEdaCorrelation(hr: List<SourceReading>, eda: List<SourceReading>): Pair<Double, Int>? {
        fun second(reading: SourceReading) = sourceTime(reading.timestamp)?.withNano(0)
        val edaBySecond = eda.filter { it.values.single() > 0.01 }
            .groupBy(::second).filterKeys { it != null }
            .mapValues { (_, readings) -> readings.map { it.values.single() }.average() }
        val pairs = hr.mapNotNull { reading -> edaBySecond[second(reading)]?.let { reading.values.single() to it } }
        if (pairs.size < 20) return null
        val meanX = pairs.map { it.first }.average()
        val meanY = pairs.map { it.second }.average()
        val cov = pairs.sumOf { (it.first - meanX) * (it.second - meanY) }
        val varX = pairs.sumOf { (it.first - meanX) * (it.first - meanX) }
        val varY = pairs.sumOf { (it.second - meanY) * (it.second - meanY) }
        if (varX == 0.0 || varY == 0.0) return null
        return cov / sqrt(varX * varY) to pairs.size
    }

    private fun hrvResult(rmssd: Double?, windowId: String): MetricResult {
        rmssd ?: return MetricResult.unavailable("Not enough clean interbeat intervals yet for an on-device HRV estimate.")
        val category = when {
            rmssd < 15 -> RiskCategory.HIGH
            rmssd < 20 -> RiskCategory.ELEVATED
            rmssd < 30 -> RiskCategory.MODERATE
            else -> RiskCategory.TYPICAL
        }
        return estimate(rmssd, "ms RMSSD", category, (rmssd / 100).toFloat(), "20–100 ms (short-term adult RMSSD)",
            "RMSSD from successive interbeat intervals. Lower values indicate reduced parasympathetic activity.",
            windowId, restorative = 0.2f to 1f)
    }

    private fun glucoseResult(stats: GlucoseStats?, windowId: String): MetricResult {
        stats ?: return MetricResult.unavailable("Not enough glucose readings yet for an on-device variability estimate.")
        val category = when {
            stats.cvPercent < 20 -> RiskCategory.LOW
            stats.cvPercent < 36 -> RiskCategory.MODERATE
            else -> RiskCategory.HIGH
        }
        return estimate(stats.cvPercent, "% CV", category, (stats.cvPercent / 50).toFloat(), "CV ≤ 36% (CGM consensus target)",
            String.format(java.util.Locale.US, "Coefficient of variation across %d CGM readings (mean %.0f mg/dL).",
                stats.count, stats.mean), windowId)
    }

    private fun hrEdaResult(coupling: Pair<Double, Int>?, windowId: String): MetricResult {
        coupling ?: return MetricResult.unavailable("Heart-rate and EDA readings do not overlap enough yet for an on-device estimate.")
        val (r, pairs) = coupling
        val category = when {
            r < 0.3 -> RiskCategory.TYPICAL
            r < 0.5 -> RiskCategory.MODERATE
            r < 0.7 -> RiskCategory.ELEVATED
            else -> RiskCategory.HIGH
        }
        return estimate(r, "r", category, r.toFloat(), "r < 0.3 (weak HR–EDA coupling)",
            "Correlation of heart rate and skin conductance across $pairs overlapping seconds. " +
                "Strong positive coupling suggests simultaneous sympathetic arousal.", windowId)
    }

    private fun riskResult(profile: HealthProfile, glucose: GlucoseStats?, rmssd: Double?, meanHr: Double?,
                           windowId: String): MetricResult {
        fun scale(value: Double, low: Double, high: Double) = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        // Glucose Management Indicator (Bergenstal 2018) maps mean CGM glucose onto the HbA1c scale.
        val gmi = glucose?.let { 3.31 + 0.02392 * it.mean }
        val components = listOfNotNull(
            0.25 to scale(profile.hba1cPercent.toDouble(), 5.0, 6.5),
            0.15 to scale(profile.fastingGlucoseMgDl.toDouble(), 85.0, 126.0),
            0.15 to scale(profile.bmi.toDouble(), 22.0, 30.0),
            gmi?.let { 0.20 to scale(it, 5.0, 6.5) },
            glucose?.let { 0.10 to scale(it.cvPercent, 0.0, 36.0) },
            rmssd?.let { 0.10 to scale(50 - it, 0.0, 35.0) },
            meanHr?.let { 0.05 to scale(it, 60.0, 90.0) }
        )
        val score = components.sumOf { it.first * it.second } / components.sumOf { it.first } * 100
        val category = when {
            score < 25 -> RiskCategory.LOW
            score < 50 -> RiskCategory.MODERATE
            score < 75 -> RiskCategory.ELEVATED
            else -> RiskCategory.HIGH
        }
        val gmiText = gmi?.let { String.format(java.util.Locale.US, " Glucose management indicator %.1f%%.", it) }.orEmpty()
        return estimate(score, "/100", category, (score / 100).toFloat(), "Index < 25 (low)",
            "Weighted index of HbA1c, fasting glucose, BMI, CGM mean and variability, HRV and heart rate.$gmiText " +
                "Not a diagnosis; confirm with an HbA1c or fasting glucose test.", windowId)
    }

    private fun estimate(value: Double, unit: String, category: RiskCategory, bar: Float, range: String,
                         explanation: String, windowId: String, restorative: Pair<Float, Float>? = null) = MetricResult(
        available = true, value = value, unit = unit, category = category,
        barPosition = bar.coerceIn(0f, 1f), referenceRange = range,
        thresholdVersion = ON_DEVICE_THRESHOLD_VERSION, modelVersion = ON_DEVICE_MODEL_VERSION,
        windowId = windowId, explanation = explanation,
        restorativeStart = restorative?.first, restorativeEnd = restorative?.second
    )
}
