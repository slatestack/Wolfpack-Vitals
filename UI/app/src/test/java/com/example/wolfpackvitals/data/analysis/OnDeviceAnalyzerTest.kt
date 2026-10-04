package com.example.wolfpackvitals.data.analysis

import com.example.wolfpackvitals.data.HealthProfile
import com.example.wolfpackvitals.data.network.DASHBOARD_METRICS
import com.example.wolfpackvitals.data.replay.Patient16ReplaySession
import com.example.wolfpackvitals.data.replay.REPLAY_HOUR_MS
import com.example.wolfpackvitals.data.replay.REPLAY_SEND_INTERVAL_MS
import com.example.wolfpackvitals.data.replay.repositoryPatient16Data
import org.junit.Assert.*
import org.junit.Test

class OnDeviceAnalyzerTest {
    private val data = repositoryPatient16Data()
    private val analyzer = OnDeviceAnalyzer()

    @Test fun everyMetricIsAvailableFromTheFirstFiveMinuteWindowThroughTheHour() {
        val snapshots = Patient16ReplaySession(data).advanceBy(REPLAY_HOUR_MS)
        assertEquals(12, snapshots.size)
        snapshots.forEach { snapshot ->
            val results = analyzer.analyze(data, snapshot, HealthProfile())
            DASHBOARD_METRICS.forEach { metric ->
                val result = results.getValue(metric)
                assertTrue("$metric at ${snapshot.activeElapsedMs}: ${result.reason}", result.available)
                assertTrue(result.value!!.isFinite())
                assertTrue(result.barPosition!! in 0f..1f)
                assertEquals(ON_DEVICE_MODEL_VERSION, result.modelVersion)
            }
        }
    }

    @Test fun riskIndexRisesWithPrediabeticLabValues() {
        val snapshot = Patient16ReplaySession(data).advanceBy(REPLAY_SEND_INTERVAL_MS).single()
        val typical = analyzer.analyze(data, snapshot, HealthProfile()).getValue("prediabetes_risk")
        val prediabetic = analyzer.analyze(data, snapshot,
            HealthProfile(fastingGlucoseMgDl = 118, hba1cPercent = 6.2f, bmi = 31f)).getValue("prediabetes_risk")
        assertTrue(prediabetic.value!! > typical.value!!)
    }
}
