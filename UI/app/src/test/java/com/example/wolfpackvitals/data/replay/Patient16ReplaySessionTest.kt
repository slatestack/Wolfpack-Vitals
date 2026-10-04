package com.example.wolfpackvitals.data.replay

import org.junit.Assert.*
import org.junit.Test

class Patient16ReplaySessionTest {
    @Test fun noImmediateRequestAndExactlyTwelveCumulativeHourSnapshots() {
        val data = repositoryPatient16Data()
        val session = Patient16ReplaySession(data)
        assertTrue(session.advanceBy(0).isEmpty())
        assertTrue(session.advanceBy(REPLAY_SEND_INTERVAL_MS - 1).isEmpty())
        val first = session.advanceBy(1).single()
        val remaining = session.advanceBy(REPLAY_HOUR_MS).toList()
        val snapshots = listOf(first) + remaining
        assertEquals(12, snapshots.size)
        assertEquals((1..12).map { it * REPLAY_SEND_INTERVAL_MS }, snapshots.map { it.activeElapsedMs })
        snapshots.forEachIndexed { index, snapshot ->
            val count = (index + 1) * 300
            assertEquals(count, snapshot.averages.sampleCountPerModality)
            assertEquals(mean(data.heartbeat, count), snapshot.averages.heartbeat, 1e-10)
            assertEquals(mean(data.glucose, count), snapshot.averages.glucose, 1e-10)
            assertEquals(mean(data.interbeatInterval, count), snapshot.averages.interbeatInterval, 1e-10)
            assertEquals(mean(data.acceleration.map { it.x }, count), snapshot.averages.acceleration.x, 1e-10)
            assertEquals(mean(data.acceleration.map { it.y }, count), snapshot.averages.acceleration.y, 1e-10)
            assertEquals(mean(data.acceleration.map { it.z }, count), snapshot.averages.acceleration.z, 1e-10)
        }
        assertEquals(REPLAY_HOUR_MS, session.activeElapsedMs)
        assertTrue(session.advanceBy(REPLAY_HOUR_MS).isEmpty())
        assertEquals(3600, session.averages!!.sampleCountPerModality)
    }

    @Test fun pauseAtThirteenMinutesRetainsPositionsSumsAndBothTimers() {
        var now = 0L
        val clock = ActiveReplayClock { now }
        val session = Patient16ReplaySession(repositoryPatient16Data())
        clock.setActive(true)
        now += 13 * 60000
        session.advanceBy(clock.takeElapsed())
        val beforePause = session.averages
        clock.setActive(false)
        now += 3 * REPLAY_HOUR_MS
        assertTrue(session.advanceBy(clock.takeElapsed()).isEmpty())
        assertEquals(beforePause, session.averages)
        assertEquals(13 * 60000L, session.activeElapsedMs)
        clock.setActive(true)
        now += 2 * 60000
        val snapshot = session.advanceBy(clock.takeElapsed()).single()
        assertEquals(15 * 60000L, snapshot.activeElapsedMs)
        assertEquals(900, snapshot.averages.sampleCountPerModality)
    }

    @Test fun partialSecondsAndIndependentDatasetWrapsArePreserved() {
        val session = Patient16ReplaySession(Patient16Data(
            listOf(10.0, 20.0), listOf(100.0, 200.0, 300.0), listOf(0.5),
            listOf(Acceleration(-10.0, 20.0, 30.0), Acceleration(-20.0, 40.0, 60.0))
        ))
        session.advanceBy(999)
        assertNull(session.averages)
        session.advanceBy(1)
        session.advanceBy(2000)
        assertEquals(3, session.averages!!.sampleCountPerModality)
        assertEquals(40.0 / 3, session.averages!!.heartbeat, 1e-10)
        assertEquals(200.0, session.averages!!.glucose, 0.0)
        assertEquals(Acceleration(-40.0 / 3, 80.0 / 3, 40.0), session.averages!!.acceleration)
    }

    private fun mean(values: List<Double>, count: Int) =
        (0 until count).sumOf { values[it % values.size] } / count
}
