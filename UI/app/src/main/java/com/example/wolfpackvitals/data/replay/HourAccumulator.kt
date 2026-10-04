package com.example.wolfpackvitals.data.replay

/** Arithmetic sums retained for the entire hour; snapshots leave all sums untouched. */
internal class HourAccumulator {
    var sampleCount = 0
        private set
    private var heartbeatSum = 0.0
    private var glucoseSum = 0.0
    private var ibiSum = 0.0
    private var xSum = 0.0
    private var ySum = 0.0
    private var zSum = 0.0

    fun add(heartbeat: Double, glucose: Double, ibi: Double, acc: Acceleration) {
        heartbeatSum += heartbeat
        glucoseSum += glucose
        ibiSum += ibi
        xSum += acc.x
        ySum += acc.y
        zSum += acc.z
        sampleCount++
    }

    val averages: HourAverages? get() = if (sampleCount == 0) null else HourAverages(
        heartbeatSum / sampleCount, glucoseSum / sampleCount, ibiSum / sampleCount,
        Acceleration(xSum / sampleCount, ySum / sampleCount, zSum / sampleCount), sampleCount
    )
}
