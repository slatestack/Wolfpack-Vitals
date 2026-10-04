package com.example.wolfpackvitals.data.network

import com.example.wolfpackvitals.data.initialAnalysis
import com.example.wolfpackvitals.data.pendingAnalysis
import com.example.wolfpackvitals.data.withResult
import com.example.wolfpackvitals.data.replay.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DashboardContractTest {
    private fun payload(): DashboardPayload {
        val snapshot = Patient16ReplaySession(repositoryPatient16Data()).advanceBy(REPLAY_SEND_INTERVAL_MS).single()
        return DashboardPayload("16", "test-session", 1, snapshot)
    }
    private fun response(payload: DashboardPayload, results: JSONObject) = JSONObject()
        .put("contract_version", "1").put("patient_id", payload.patientId).put("session_id", payload.sessionId)
        .put("interval", payload.interval).put("window_id", payload.windowId).put("results", results)
    private fun available(payload: DashboardPayload, category: String = "moderate") = JSONObject()
        .put("availability", "available").put("value", 17.0).put("unit", "fixture-unit")
        .put("risk_category", category).put("bar_position", 0.65)
        .put("reference_range", JSONObject().put("label", "Synthetic range").put("unit", "fixture-unit"))
        .put("threshold_version", "fixture-v1").put("model_version", "test-model")
        .put("window_id", payload.windowId).put("confidence", JSONObject.NULL).put("risk_probability", .3)

    @Test fun postPreservesSourceReadingsZeroEdaAndLegacyCumulativeAverages() {
        val payload = payload()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(payload, JSONObject()).toString()))
            val finished = CountDownLatch(1)
            var result: Result<DashboardResponse>? = null
            OkHttpPredictionClient(server.url("/").toString()).analyze(payload) { result = it; finished.countDown() }
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/make_prediction", request.requestUrl!!.encodedPath)
            assertTrue(request.requestUrl!!.queryParameterNames.isEmpty())
            val body = JSONObject(request.body.readUtf8())
            assertEquals("16", body.getString("patient_id"))
            assertEquals("test-session", body.getString("session_id"))
            assertEquals(300, body.getJSONObject("sensors").getJSONObject("hr").getJSONArray("readings").length())
            val eda = body.getJSONObject("sensors").getJSONObject("eda")
            assertEquals("uS", eda.getString("unit"))
            assertEquals(payload.snapshot.sourceWindow!!.sensors.getValue("eda").readings.first().timestamp,
                eda.getJSONArray("readings").getJSONObject(0).getString("timestamp"))
            assertEquals(0, body.getJSONObject("sensors").getJSONObject("temp").getJSONArray("readings").length())
            assertEquals(payload.snapshot.averages.heartbeat, body.getJSONObject("cumulative_replay_averages").getDouble("heartbeat"), 0.0)
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue(result!!.isSuccess)
            assertTrue(result!!.getOrThrow().results.values.none { it.available })
        }
    }

    @Test fun malformedSiblingDoesNotEraseSupportedMetricAndNullConfidenceStaysNull() {
        val payload = payload()
        val results = JSONObject().put("glucose_variability", available(payload)).put("hrv", "LLM text")
        val parsed = DashboardResponse.parse(response(payload, results).toString())
        val metric = parsed.results.getValue("glucose_variability")
        assertTrue(metric.available)
        assertNull(metric.confidence)
        assertEquals(.3, metric.riskProbability!!, 0.0)
        assertFalse(parsed.results.getValue("hrv").available)
        assertFalse(parsed.results.getValue("hr_eda").available)
    }

    @Test fun numericStringsAndOutOfRangeConfidenceAreInvalid() {
        val payload = payload()
        for (invalid in listOf(available(payload).put("value", "17"), available(payload).put("confidence", 1.1),
            available(payload).put("bar_position", -0.1), available(payload).put("window_id", "old"))) {
            val parsed = DashboardResponse.parse(response(payload, JSONObject().put("hrv", invalid)).toString())
            assertFalse(parsed.results.getValue("hrv").available)
        }
    }

    @Test fun envelopeMismatchFailsBeforeResultsReachTheViewModel() {
        val payload = payload()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(response(payload, JSONObject()).put("session_id", "old").toString()))
            val finished = CountDownLatch(1)
            var result: Result<DashboardResponse>? = null
            OkHttpPredictionClient(server.url("/").toString()).analyze(payload) { result = it; finished.countDown() }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue(result!!.isFailure)
        }
    }

    @Test fun oneCategoryMappingDrivesBadgesColorsSeverityAndFilters() {
        val payload = payload()
        RiskCategory.entries.forEach { category ->
            val metric = MetricResult.parse(available(payload, category.name.lowercase()), payload.windowId)
            val card = initialAnalysis("glucose_variability").withResult(metric)
            assertEquals(category.badge, card.badgeText)
            assertEquals(category.label, card.severity)
            assertEquals(category.colorHex, card.progressColorHex)
            assertEquals(0.65f, card.progress)
            assertEquals("—", card.confidenceScore)
            assertEquals("30.0%", card.riskProbabilityText)
            assertFalse(card.description.contains("spikes after meals"))
        }
    }

    @Test fun missingAndOutdatedResultsDoNotCreateModelNumbers() {
        val empty = initialAnalysis("hrv")
        assertEquals("Collecting data", empty.badgeText)
        assertEquals(0f, empty.progress)
        assertEquals("—", empty.confidenceScore)
        val unavailable = empty.withResult(MetricResult.unavailable("Missing temperature"))
        assertEquals("Unavailable", unavailable.badgeText)
        assertEquals(0f, unavailable.progress)
        val payload = payload()
        val available = empty.withResult(MetricResult.parse(available(payload), payload.windowId))
        val outdated = available.pendingAnalysis("Current request failed", unavailable = true)
        assertEquals("Outdated", outdated.badgeText)
        assertEquals(available.result, outdated.result)
        assertEquals(available.progress, outdated.progress)
    }
}
