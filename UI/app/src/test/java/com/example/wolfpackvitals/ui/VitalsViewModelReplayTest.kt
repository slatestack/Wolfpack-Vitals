package com.example.wolfpackvitals.ui

import androidx.lifecycle.ViewModelStore
import com.example.wolfpackvitals.data.network.*
import com.example.wolfpackvitals.data.network.PredictionClient
import com.example.wolfpackvitals.data.network.PredictionPayload
import com.example.wolfpackvitals.data.network.PredictionRequest
import com.example.wolfpackvitals.data.replay.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class VitalsViewModelReplayTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private val client = RecordingPredictionClient()
    private var loadCount = 0

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(): VitalsViewModel {
        val vm = VitalsViewModel(
            Patient16DataSource { loadCount++; repositoryPatient16Data() }, client,
            monotonicNowMs = { scheduler.currentTime }, wallNowMs = { scheduler.currentTime },
            dataDispatcher = dispatcher
        )
        store.put("vitals", vm)
        vm.setApplicationActive(true)
        vm.togglePatient16Replay()
        return vm
    }

    @Test fun missingConfigurationIsExplainedBeforeFiveMinutesAndSurvivesCheckpoints() = runTest(dispatcher) {
        client.readinessResult = Result.success(AnalysisReadiness(DASHBOARD_METRICS.associateWith {
            "Analysis is not configured. Ask the service operator to install a verified workflow."
        }))
        val vm = viewModel()
        runCurrent()
        assertTrue(client.requests.isEmpty())
        assertTrue(vm.uiState.value.biomarkers.all { it.badgeText == "Unavailable" && it.description.contains("operator") })
        assertEquals("Unavailable", vm.uiState.value.pipelineStatus.analysis.badgeText)
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS - 1)
        runCurrent()
        assertTrue(client.requests.isEmpty())
        assertTrue(vm.uiState.value.biomarkers.all { it.description.contains("operator") })
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, client.requests.size)
    }

    @Test fun readinessNetworkFailureIsActionableAndDoesNotBlockCollection() = runTest(dispatcher) {
        client.readinessResult = Result.failure(IOException("sensitive hostname"))
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.uiState.value.biomarkers.all { it.description.contains("API connection") && !it.description.contains("sensitive") })
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertEquals(1, client.requests.size)
        assertEquals(1, vm.uiState.value.heartRate.hourlyHistory.size)
    }

    @Test fun unavailableRefreshRetainsGlucoseWhileHRVRemainsAvailable() = runTest(dispatcher) {
        client.autoComplete = false
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        client.complete(0, mapOf("glucose_variability" to valid(), "hrv" to valid(RiskCategory.LOW)))
        runCurrent()
        val previous = vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.result
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        client.complete(1, mapOf("hrv" to valid(RiskCategory.LOW)))
        runCurrent()
        assertEquals("Outdated", vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.badgeText)
        assertEquals(previous, vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.result)
        assertEquals("Stable", vm.uiState.value.biomarkers.first { it.id == "hrv" }.badgeText)
        assertEquals("Unavailable", vm.uiState.value.pipelineStatus.analysis.badgeText)
    }

    @Test fun liveStreamSettingsPauseAndResumeTheSameReplaySession() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(60000)
        runCurrent()
        vm.updateSettings(liveStreaming = false)
        runCurrent()
        assertEquals(ReplayPhase.PAUSED, vm.uiState.value.replay.phase)
        val elapsed = vm.uiState.value.replay.activeElapsedMs
        advanceTimeBy(60000)
        runCurrent()
        assertEquals(elapsed, vm.uiState.value.replay.activeElapsedMs)
        vm.updateSettings(liveStreaming = true)
        runCurrent()
        assertEquals(ReplayPhase.RUNNING, vm.uiState.value.replay.phase)
        assertEquals(1, loadCount)
        assertTrue(vm.uiState.value.settings.isLiveStreamingEnabled)
    }

    @Test fun uninterruptedHourSendsTwelveRequestsAndNewHourStartsAtFirstRow() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        assertTrue(client.payloads.isEmpty())
        assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS - 1)
        runCurrent()
        assertTrue(client.payloads.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, client.payloads.size)
        assertEquals(1, vm.uiState.value.heartRate.hourlyHistory.size)
        advanceTimeBy(REPLAY_HOUR_MS - REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        val state = vm.uiState.value.replay
        assertEquals(ReplayPhase.COMPLETED, state.phase)
        assertEquals(12, vm.uiState.value.heartRate.hourlyHistory.size)
        assertEquals("0–5m", vm.uiState.value.heartRate.hourlyHistory.first().first)
        assertEquals("55–60m", vm.uiState.value.heartRate.hourlyHistory.last().first)
        assertEquals(12, state.transmissionAttempts)
        assertEquals(12, state.transmissionsCompleted)
        assertEquals(12, state.successfulTransmissions)
        assertEquals(3600, state.averages!!.sampleCountPerModality)
        val expected = Patient16ReplaySession(repositoryPatient16Data()).advanceBy(REPLAY_HOUR_MS)
        assertEquals(expected.map { PredictionPayload.from(it.averages) }, client.payloads)
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertEquals(12, client.payloads.size)
        vm.togglePatient16Replay()
        runCurrent()
        assertEquals(0, vm.uiState.value.replay.activeElapsedMs)
        assertNull(vm.uiState.value.replay.averages)
        assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(71.0, vm.uiState.value.replay.averages!!.heartbeat, 0.0)
        assertEquals(2, loadCount)
    }

    @Test fun pauseAtThirteenMinutesAndBackgroundTimePreserveTheSameHour() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(13 * 60000L)
        runCurrent()
        vm.togglePatient16Replay()
        val paused = vm.uiState.value.replay
        assertEquals(2, client.payloads.size)
        advanceTimeBy(REPLAY_HOUR_MS)
        runCurrent()
        assertEquals(paused, vm.uiState.value.replay)
        vm.togglePatient16Replay()
        runCurrent()
        advanceTimeBy(60000)
        runCurrent()
        vm.setApplicationActive(false)
        val background = vm.uiState.value.replay
        advanceTimeBy(REPLAY_HOUR_MS)
        runCurrent()
        assertEquals(background, vm.uiState.value.replay)
        vm.setApplicationActive(true)
        runCurrent()
        advanceTimeBy(60000)
        runCurrent()
        assertEquals(3, client.payloads.size)
        assertEquals(900, vm.uiState.value.replay.averages!!.sampleCountPerModality)
        assertEquals(1, loadCount)
        val expected = Patient16ReplaySession(repositoryPatient16Data()).advanceBy(15 * 60000L).last()
        assertEquals(PredictionPayload.from(expected.averages), client.payloads.last())
    }

    @Test fun networkFailureDoesNotResetAggregatesOrRetryTheFailedSend() = runTest(dispatcher) {
        client.failFirst = true
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertNotNull(vm.uiState.value.replay.lastApiError)
        assertEquals(1, vm.uiState.value.heartRate.hourlyHistory.size)
        assertTrue(vm.uiState.value.biomarkers.all { it.badgeText == "Unavailable" })
        assertEquals(ReplayPhase.RUNNING, vm.uiState.value.replay.phase)
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertEquals(2, client.payloads.size)
        assertEquals(600, vm.uiState.value.replay.averages!!.sampleCountPerModality)
        assertEquals(1, vm.uiState.value.replay.successfulTransmissions)
        assertNull(vm.uiState.value.replay.lastApiError)
        assertNotNull(vm.uiState.value.replay.lastSuccessfulSendEpochMs)
    }

    @Test fun inFlightRequestDoesNotBlockAggregationAndPauseCancelsItWithoutDuplicate() = runTest(dispatcher) {
        client.autoComplete = false
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS + 60000)
        runCurrent()
        assertEquals(360, vm.uiState.value.replay.averages!!.sampleCountPerModality)
        vm.togglePatient16Replay()
        runCurrent()
        assertEquals(1, client.cancellations)
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertEquals(1, client.payloads.size)
        vm.togglePatient16Replay()
        runCurrent()
        advanceTimeBy(4 * 60000)
        runCurrent()
        assertEquals(2, client.payloads.size)
        assertEquals(600, vm.uiState.value.replay.averages!!.sampleCountPerModality)
    }

    @Test fun rapidPauseResumeDoesNotCreateAnotherSessionOrLosePartialSeconds() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(1300)
        vm.togglePatient16Replay()
        repeat(10) {
            vm.togglePatient16Replay()
            vm.togglePatient16Replay()
        }
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(1300L, vm.uiState.value.replay.activeElapsedMs)
        vm.togglePatient16Replay()
        runCurrent()
        advanceTimeBy(REPLAY_HOUR_MS - 1300 + 1000)
        runCurrent()
        assertEquals(12, client.payloads.size)
        assertEquals(1, loadCount)
        assertEquals(ReplayPhase.COMPLETED, vm.uiState.value.replay.phase)
    }

    @Test fun rangeChangesUseOnlyCollectedIntervalsAndBothStylesShareState() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        listOf("1H", "6H", "24H", "7D").forEach { range ->
            vm.setTimeRange(range)
            assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        }
        advanceTimeBy(10 * 60000L)
        runCurrent()
        val collected = vm.uiState.value.heartRate.hourlyHistory
        assertEquals(2, collected.size)
        listOf("1H", "6H", "24H", "7D").forEach { range ->
            vm.setTimeRange(range)
            assertEquals(collected, vm.uiState.value.heartRate.hourlyHistory)
        }
        vm.toggleChartStyle()
        assertFalse(vm.uiState.value.heartRate.isLineMode)
        vm.toggleChartStyle()
        assertTrue(vm.uiState.value.heartRate.isLineMode)
    }

    @Test fun intervalMeansDoNotUseCumulativeHourMeans() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(10 * 60000L)
        runCurrent()
        val snapshots = Patient16ReplaySession(repositoryPatient16Data()).advanceBy(10 * 60000L)
        assertEquals(snapshots.map { kotlin.math.round(it.intervalHeartbeat).toInt() },
            vm.uiState.value.heartRate.hourlyHistory.map { it.second })
        assertNotEquals(snapshots[1].averages.heartbeat, snapshots[1].intervalHeartbeat, 0.01)
    }

    private fun valid(category: RiskCategory = RiskCategory.MODERATE) = MetricResult(
        true, value = 17.0, unit = "test", category = category, barPosition = 0.6f,
        referenceRange = "Model test range", thresholdVersion = "test-v1", confidence = null,
        modelVersion = "test-model", windowId = "test-window")

    @Test fun newestResponseWinsPartialResultsUpdateAndPreviousResultBecomesOutdated() = runTest(dispatcher) {
        client.autoComplete = false
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(10 * 60000L)
        runCurrent()
        client.complete(1, mapOf("glucose_variability" to valid()))
        runCurrent()
        val glucose = vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }
        assertEquals("Monitoring", glucose.badgeText)
        assertEquals("Moderate", glucose.severity)
        assertEquals("—", glucose.confidenceScore)
        assertEquals("Unavailable", vm.uiState.value.biomarkers.first().badgeText)
        client.complete(0, mapOf("glucose_variability" to valid(RiskCategory.LOW)))
        runCurrent()
        assertEquals(glucose, vm.uiState.value.biomarkers.first { it.id == "glucose_variability" })
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        assertEquals("Outdated", vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.badgeText)
        client.callbacks[2](Result.failure(IOException("Sensitive raw error")))
        runCurrent()
        val old = vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }
        assertEquals("Outdated", old.badgeText)
        assertEquals(glucose.progress, old.progress)
        assertFalse(old.description.contains("Sensitive"))
    }

    @Test fun restartAndCancelledCallbacksCannotOverwriteTheNewSession() = runTest(dispatcher) {
        client.autoComplete = false
        val vm = viewModel()
        runCurrent()
        advanceTimeBy(REPLAY_HOUR_MS)
        runCurrent()
        vm.togglePatient16Replay()
        runCurrent()
        client.complete(11, mapOf("glucose_variability" to valid()))
        runCurrent()
        assertTrue(vm.uiState.value.heartRate.hourlyHistory.isEmpty())
        assertTrue(vm.uiState.value.biomarkers.all { it.badgeText == "Collecting data" })
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        vm.togglePatient16Replay()
        client.complete(12, mapOf("glucose_variability" to valid()))
        runCurrent()
        assertTrue(vm.uiState.value.biomarkers.none { it.badgeText == "Monitoring" })
    }

    @Test fun syncUsesTheLatestReplayWindowAndSameIntervalRefreshRejectsOlderResponses() = runTest(dispatcher) {
        client.autoComplete = false
        val vm = viewModel()
        runCurrent()
        vm.syncPipeline()
        assertTrue(client.requests.isEmpty())
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        client.complete(0)
        runCurrent()
        vm.syncPipeline()
        runCurrent()
        assertEquals(client.requests[0], client.requests[1])
        client.complete(1, mapOf("glucose_variability" to valid()))
        runCurrent()
        client.complete(0, mapOf("glucose_variability" to valid(RiskCategory.LOW)))
        runCurrent()
        assertEquals("Moderate", vm.uiState.value.biomarkers.first { it.id == "glucose_variability" }.severity)
    }

    private class RecordingPredictionClient : PredictionClient {
        override fun readiness(onResult: (Result<AnalysisReadiness>) -> Unit): PredictionRequest {
            onResult(readinessResult)
            return PredictionRequest {}
        }
        var readinessResult = Result.success(AnalysisReadiness(DASHBOARD_METRICS.associateWith { null }))
        val payloads = mutableListOf<PredictionPayload>()
        val requests = mutableListOf<DashboardPayload>()
        val callbacks = mutableListOf<(Result<DashboardResponse>) -> Unit>()
        var failFirst = false
        var autoComplete = true
        var cancellations = 0
        override fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest =
            error("Replay must use the structured POST contract")

        fun complete(index: Int, results: Map<String, MetricResult> = emptyMap()) {
            val request = requests[index]
            callbacks[index](Result.success(DashboardResponse(request.patientId, request.sessionId, request.interval,
                request.windowId, DASHBOARD_METRICS.associateWith { results[it] ?: MetricResult.unavailable("Test unsupported model") })))
        }
        override fun analyze(payload: DashboardPayload, onResult: (Result<DashboardResponse>) -> Unit): PredictionRequest {
            requests += payload
            payloads += PredictionPayload.from(payload.snapshot.averages)
            callbacks += onResult
            if (autoComplete) {
                if (failFirst && payloads.size == 1) onResult(Result.failure(IOException("Test network failure")))
                else complete(requests.lastIndex)
            }
            return PredictionRequest {
                cancellations++
                if (!autoComplete) onResult(Result.failure(IOException("Request cancelled during pause")))
            }
        }
    }
}
