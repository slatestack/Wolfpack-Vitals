package com.example.wolfpackvitals.ui

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wolfpackvitals.data.network.*
import com.example.wolfpackvitals.data.replay.*
import com.example.wolfpackvitals.ui.screens.DashboardScreen
import com.example.wolfpackvitals.ui.theme.WolfpackVitalsTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Captures native Compose output with synthetic contract fixtures, never clinical predictions. */
class DashboardVerificationTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private val now = AtomicLong()
    private lateinit var vm: VitalsViewModel
    private val client = FixtureClient()

    @After fun clear() { rule.runOnIdle { store.clear() } }

    private fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.runOnUiThread {
            vm = VitalsViewModel(CsvPatient16DataSource { context.assets.open("patient-16-data/$it").reader() },
                client, monotonicNowMs = { now.get() })
            store.put("verification", vm)
            vm.setApplicationActive(true)
        }
        rule.setContent {
            val state by vm.uiState.collectAsState()
            WolfpackVitalsTheme { DashboardScreen(state, vm) }
        }
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = rule.onRoot()) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "dashboard-verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun chartRedPixels(): Int {
        val bitmap = rule.onNodeWithTag("heart-rate-graph").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { android.graphics.Color.red(it) > 160 && android.graphics.Color.green(it) < 100 && android.graphics.Color.blue(it) < 100 }
    }

    private fun advance(ms: Long) {
        rule.runOnIdle {
            now.addAndGet(ms)
            vm.setApplicationActive(false)
            vm.setApplicationActive(true)
        }
        val completedIntervals = vm.uiState.value.replay.activeElapsedMs / REPLAY_SEND_INTERVAL_MS
        rule.waitUntil(10000) { vm.uiState.value.replay.transmissionsCompleted >= completedIntervals && !vm.uiState.value.pipelineStatus.isSyncing }
        rule.waitForIdle()
    }

    @Test fun chartsStayEmptyThenGrowTwelveIntervalsAndRestartClearsThem() {
        launch()
        assertEquals(0, chartRedPixels())
        capture("dashboard-empty")
        rule.onNodeWithContentDescription("Toggle Chart Style").performClick()
        assertEquals(0, chartRedPixels())
        capture("chart-empty-bars", rule.onNodeWithTag("heart-rate-graph"))
        rule.onNodeWithContentDescription("Toggle Chart Style").performClick()
        rule.onNodeWithText("Start", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { !vm.uiState.value.replay.isLoading }
        advance(REPLAY_SEND_INTERVAL_MS - 1)
        assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        assertEquals(0, chartRedPixels())
        advance(1)
        assertEquals(1, vm.uiState.value.heartRate.hourlyHistory.size)
        assertTrue(chartRedPixels() > 0)
        capture("dashboard-one-point")
        capture("chart-one-point", rule.onNodeWithTag("heart-rate-graph"))
        rule.onNodeWithContentDescription("Toggle Chart Style").performClick()
        assertTrue(chartRedPixels() > 0)
        capture("chart-one-bar", rule.onNodeWithTag("heart-rate-graph"))
        rule.onNodeWithContentDescription("Toggle Chart Style").performClick()
        advance(REPLAY_SEND_INTERVAL_MS)
        val twoPixels = chartRedPixels()
        assertEquals(2, vm.uiState.value.heartRate.hourlyHistory.size)
        assertTrue(twoPixels > 20)
        capture("chart-two-points", rule.onNodeWithTag("heart-rate-graph"))
        rule.onNodeWithText("Pause", useUnmergedTree = true).performClick()
        rule.runOnIdle { now.addAndGet(REPLAY_HOUR_MS) }
        assertEquals(2, vm.uiState.value.heartRate.hourlyHistory.size)
        rule.onNodeWithText("Resume", useUnmergedTree = true).performClick()
        for (i in 3..12) {
            advance(REPLAY_SEND_INTERVAL_MS)
            assertEquals(i, vm.uiState.value.heartRate.hourlyHistory.size)
        }
        capture("chart-twelve-points", rule.onNodeWithTag("heart-rate-graph"))
        listOf("1H", "6H", "24H", "7D").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).performClick()
            assertEquals(12, vm.uiState.value.heartRate.hourlyHistory.size)
        }
        rule.onNodeWithContentDescription("Toggle Chart Style").performClick()
        capture("chart-twelve-bars", rule.onNodeWithTag("heart-rate-graph"))
        rule.onNodeWithContentDescription("Expand Chart").performClick()
        capture("expanded-chart", rule.onNode(isDialog()))
        rule.onNodeWithText("Line chart").assertExists()
        rule.onNodeWithText("Line chart").performClick()
        assertTrue(vm.uiState.value.heartRate.isLineMode)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Start again", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { !vm.uiState.value.replay.isLoading }
        assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        assertEquals(0, chartRedPixels())
    }

    @Test fun cardsAndDialogsFitAndReplayDiagnosticsRemainHidden() {
        launch()
        rule.onNodeWithText("Prediabetes Risk Estimate").performScrollTo()
        capture("dashboard-collecting-cards")
        rule.onNodeWithTag("analysis-card-hr_eda").performScrollTo()
        capture("heart-skin-card", rule.onNodeWithTag("analysis-card-hr_eda"))
        rule.onNodeWithText("Heart & Skin Patterns").performClick()
        rule.onNodeWithText("HR–EDA / Cross-Fuzzy Entropy (X-FuzzEn)").assertExists()
        rule.onNodeWithText("Model Confidence").assertExists()
        rule.onNode(hasText("—") and hasAnyAncestor(isDialog())).assertExists()
        capture("heart-skin-detail", rule.onNode(isDialog()))
        rule.onNodeWithText("Close Details").performClick()
        rule.onNodeWithText("Start", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(10000) { !vm.uiState.value.replay.isLoading }
        advance(REPLAY_SEND_INTERVAL_MS)
        rule.onNodeWithTag("analysis-card-glucose_variability").performScrollTo()
        capture("glucose-card", rule.onNodeWithTag("analysis-card-glucose_variability"))
        capture("dashboard-result-cards")
        rule.onNodeWithText("Glucose Variability").performClick()
        rule.onNode(hasText("Monitoring") and hasAnyAncestor(isDialog())).assertExists()
        rule.onNode(hasText("—") and hasAnyAncestor(isDialog())).assertExists()
        capture("glucose-detail", rule.onNode(isDialog()))
        rule.onNodeWithText("Close Details").performClick()
        rule.onNodeWithTag("analysis-card-hrv").performScrollTo()
        capture("hrv-card", rule.onNodeWithTag("analysis-card-hrv"))
        rule.onNodeWithText("Heart Rate Variability").performClick()
        rule.onNodeWithText("HRV / LF/HF analysis").assertExists()
        capture("hrv-detail", rule.onNode(isDialog()))
        rule.onNodeWithText("Close Details").performClick()
        rule.onNodeWithText("Prediabetes Risk Estimate").performScrollTo()
        capture("risk-unavailable", rule.onNodeWithTag("analysis-card-prediabetes_risk"))
        rule.onNodeWithText("Prediabetes Risk Estimate").performClick()
        rule.onNodeWithText("Risk probability").assertExists()
        capture("risk-detail", rule.onNode(isDialog()))
        rule.onNodeWithText("Close Details").performClick()
        rule.onNodeWithText("Raw API response", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Next send", substring = true).assertDoesNotExist()
        rule.onNodeWithText("IBI:", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Patient 16 hourly", substring = true).assertDoesNotExist()
    }

    @Test fun modelRangesHighSeverityAndConfidenceRemainDistinct() {
        client.supportedRanges = true
        launch()
        rule.onNodeWithText("Start", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { !vm.uiState.value.replay.isLoading }
        advance(REPLAY_SEND_INTERVAL_MS)
        rule.onNodeWithTag("analysis-card-glucose_variability").performScrollTo()
        capture("glucose-high-card", rule.onNodeWithTag("analysis-card-glucose_variability"))
        assertEquals(RiskCategory.HIGH, vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.result!!.category)
        rule.onNodeWithTag("analysis-card-hrv").performScrollTo()
        capture("hrv-restorative-card", rule.onNodeWithTag("analysis-card-hrv"))
        rule.onNodeWithText("Heart Rate Variability").performClick()
        capture("hrv-restorative-detail", rule.onNode(isDialog()))
        rule.onNodeWithText("Close Details").performClick()
        rule.onNodeWithTag("analysis-card-prediabetes_risk").performScrollTo()
        capture("risk-high-card", rule.onNodeWithTag("analysis-card-prediabetes_risk"))
        rule.onNodeWithText("Prediabetes Risk Estimate").performClick()
        rule.onNode(hasText("90.0%") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        rule.onNode(hasText("80.0%") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        capture("risk-high-detail", rule.onNode(isDialog()))
    }

    private class FixtureClient : PredictionClient {
        override fun readiness(onResult: (Result<AnalysisReadiness>) -> Unit): PredictionRequest {
            onResult(Result.success(AnalysisReadiness(DASHBOARD_METRICS.associateWith { null })))
            return PredictionRequest {}
        }
        var supportedRanges = false
        override fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest = error("Legacy GET should not be used")
        override fun analyze(payload: DashboardPayload, onResult: (Result<DashboardResponse>) -> Unit): PredictionRequest {
            val glucose = MetricResult(true, value = 17.0, unit = "fixture units", category = RiskCategory.MODERATE,
                barPosition = .6f, referenceRange = "Synthetic model range", thresholdVersion = "fixture-v1",
                confidence = null, modelVersion = "fixture", windowId = payload.windowId)
            val supported = if (supportedRanges) mapOf(
                "glucose_variability" to glucose.copy(value = 40.0, category = RiskCategory.HIGH, barPosition = .9f),
                "hrv" to MetricResult(true, value = 42.0, unit = "fixture ms", category = RiskCategory.TYPICAL,
                    barPosition = .45f, referenceRange = "Synthetic restorative range", thresholdVersion = "fixture-v1",
                    confidence = null, modelVersion = "fixture", windowId = payload.windowId,
                    restorativeStart = .25f, restorativeEnd = .6f),
                "prediabetes_risk" to MetricResult(true, value = .8, unit = "fixture probability", category = RiskCategory.HIGH,
                    barPosition = .9f, referenceRange = "Synthetic risk range", thresholdVersion = "fixture-v1",
                    confidence = .9, riskProbability = .8, modelVersion = "fixture", windowId = payload.windowId,
                    explanation = "Synthetic model fixture returned a high category for this source window.")
            ) else mapOf("glucose_variability" to glucose)
            onResult(Result.success(DashboardResponse(payload.patientId, payload.sessionId, payload.interval, payload.windowId,
                DASHBOARD_METRICS.associateWith { supported[it] ?: MetricResult.unavailable("Required sensor coverage is unavailable.") })))
            return PredictionRequest {}
        }
    }
}
