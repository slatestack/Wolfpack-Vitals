package com.example.wolfpackvitals.data.network

import com.example.wolfpackvitals.data.replay.Patient16ReplaySession
import com.example.wolfpackvitals.data.replay.REPLAY_SEND_INTERVAL_MS
import com.example.wolfpackvitals.data.replay.repositoryPatient16Data
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PredictionClientTest {
    @Test fun readinessChecksTheActualBasePathAndRejectsMalformedResponses() {
        MockWebServer().use { server ->
            val statuses = org.json.JSONObject()
            DASHBOARD_METRICS.forEach { statuses.put(it, org.json.JSONObject().put("configured", false).put("reason", "Install a verified workflow.")) }
            server.enqueue(MockResponse().setBody(org.json.JSONObject().put("contract_version", "1").put("results", statuses).toString()))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(503))
            val client = OkHttpPredictionClient(server.url("/api/").toString())
            repeat(3) { index ->
                val finished = CountDownLatch(1)
                var result: Result<AnalysisReadiness>? = null
                client.readiness { result = it; finished.countDown() }
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertEquals("/api/analysis_readiness", server.takeRequest().path)
                if (index == 0) assertTrue(result!!.getOrThrow().reasons.values.all { it == "Install a verified workflow." })
                else assertTrue(result!!.isFailure)
            }
        }
    }

    @Test fun actualPatientAveragesUseExactEncodedFastApiQueryNamesAndLocaleIndependentStrings() {
        val session = Patient16ReplaySession(repositoryPatient16Data())
        val snapshot = session.advanceBy(REPLAY_SEND_INTERVAL_MS).single()
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val payload = PredictionPayload.from(snapshot.averages)
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("\"prediction\""))
                val finished = CountDownLatch(1)
                var result: Result<String>? = null
                OkHttpPredictionClient(server.url("/").toString()).send(payload) {
                    result = it
                    finished.countDown()
                }
                val request = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("GET", request.method)
                assertEquals("/make_prediction", request.requestUrl!!.encodedPath)
                assertEquals(setOf("heartbeat", "glucose", "Interbeat_interval", "ACC"),
                    request.requestUrl!!.queryParameterNames)
                assertEquals(payload.heartbeat, request.requestUrl!!.queryParameter("heartbeat"))
                assertEquals(payload.glucose, request.requestUrl!!.queryParameter("glucose"))
                assertEquals(payload.interbeatInterval, request.requestUrl!!.queryParameter("Interbeat_interval"))
                assertEquals(payload.acc, request.requestUrl!!.queryParameter("ACC"))
                assertTrue(payload.acc.matches(Regex("x=-?\\d+\\.\\d{2},y=-?\\d+\\.\\d{2},z=-?\\d+\\.\\d{2}")))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertTrue(result!!.isSuccess)
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test fun httpFailuresAreReportedWithoutAutomaticRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val finished = CountDownLatch(1)
            var failure: Throwable? = null
            val session = Patient16ReplaySession(repositoryPatient16Data())
            val payload = PredictionPayload.from(session.advanceBy(REPLAY_SEND_INTERVAL_MS).single().averages)
            OkHttpPredictionClient(server.url("/").toString()).send(payload) {
                failure = it.exceptionOrNull()
                finished.countDown()
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue(failure!!.message!!.contains("503"))
            assertEquals(1, server.requestCount)
        }
    }
}
