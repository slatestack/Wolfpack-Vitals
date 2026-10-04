package com.example.wolfpackvitals.ui

import androidx.lifecycle.ViewModelStore
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

    @Test fun uninterruptedHourSendsTwelveRequestsAndNewHourStartsAtFirstRow() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        assertTrue(client.payloads.isEmpty())
        advanceTimeBy(REPLAY_SEND_INTERVAL_MS - 1)
        runCurrent()
        assertTrue(client.payloads.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, client.payloads.size)
        advanceTimeBy(REPLAY_HOUR_MS - REPLAY_SEND_INTERVAL_MS)
        runCurrent()
        val state = vm.uiState.value.replay
        assertEquals(ReplayPhase.COMPLETED, state.phase)
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

    private class RecordingPredictionClient : PredictionClient {
        val payloads = mutableListOf<PredictionPayload>()
        var failFirst = false
        var autoComplete = true
        var cancellations = 0
        override fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest {
            payloads += payload
            if (autoComplete) onResult(
                if (failFirst && payloads.size == 1) Result.failure(IOException("Test network failure"))
                else Result.success("prediction")
            )
            return PredictionRequest {
                cancellations++
                if (!autoComplete) onResult(Result.failure(IOException("Request cancelled during pause")))
            }
        }
    }
}
