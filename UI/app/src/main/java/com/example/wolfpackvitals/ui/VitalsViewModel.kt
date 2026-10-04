package com.example.wolfpackvitals.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wolfpackvitals.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VitalsViewModel : ViewModel() {

    private val full24HHistory = mutableListOf(
        "00:00" to 62, "01:00" to 60, "02:00" to 58, "03:00" to 59,
        "04:00" to 61, "05:00" to 65, "06:00" to 72, "07:00" to 85,
        "08:00" to 98, "09:00" to 88, "10:00" to 92, "11:00" to 105,
        "12:00" to 118, "13:00" to 110, "14:00" to 95, "15:00" to 88,
        "16:00" to 82, "17:00" to 78, "18:00" to 92, "19:00" to 106,
        "20:00" to 85, "21:00" to 76, "22:00" to 70, "23:00" to 65
    )

    private val history1H = mutableListOf(
        "0m" to 71, "5m" to 68, "10m" to 69, "15m" to 73, "20m" to 75,
        "25m" to 72, "30m" to 70, "35m" to 68, "40m" to 74, "45m" to 71,
        "50m" to 69, "55m" to 70
    )

    private val history6H = mutableListOf(
        "12:00" to 118, "13:00" to 110, "14:00" to 95,
        "15:00" to 88, "16:00" to 82, "17:00" to 78
    )

    private val history7D = mutableListOf(
        "Mon" to 72, "Tue" to 68, "Wed" to 74,
        "Thu" to 70, "Fri" to 76, "Sat" to 69, "Sun" to 71
    )

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            heartRate = HeartRateReading(
                hourlyHistory = full24HHistory.toList(),
                avgBpm = (full24HHistory.map { it.second }.average()).roundToInt(),
                minBpm = full24HHistory.minOf { it.second },
                maxBpm = full24HHistory.maxOf { it.second },
                selectedRange = "24H"
            )
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // 1. Sync & Re-run Machine Learning Pipeline
    fun syncPipeline(onCompleted: (() -> Unit)? = null) {
        if (_uiState.value.pipelineStatus.isSyncing) return

        _uiState.update { current ->
            current.copy(
                pipelineStatus = current.pipelineStatus.copy(
                    isSyncing = true,
                    statusDescription = ""
                )
            )
        }

        viewModelScope.launch {
            delay(1400)
            _uiState.update { current ->
                val newAvg = (68..74).random()
                current.copy(
                    heartRate = current.heartRate.copy(avgBpm = newAvg),
                    pipelineStatus = current.pipelineStatus.copy(
                        isSyncing = false,
                        lastSyncedText = "Just now",
                        riskLevelText = "Optimal (X-FuzzEn = 0.081)",
                        statusDescription = ""
                    )
                )
            }
            onCompleted?.invoke()
        }
    }

    // 2. Switch Heart Rate Chart Time Range
    fun setTimeRange(range: String) {
        val activeHistory = when (range) {
            "1H" -> history1H.toList()
            "6H" -> history6H.toList()
            "7D" -> history7D.toList()
            else -> full24HHistory.toList()
        }

        val calculatedAvg = (activeHistory.map { it.second }.average()).roundToInt()
        val calculatedMin = activeHistory.minOf { it.second }
        val calculatedMax = activeHistory.maxOf { it.second }

        _uiState.update { current ->
            current.copy(
                heartRate = current.heartRate.copy(
                    selectedRange = range,
                    hourlyHistory = activeHistory,
                    avgBpm = calculatedAvg,
                    minBpm = calculatedMin,
                    maxBpm = calculatedMax
                )
            )
        }
    }

    // 3. Filter Biomarkers on Dashboard
    fun setBiomarkerFilter(filter: String) {
        _uiState.update { current ->
            current.copy(selectedBiomarkerFilter = filter)
        }
    }

    // 4. Toggle Live Sensor Stream
    fun toggleStreaming() {
        _uiState.update { current ->
            val newState = !current.isStreamingActive
            current.copy(
                isStreamingActive = newState,
                pipelineStatus = current.pipelineStatus.copy(
                    clusterStatus = if (newState) "Running (Online)" else "Paused (Standby)"
                )
            )
        }
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
        _uiState.update { current ->
            val currentSettings = current.settings
            val newLiveStreaming = liveStreaming ?: currentSettings.isLiveStreamingEnabled
            current.copy(
                isStreamingActive = liveStreaming ?: current.isStreamingActive,
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
        val trimmedHour = hour.trim()

        // 1. Update 24H history entry matching this hour
        val index24 = full24HHistory.indexOfFirst {
            it.first.equals(trimmedHour, ignoreCase = true) ||
            it.first.startsWith(trimmedHour.substringBefore(":")) ||
            trimmedHour.startsWith(it.first.substringBefore(":"))
        }

        if (index24 != -1) {
            full24HHistory[index24] = full24HHistory[index24].first to bpm
        } else {
            val formattedHour = if (trimmedHour.contains(":")) trimmedHour else String.format("%02d:00", trimmedHour.toIntOrNull() ?: 12)
            val existing = full24HHistory.indexOfFirst { it.first == formattedHour }
            if (existing != -1) {
                full24HHistory[existing] = formattedHour to bpm
            } else {
                full24HHistory.add(formattedHour to bpm)
                full24HHistory.sortBy { it.first }
            }
        }

        // 2. Also update 6H history if in range
        val index6H = history6H.indexOfFirst {
            it.first.equals(trimmedHour, ignoreCase = true) ||
            it.first.startsWith(trimmedHour.substringBefore(":"))
        }
        if (index6H != -1) {
            history6H[index6H] = history6H[index6H].first to bpm
        }

        // 3. Update 1H history latest reading
        if (history1H.isNotEmpty()) {
            history1H[history1H.size - 1] = history1H.last().first to bpm
        }

        // 4. Update today's point in 7D
        val todayIndex = 6
        if (history7D.size > todayIndex) {
            history7D[todayIndex] = history7D[todayIndex].first to bpm
        }

        // 5. Select active history based on range
        val currentRange = _uiState.value.heartRate.selectedRange
        val activeHistory = when (currentRange) {
            "1H" -> history1H.toList()
            "6H" -> history6H.toList()
            "7D" -> history7D.toList()
            else -> full24HHistory.toList()
        }

        val calculatedAvg = (activeHistory.map { it.second }.average()).roundToInt()
        val calculatedMin = activeHistory.minOf { it.second }
        val calculatedMax = activeHistory.maxOf { it.second }

        _uiState.update { current ->
            current.copy(
                heartRate = current.heartRate.copy(
                    hourlyHistory = activeHistory,
                    avgBpm = calculatedAvg,
                    minBpm = calculatedMin,
                    maxBpm = calculatedMax,
                    lastUpdatedHour = trimmedHour
                )
            )
        }
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
