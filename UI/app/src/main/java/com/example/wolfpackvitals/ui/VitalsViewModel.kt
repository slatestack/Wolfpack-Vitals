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

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // 1. Sync & Re-run Machine Learning Pipeline
    fun syncPipeline(onCompleted: (() -> Unit)? = null) {
        if (_uiState.value.pipelineStatus.isSyncing) return

        _uiState.update { current ->
            current.copy(
                pipelineStatus = current.pipelineStatus.copy(
                    isSyncing = true,
                    statusDescription = "Executing 64Hz multi-modal inference pipeline on Databricks Apache Spark ML..."
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
                        statusDescription = "Processing real-time LightGBM ensemble models on 64Hz stream to assess physiological resilience."
                    )
                )
            }
            onCompleted?.invoke()
        }
    }

    // 2. Switch Heart Rate Chart Time Range
    fun setTimeRange(range: String) {
        val newHistory = when (range) {
            "1H" -> listOf(
                "0m" to 71, "5m" to 68, "10m" to 69, "15m" to 73, "20m" to 75,
                "25m" to 72, "30m" to 70, "35m" to 68, "40m" to 74, "45m" to 71,
                "50m" to 69, "55m" to 70
            )
            "6H" -> listOf(
                "12:00" to 118, "13:00" to 110, "14:00" to 95,
                "15:00" to 88, "16:00" to 82, "17:00" to 78
            )
            "7D" -> listOf(
                "Mon" to 72, "Tue" to 68, "Wed" to 74,
                "Thu" to 70, "Fri" to 76, "Sat" to 69, "Sun" to 71
            )
            else -> listOf( // "24H"
                "00:00" to 62, "01:00" to 60, "02:00" to 58, "03:00" to 59,
                "04:00" to 61, "05:00" to 65, "06:00" to 72, "07:00" to 85,
                "08:00" to 98, "09:00" to 88, "10:00" to 92, "11:00" to 105,
                "12:00" to 118, "13:00" to 110, "14:00" to 95, "15:00" to 88,
                "16:00" to 82, "17:00" to 78, "18:00" to 92, "19:00" to 106,
                "20:00" to 85, "21:00" to 76, "22:00" to 70, "23:00" to 65
            )
        }

        val calculatedAvg = (newHistory.map { it.second }.average()).roundToInt()
        val calculatedMin = newHistory.minOf { it.second }
        val calculatedMax = newHistory.maxOf { it.second }

        _uiState.update { current ->
            current.copy(
                heartRate = current.heartRate.copy(
                    selectedRange = range,
                    hourlyHistory = newHistory,
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

    // 7. Toggle Research Study Cohort Enrollment
    fun toggleStudyEnrollment(studyId: String) {
        _uiState.update { current ->
            val updatedStudies = current.studies.map { study ->
                if (study.id == studyId) {
                    study.copy(isEnrolled = !study.isEnrolled)
                } else {
                    study
                }
            }
            current.copy(studies = updatedStudies)
        }
    }

    // 8. Update User Profile Info
    fun updateUserProfile(name: String, id: String, email: String, cohort: String) {
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
        streamingFrequencyHz: Int? = null,
        notificationsEnabled: Boolean? = null,
        anomalousSpikes: Boolean? = null,
        riskThresholds: Boolean? = null,
        offlineCaching: Boolean? = null,
        deidentified: Boolean? = null
    ) {
        _uiState.update { current ->
            val currentSettings = current.settings
            current.copy(
                settings = currentSettings.copy(
                    streamingFrequencyHz = streamingFrequencyHz ?: currentSettings.streamingFrequencyHz,
                    notificationsEnabled = notificationsEnabled ?: currentSettings.notificationsEnabled,
                    anomalousSpikeAlerts = anomalousSpikes ?: currentSettings.anomalousSpikeAlerts,
                    riskThresholdAlerts = riskThresholds ?: currentSettings.riskThresholdAlerts,
                    isOfflineCachingEnabled = offlineCaching ?: currentSettings.isOfflineCachingEnabled,
                    isTelemetryDeidentified = deidentified ?: currentSettings.isTelemetryDeidentified
                )
            )
        }
    }

    // 11. Quick Log Vital Reading
    fun logManualVital(bpm: Int) {
        _uiState.update { current ->
            val history = current.heartRate.hourlyHistory.toMutableList()
            history.add("Now" to bpm)
            val updatedAvg = (history.takeLast(10).map { it.second }.average()).roundToInt()
            current.copy(
                heartRate = current.heartRate.copy(
                    avgBpm = updatedAvg,
                    hourlyHistory = history
                )
            )
        }
    }

    // 12. Clear Cache Simulation
    fun clearLocalCache(onCleared: () -> Unit) {
        viewModelScope.launch {
            delay(500)
            onCleared()
        }
    }
}
