package com.example.wolfpackvitals.data.network

import com.example.wolfpackvitals.data.replay.ReplaySnapshot
import org.json.JSONArray
import org.json.JSONObject

val DASHBOARD_METRICS = listOf("hr_eda", "glucose_variability", "hrv", "prediabetes_risk")

data class DashboardPayload(val patientId: String, val sessionId: String, val interval: Int, val snapshot: ReplaySnapshot) {
    val windowId: String get() = requireNotNull(snapshot.sourceWindow).id
    fun json(): String {
        val window = requireNotNull(snapshot.sourceWindow) { "Timestamped source window is missing" }
        val sensors = JSONObject()
        window.sensors.forEach { (name, series) ->
            sensors.put(name, JSONObject()
                .put("unit", series.unit)
                .put("coverage", JSONObject().put("sample_count", series.readings.size)
                    .put("first_timestamp", series.readings.firstOrNull()?.timestamp ?: JSONObject.NULL)
                    .put("last_timestamp", series.readings.lastOrNull()?.timestamp ?: JSONObject.NULL))
                .put("readings", JSONArray().apply { series.readings.forEach { reading ->
                    put(JSONObject().put("timestamp", reading.timestamp).put("values", JSONArray(reading.values)))
                } }))
        }
        val averages = snapshot.averages
        return JSONObject().put("contract_version", "1")
            .put("patient_id", patientId).put("session_id", sessionId).put("interval", interval)
            .put("active_elapsed_ms", snapshot.activeElapsedMs)
            .put("source_window", JSONObject().put("id", window.id).put("start", window.start)
                .put("end", window.end).put("time_basis", window.timeBasis))
            .put("sensors", sensors)
            .put("meals", JSONArray().apply { window.meals.forEach { meal ->
                put(JSONObject().put("timestamp", meal.timestamp).put("carbohydrates_g", meal.carbohydratesG ?: JSONObject.NULL))
            } })
            .put("features", JSONObject())
            .put("cumulative_replay_averages", JSONObject().put("heartbeat", averages.heartbeat)
                .put("glucose", averages.glucose).put("interbeat_interval", averages.interbeatInterval)
                .put("acc_x", averages.acceleration.x).put("acc_y", averages.acceleration.y).put("acc_z", averages.acceleration.z))
            .toString()
    }
}

enum class RiskCategory(val label: String, val badge: String, val colorHex: Long, val backgroundHex: Long) {
    TYPICAL("Typical", "Stable", 0xFF16A34A, 0xFFDCFCE7),
    LOW("Low", "Stable", 0xFF16A34A, 0xFFDCFCE7),
    MODERATE("Moderate", "Monitoring", 0xFFCA8A04, 0xFFFEF9C3),
    ELEVATED("Elevated", "Monitoring", 0xFFCA8A04, 0xFFFEF9C3),
    HIGH("High", "Monitoring", 0xFFCC0000, 0xFFFEE2E2)
}

data class MetricResult(
    val available: Boolean,
    val value: Double? = null,
    val unit: String? = null,
    val category: RiskCategory? = null,
    val barPosition: Float? = null,
    val referenceRange: String? = null,
    val thresholdVersion: String? = null,
    val confidence: Double? = null,
    val riskProbability: Double? = null,
    val modelVersion: String? = null,
    val windowId: String? = null,
    val explanation: String? = null,
    val reason: String? = null,
    val restorativeStart: Float? = null,
    val restorativeEnd: Float? = null,
    val supportsPostmealSpikes: Boolean = false
) {
    companion object {
        fun unavailable(reason: String) = MetricResult(false, reason = reason)
        private fun JSONObject.nullableString(name: String) = if (isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
        private fun JSONObject.nullableNumber(name: String): Double? {
            if (isNull(name)) return null
            val value = get(name)
            require(value is Number) { "Expected a numeric model output" }
            return value.toDouble().also { require(it.isFinite()) }
        }
        private fun JSONObject.probability(name: String) = nullableNumber(name)?.also { require(it in 0.0..1.0) }
        fun parse(json: JSONObject, windowId: String): MetricResult {
            require(json.nullableString("window_id") == windowId)
            val availability = json.getString("availability")
            require(availability in listOf("available", "unavailable"))
            if (availability == "unavailable") {
                listOf("value", "risk_category", "bar_position", "confidence", "risk_probability", "explanation").forEach { require(json.isNull(it)) }
                require(!json.optBoolean("supports_postmeal_spikes", false))
                return unavailable(json.nullableString("reason") ?: "Analysis is unavailable.")
            }
            val reference = json.getJSONObject("reference_range")
            val unit = requireNotNull(json.nullableString("unit"))
            require(reference.getString("unit") == unit)
            val lower = reference.nullableNumber("lower")
            val upper = reference.nullableNumber("upper")
            require(lower == null || upper == null || lower <= upper)
            val start = reference.probability("restorative_bar_start")?.toFloat()
            val end = reference.probability("restorative_bar_end")?.toFloat()
            require((start == null) == (end == null) && (start == null || start <= requireNotNull(end)))
            require(json.isNull("reason"))
            return MetricResult(true,
                value = requireNotNull(json.nullableNumber("value")), unit = unit,
                category = RiskCategory.valueOf(json.getString("risk_category").uppercase(java.util.Locale.US)),
                barPosition = requireNotNull(json.probability("bar_position")).toFloat(),
                referenceRange = requireNotNull(reference.nullableString("label")),
                thresholdVersion = requireNotNull(json.nullableString("threshold_version")),
                confidence = json.probability("confidence"), riskProbability = json.probability("risk_probability"),
                modelVersion = requireNotNull(json.nullableString("model_version")), windowId = windowId,
                explanation = json.nullableString("explanation"), restorativeStart = start, restorativeEnd = end,
                supportsPostmealSpikes = json.optBoolean("supports_postmeal_spikes", false))
        }
    }
}

data class DashboardResponse(val patientId: String, val sessionId: String, val interval: Int, val windowId: String, val results: Map<String, MetricResult>) {
    companion object {
        fun parse(body: String): DashboardResponse {
            val json = JSONObject(body)
            require(json.getString("contract_version") == "1")
            val window = json.getString("window_id")
            val results = json.getJSONObject("results")
            return DashboardResponse(json.getString("patient_id"), json.getString("session_id"), json.getInt("interval"), window,
                DASHBOARD_METRICS.associateWith { key -> runCatching { MetricResult.parse(results.getJSONObject(key), window) }
                    .getOrElse { MetricResult.unavailable("The analysis returned an invalid result.") } })
        }
    }
}
