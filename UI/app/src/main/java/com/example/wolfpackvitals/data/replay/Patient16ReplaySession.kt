package com.example.wolfpackvitals.data.replay

const val REPLAY_HOUR_MS = 60L * 60 * 1000
const val REPLAY_SEND_INTERVAL_MS = 5L * 60 * 1000
const val REPLAY_SAMPLE_INTERVAL_MS = 1000L

enum class ReplayPhase { INACTIVE, RUNNING, PAUSED, COMPLETED }

data class HourAverages(
    val heartbeat: Double,
    val glucose: Double,
    val interbeatInterval: Double,
    val acceleration: Acceleration,
    val sampleCountPerModality: Int
)

data class ReplaySnapshot(val activeElapsedMs: Long, val averages: HourAverages, val intervalHeartbeat: Double, val sourceWindow: AnalysisWindow? = null)

data class Patient16ReplayState(
    val phase: ReplayPhase = ReplayPhase.INACTIVE,
    val isLoading: Boolean = false,
    val isApplicationActive: Boolean = false,
    val activeElapsedMs: Long = 0,
    val transmissionAttempts: Int = 0,
    val transmissionsCompleted: Int = 0,
    val successfulTransmissions: Int = 0,
    val lastSuccessfulSendEpochMs: Long? = null,
    val lastApiError: String? = null,
    val dataError: String? = null,
    val averages: HourAverages? = null
) {
    val remainingHourMs: Long get() = (REPLAY_HOUR_MS - activeElapsedMs).coerceAtLeast(0)
    val untilNextTransmissionMs: Long get() = if (remainingHourMs == 0L) 0 else
        REPLAY_SEND_INTERVAL_MS - activeElapsedMs % REPLAY_SEND_INTERVAL_MS
    val isAdvancing: Boolean get() = phase == ReplayPhase.RUNNING && isApplicationActive && !isLoading
    val badgeLabel: String get() = when (phase) {
        ReplayPhase.INACTIVE -> "Start"
        ReplayPhase.RUNNING -> if (isLoading) "Loading" else "Pause"
        ReplayPhase.PAUSED -> "Resume"
        ReplayPhase.COMPLETED -> "Start again"
    }
}

/** One running hour, with independent looping cursors and a single arithmetic sum/count per axis. */
class Patient16ReplaySession(private val data: Patient16Data) {
    var activeElapsedMs = 0L
        private set
    private val accumulator = HourAccumulator()
    private var intervalHrSum = 0.0
    private var intervalHrCount = 0
    val averages: HourAverages? get() = accumulator.averages

    /** Consume one existing valid row per modality per active second, starting at row zero.
     * Catch-up still takes each snapshot exactly at its hour-relative five-minute boundary.
     * Taking a snapshot NEVER resets sums or cursors. A new instance starts a new hour.
     */
    fun advanceBy(activeMillis: Long): List<ReplaySnapshot> {
        require(activeMillis >= 0)
        val target = (activeElapsedMs + activeMillis).coerceAtMost(REPLAY_HOUR_MS)
        val snapshots = mutableListOf<ReplaySnapshot>()
        while ((accumulator.sampleCount + 1) * REPLAY_SAMPLE_INTERVAL_MS <= target) {
            val cursor = accumulator.sampleCount
            intervalHrSum += data.heartbeat[cursor % data.heartbeat.size]
            intervalHrCount++
            accumulator.add(
                data.heartbeat[cursor % data.heartbeat.size],
                data.glucose[cursor % data.glucose.size],
                data.interbeatInterval[cursor % data.interbeatInterval.size],
                data.acceleration[cursor % data.acceleration.size]
            )
            val sampleTime = accumulator.sampleCount * REPLAY_SAMPLE_INTERVAL_MS
            if (sampleTime % REPLAY_SEND_INTERVAL_MS == 0L) {
                snapshots += ReplaySnapshot(sampleTime, requireNotNull(averages), intervalHrSum / intervalHrCount,
                    if (data.sourceSeries["hr"]?.readings?.isNotEmpty() == true) data.analysisWindow(sampleTime) else null)
                intervalHrSum = 0.0
                intervalHrCount = 0
            }
        }
        activeElapsedMs = target
        return snapshots
    }
}

/** Only foreground, unpaused monotonic time is passed into the session. */
class ActiveReplayClock(private val nowMs: () -> Long) {
    private var active = false
    private var checkpointMs = nowMs()

    fun takeElapsed(): Long {
        val now = nowMs()
        val elapsed = if (active) (now - checkpointMs).coerceAtLeast(0) else 0L
        checkpointMs = now
        return elapsed
    }

    // Caller checkpoints before changing activity, preserving fractions of active seconds.
    fun setActive(value: Boolean) {
        checkpointMs = nowMs()
        active = value
    }
}
