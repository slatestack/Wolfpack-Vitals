package com.example.wolfpackvitals.ui

import android.content.res.AssetManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.wolfpackvitals.BuildConfig
import com.example.wolfpackvitals.data.*
import com.example.wolfpackvitals.data.network.*
import com.example.wolfpackvitals.data.replay.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VitalsViewModel(
    private val patient16DataSource: Patient16DataSource,
    private val predictionClient: PredictionClient,
    monotonicNowMs: () -> Long = { SystemClock.uptimeMillis() },
    private val wallNowMs: () -> Long = { System.currentTimeMillis() },
    private val dataDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    companion object {
        fun factory(assets: AssetManager) = viewModelFactory {
            initializer {
                VitalsViewModel(
                    CsvPatient16DataSource { name -> assets.open("patient-16-data/$name").reader() },
                    OkHttpPredictionClient(BuildConfig.FASTAPI_BASE_URL)
                )
            }
        }
    }

    private var replayJob: Job? = null
    private var replaySession: Patient16ReplaySession? = null
    private val replayClock = ActiveReplayClock(monotonicNowMs)
    private val pendingSnapshots = ArrayDeque<ReplaySnapshot>()
    private val inFlightRequests = mutableMapOf<Int, PredictionRequest>()
    private var sessionId = 0
    private var sessionToken = java.util.UUID.randomUUID().toString()
    private var analysisGeneration = 0
    private var latestAttempt = 0
    private var latestSnapshot: ReplaySnapshot? = null

    private val collectedHistory = mutableListOf<Pair<Long, Pair<String, Int>>>()
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var readinessRequest: PredictionRequest? = null
    private var readinessReasons: Map<String, String?> = emptyMap()
    private var readinessGeneration = 0

    init { checkReadiness() }

    private fun checkReadiness() {
        val generation = ++readinessGeneration
        val checkingSession = sessionId
        val checkingAttempt = latestAttempt
        readinessRequest?.cancel()
        try {
            readinessRequest = predictionClient.readiness { outcome ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (generation != readinessGeneration || checkingSession != sessionId ||
                        checkingAttempt != latestAttempt) return@launch
                    readinessReasons = outcome.getOrNull()?.reasons ?: DASHBOARD_METRICS.associateWith {
                        "Cannot check analysis availability. Check the API connection and try syncing again."
                    }
                    markAnalysisPending("Complete a five-minute window to run analysis.")
                }
            }
        } catch (error: Exception) {
            readinessReasons = DASHBOARD_METRICS.associateWith { "Cannot check analysis availability. Check the API connection and try syncing again." }
            markAnalysisPending("Complete a five-minute window to run analysis.")
        }
    }

    // Manual sync uses the same timestamped analysis flow as replay.
    fun syncPipeline(onCompleted: (() -> Unit)? = null) {
        val snapshot = latestSnapshot
        if (snapshot == null) {
            checkReadiness()
            markAnalysisPending("Complete a five-minute window to run analysis.")
            onCompleted?.invoke()
            return
        }
        if (_uiState.value.pipelineStatus.isSyncing) return
        sendSnapshot(snapshot, onCompleted)
    }

    private fun markAnalysisPending(message: String, unavailable: Boolean = false) {
        _uiState.update { current ->
            fun pending(card: BiomarkerAnalysis): BiomarkerAnalysis {
                val reason = if (card.result?.available != true && !unavailable) readinessReasons[card.id] else null
                return card.pendingAnalysis(reason ?: message, unavailable || reason != null)
            }
            val risk = pending(current.pipelineStatus.analysis)
            current.copy(biomarkers = current.biomarkers.map(::pending),
                userProfile = current.userProfile.copy(databricksConnected = false),
                pipelineStatus = current.pipelineStatus.copy(analysis = risk, statusDescription = message,
                    lastSyncedText = risk.badgeText, riskLevelText = risk.severity ?: risk.badgeText,
                    clusterStatus = if (unavailable) "Analysis unavailable" else "Awaiting analysis"))
        }
    }

    // 2. Switch Heart Rate Chart Time Range
    fun setTimeRange(range: String) {
        val duration = when (range) {
            "1H" -> REPLAY_HOUR_MS
            "6H" -> 6 * REPLAY_HOUR_MS
            "7D" -> 7 * 24 * REPLAY_HOUR_MS
            else -> 24 * REPLAY_HOUR_MS
        }
        val elapsed = replaySession?.activeElapsedMs ?: 0L
        val history = collectedHistory.filter { it.first > elapsed - duration }.map { it.second }
        _uiState.update { current -> current.copy(heartRate = current.heartRate.copy(
            selectedRange = range,
            hourlyHistory = history,
            avgBpm = if (history.isEmpty()) 0 else history.map { it.second }.average().roundToInt(),
            minBpm = history.minOfOrNull { it.second } ?: 0,
            maxBpm = history.maxOfOrNull { it.second } ?: 0
        )) }
    }

    fun toggleChartStyle() {
        _uiState.update { it.copy(heartRate = it.heartRate.copy(isLineMode = !it.heartRate.isLineMode)) }
    }

    // 3. Filter Biomarkers on Dashboard
    fun setBiomarkerFilter(filter: String) {
        _uiState.update { current ->
            current.copy(selectedBiomarkerFilter = filter)
        }
    }

    // One ViewModel-owned replay job; taps only pause/resume the same clock and session.
    fun togglePatient16Replay() {
        when (_uiState.value.replay.phase) {
            ReplayPhase.INACTIVE, ReplayPhase.COMPLETED -> {
                startPatient16Replay()
            }
            ReplayPhase.RUNNING -> {
                checkpointReplay()
                replayClock.setActive(false)
                _uiState.update { it.copy(replay = it.replay.copy(phase = ReplayPhase.PAUSED)) }
                cancelInFlightRequests()
            }
            ReplayPhase.PAUSED -> {
                _uiState.update { it.copy(replay = it.replay.copy(phase = ReplayPhase.RUNNING)) }
                replayClock.setActive(_uiState.value.replay.isAdvancing)
            }
        }
    }

    /** The activity forwards lifecycle events; background wall-clock time never counts. */
    fun setApplicationActive(active: Boolean) {
        if (_uiState.value.replay.isApplicationActive == active) return
        checkpointReplay()
        _uiState.update { it.copy(replay = it.replay.copy(isApplicationActive = active)) }
        replayClock.setActive(_uiState.value.replay.isAdvancing)
        if (!active) cancelInFlightRequests()
    }

    private fun startPatient16Replay() {
        if (replayJob?.isActive == true) return
        cancelInFlightRequests()
        sessionId++
        sessionToken = java.util.UUID.randomUUID().toString()
        latestSnapshot = null
        latestAttempt = 0
        replaySession = null
        collectedHistory.clear()
        _uiState.update { it.copy(biomarkers = DashboardUiState().biomarkers,
            pipelineStatus = DatabricksPipelineStatus()) }
        checkReadiness()
        _uiState.update { it.copy(heartRate = HeartRateReading(selectedRange = it.heartRate.selectedRange, restingBpm = it.heartRate.restingBpm, isLineMode = it.heartRate.isLineMode)) }
        pendingSnapshots.clear()
        replayClock.setActive(false)
        _uiState.update { current -> current.copy(replay = Patient16ReplayState(
            phase = ReplayPhase.RUNNING,
            isLoading = true,
            isApplicationActive = current.replay.isApplicationActive
        )) }
        replayJob = viewModelScope.launch {
            try {
                val data = withContext(dataDispatcher) { patient16DataSource.load() }
                replaySession = Patient16ReplaySession(data)
                _uiState.update { it.copy(replay = it.replay.copy(isLoading = false)) }
                replayClock.setActive(_uiState.value.replay.isAdvancing)
                while (isActive) {
                    // Suspending on StateFlow avoids polling or counting time while paused.
                    uiState.first { it.replay.isAdvancing }
                    checkpointReplay()
                    while (pendingSnapshots.isNotEmpty() && _uiState.value.replay.isAdvancing) {
                        sendSnapshot(pendingSnapshots.removeFirst())
                    }
                    if (requireNotNull(replaySession).activeElapsedMs == REPLAY_HOUR_MS &&
                        pendingSnapshots.isEmpty()) {
                        replayClock.setActive(false)
                        _uiState.update { it.copy(replay = it.replay.copy(phase = ReplayPhase.COMPLETED)) }
                        break
                    }
                    delay(REPLAY_SAMPLE_INTERVAL_MS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                replayClock.setActive(false)
                _uiState.update { it.copy(replay = it.replay.copy(
                    phase = ReplayPhase.INACTIVE, isLoading = false,
                    dataError = error.message ?: "Unable to load Patient 16 data"
                )) }
                markAnalysisPending("Source recordings could not be loaded.", unavailable = true)
            }
        }
    }

    private fun checkpointReplay() {
        val elapsed = replayClock.takeElapsed()
        val session = replaySession ?: return
        val completed = session.advanceBy(elapsed)
        pendingSnapshots.addAll(completed)
        completed.forEach { snapshot ->
            val end = snapshot.activeElapsedMs / 60000
            collectedHistory += snapshot.activeElapsedMs to ("${end - 5}–${end}m" to snapshot.intervalHeartbeat.roundToInt())
        }
        if (completed.isNotEmpty()) {
            latestSnapshot = completed.last()
            setTimeRange(_uiState.value.heartRate.selectedRange)
            markAnalysisPending("Waiting for the current analysis window.")
        }
        _uiState.update { it.copy(replay = it.replay.copy(
            activeElapsedMs = session.activeElapsedMs, averages = session.averages
        )) }
    }

    private fun sendSnapshot(snapshot: ReplaySnapshot, onCompleted: (() -> Unit)? = null) {
        val sendingSessionId = sessionId
        val generation = analysisGeneration
        val attempt = _uiState.value.replay.transmissionAttempts + 1
        latestAttempt = attempt
        _uiState.update { it.copy(replay = it.replay.copy(transmissionAttempts = attempt),
            pipelineStatus = it.pipelineStatus.copy(isSyncing = true)) }
        val payload = snapshot.sourceWindow?.let {
            DashboardPayload("16", sessionToken, (snapshot.activeElapsedMs / REPLAY_SEND_INTERVAL_MS).toInt(), snapshot)
        }
        val onResult: (Result<DashboardResponse>) -> Unit = { outcome ->
            viewModelScope.launch(Dispatchers.Main) {
                if (sendingSessionId == sessionId && generation == analysisGeneration) {
                    inFlightRequests.remove(attempt)
                    // Also guard same-interval manual refreshes and callbacks after restart/pause.
                    if (attempt == latestAttempt && snapshot.activeElapsedMs == latestSnapshot?.activeElapsedMs) {
                        val result = outcome.mapCatching { response ->
                            require(payload != null && response.patientId == payload.patientId &&
                                response.sessionId == payload.sessionId && response.interval == payload.interval &&
                                response.windowId == payload.windowId)
                            response
                        }
                        _uiState.update { current -> current.copy(replay = current.replay.copy(
                            transmissionsCompleted = current.replay.transmissionsCompleted + 1,
                            successfulTransmissions = current.replay.successfulTransmissions + if (result.isSuccess) 1 else 0,
                            lastSuccessfulSendEpochMs = if (result.isSuccess) wallNowMs() else current.replay.lastSuccessfulSendEpochMs,
                            lastApiError = if (result.isFailure) "Analysis request failed." else null
                        ), pipelineStatus = current.pipelineStatus.copy(isSyncing = false)) }
                        result.fold(onSuccess = { response ->
                            _uiState.update { current ->
                                val risk = current.pipelineStatus.analysis.withResult(response.results["prediabetes_risk"]
                                    ?: MetricResult.unavailable("The risk estimate was not returned."))
                                current.copy(biomarkers = current.biomarkers.map { card -> card.withResult(response.results[card.id]
                                        ?: MetricResult.unavailable("The analysis was not returned.")) },
                                    userProfile = current.userProfile.copy(databricksConnected = response.results.values.any { it.available }),
                                    pipelineStatus = current.pipelineStatus.copy(analysis = risk,
                                        riskLevelText = risk.severity ?: risk.badgeText, lastSyncedText = risk.badgeText,
                                        clusterStatus = if (risk.badgeText in listOf("Stable", "Monitoring")) "Result available" else "Analysis unavailable",
                                        statusDescription = risk.description))
                            }
                        }, onFailure = { markAnalysisPending("Analysis request failed. Try syncing again.", unavailable = true) })
                        onCompleted?.invoke()
                    }
                }
            }
        }
        try {
            if (payload == null) onResult(Result.failure(IllegalStateException("Source timestamps unavailable")))
            else inFlightRequests[attempt] = predictionClient.analyze(payload, onResult)
        } catch (error: Exception) {
            onResult(Result.failure(error))
        }
    }

    private fun cancelInFlightRequests() {
        analysisGeneration++
        val requests = inFlightRequests.values.toList()
        inFlightRequests.clear()
        requests.forEach { it.cancel() }
        if (_uiState.value.pipelineStatus.isSyncing) {
            markAnalysisPending("Analysis was interrupted. Try syncing again.", unavailable = true)
            _uiState.update { it.copy(pipelineStatus = it.pipelineStatus.copy(isSyncing = false)) }
        }
    }

    override fun onCleared() {
        readinessGeneration++
        readinessRequest?.cancel()
        replayClock.setActive(false)
        cancelInFlightRequests()
        super.onCleared()
    }

    // 5. Connect / Disconnect Hardware Devices
    fun toggleDeviceConnection(deviceId: String) {
        _uiState.update { current ->
            val updatedDevices = current.devices.map { device ->
                if (device.id == deviceId) {
                    val nextConnected = !device.isConnected
                    device.copy(
                        isConnected = nextConnected,
                        lastSyncTime = if (nextConnected) "Just connected" else "Disconnected"
                    )
                } else {
                    device
                }
            }
            current.copy(devices = updatedDevices)
        }
    }

    // 6. Pair New Discovered Bluetooth Device
    fun addPairedDevice(name: String, type: String, modalities: String) {
        val newDevice = ConnectedDevice(
            id = "dev_${System.currentTimeMillis()}",
            name = name,
            type = type,
            isConnected = true,
            batteryPercent = (80..100).random(),
            lastSyncTime = "Active (Just paired)",
            modalities = modalities
        )
        _uiState.update { current ->
            current.copy(devices = current.devices + newDevice)
        }
    }

    // 8. Update User Profile Info
    fun updateUserProfile(name: String, id: String, email: String, cohort: String = "") {
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { "WP" }

        _uiState.update { current ->
            current.copy(
                userProfile = current.userProfile.copy(
                    name = name,
                    id = id,
                    email = email,
                    studyCohort = cohort,
                    initials = initials
                )
            )
        }
    }

    // 9. Update Health Baselines & Recalculate BMI
    fun updateHealthProfile(
        age: Int,
        sex: String,
        heightCm: Float,
        weightKg: Float,
        restingBpm: Int,
        fastingGlucose: Int,
        hba1c: Float
    ) {
        val heightM = heightCm / 100f
        val calculatedBmi = if (heightM > 0) {
            (weightKg / (heightM * heightM) * 10f).roundToInt() / 10f
        } else {
            22.0f
        }

        _uiState.update { current ->
            current.copy(
                healthProfile = current.healthProfile.copy(
                    age = age,
                    sex = sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    bmi = calculatedBmi,
                    restingBpmBaseline = restingBpm,
                    fastingGlucoseMgDl = fastingGlucose,
                    hba1cPercent = hba1c
                ),
                heartRate = current.heartRate.copy(
                    restingBpm = restingBpm
                )
            )
        }
    }

    // 10. Update App Preferences & Settings
    fun updateSettings(
        liveStreaming: Boolean? = null,
        notificationsEnabled: Boolean? = null,
        anomalousSpikes: Boolean? = null,
        riskThresholds: Boolean? = null,
        offlineCaching: Boolean? = null,
        deidentified: Boolean? = null
    ) {
        if (liveStreaming != null && liveStreaming != (_uiState.value.replay.phase == ReplayPhase.RUNNING)) {
            togglePatient16Replay()
        }
        _uiState.update { current ->
            val currentSettings = current.settings
            val newLiveStreaming = liveStreaming ?: currentSettings.isLiveStreamingEnabled
            current.copy(
                settings = currentSettings.copy(
                    isLiveStreamingEnabled = newLiveStreaming,
                    notificationsEnabled = notificationsEnabled ?: currentSettings.notificationsEnabled,
                    anomalousSpikeAlerts = anomalousSpikes ?: currentSettings.anomalousSpikeAlerts,
                    riskThresholdAlerts = riskThresholds ?: currentSettings.riskThresholdAlerts,
                    isOfflineCachingEnabled = offlineCaching ?: currentSettings.isOfflineCachingEnabled,
                    isTelemetryDeidentified = deidentified ?: currentSettings.isTelemetryDeidentified
                )
            )
        }
    }

    // 11. Quick Log Vital Reading for specific hour
    fun logManualVital(hour: String, bpm: Int) {
        val label = hour.trim()
        val index = collectedHistory.indexOfFirst { it.second.first == label }
        val point = (replaySession?.activeElapsedMs ?: 0L) to (label to bpm)
        if (index >= 0) collectedHistory[index] = point else collectedHistory.add(point)
        setTimeRange(_uiState.value.heartRate.selectedRange)
        _uiState.update { it.copy(heartRate = it.heartRate.copy(lastUpdatedHour = label)) }
    }

    // Overload for current hour logging
    fun logManualVital(bpm: Int) {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hourString = String.format("%02d:00", currentHour)
        logManualVital(hourString, bpm)
    }

    // 12. Clear Cache Simulation
    fun clearLocalCache(onCleared: () -> Unit) {
        viewModelScope.launch {
            delay(500)
            onCleared()
        }
    }

    // 13. Handle User Authentication via CSV
    fun onUserAuthenticated(user: UserRecord) {
        val initials = user.name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { "WP" }

        val wolfId = "WOLF-" + Math.abs(user.email.hashCode()).toString().takeLast(4).padStart(4, '8') + "-A"

        _uiState.update { current ->
            current.copy(
                userProfile = current.userProfile.copy(
                    name = user.name,
                    email = user.email,
                    id = wolfId,
                    initials = initials
                )
            )
        }
    }

    // 14. Log Out Active Participant
    fun logout() {
        _uiState.update { current ->
            current.copy(
                userProfile = UserProfile(
                    name = "Participant",
                    id = "WOLF-DEMO",
                    initials = "WP",
                    email = ""
                )
            )
        }
    }
}
