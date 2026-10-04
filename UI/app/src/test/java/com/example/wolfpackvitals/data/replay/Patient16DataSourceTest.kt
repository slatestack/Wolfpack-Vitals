package com.example.wolfpackvitals.data.replay

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class Patient16DataSourceTest {
    @Test fun repositoryFilesUseOnlyActualEgvRowsAndPreserveAllAxes() {
        val data = repositoryPatient16Data()
        assertEquals(499, data.heartbeat.size)
        assertEquals(488, data.glucose.size)
        assertEquals(499, data.interbeatInterval.size)
        assertEquals(499, data.acceleration.size)
        assertEquals(499, data.eda.size)
        assertEquals(0.0, data.eda.first(), 0.0)
        assertEquals("2020-07-16 09:29:03.000", data.sourceSeries.getValue("eda").readings.first().timestamp)
        assertEquals("uS", data.sourceSeries.getValue("eda").unit)
        assertTrue(data.sourceSeries.getValue("temp").readings.isEmpty())
        assertEquals(listOf(71.0, 84.0, 81.67), data.heartbeat.take(3))
        assertEquals(listOf(138.0, 134.0, 130.0), data.glucose.take(3))
        assertEquals(0.734409, data.interbeatInterval.first(), 0.0)
        assertEquals(Acceleration(-39.0, -28.0, 37.0), data.acceleration.first())
        assertEquals(113.0, data.glucose.last(), 0.0)
    }

    @Test fun metadataAlertsInsulinAndNonFiniteReadingsAreExcluded() {
        val files = mapOf(
            "HR_016.csv" to "datetime,hr\r\na,71\r\nb,NaN\r\nc,Infinity\r\nd,\r\ne,-1\r\n",
            "IBI_016.csv" to "datetime,ibi\na,0.7\n",
            "ACC_016.csv" to "datetime,acc_x,acc_y,acc_z\na,-1,2,3\nb,4,NaN,6\n",
            "Dexcom_016.csv" to "Event Type,Patient Info,Glucose Value,Insulin Value\n" +
                "FirstName,\"patient, metadata\",999,\nAlert,,200,\nInsulin,,400,20\n" +
                "EGV,,138,\nEGV,,NaN,\nEGV,,Infinity,\nEGV,,0,\n EGV ,,134,\n"
        )
        val data = CsvPatient16DataSource { StringReader(files.getValue(it)) }.load()
        assertEquals(listOf(71.0), data.heartbeat)
        assertEquals(listOf(138.0, 134.0), data.glucose)
        assertEquals(listOf(Acceleration(-1.0, 2.0, 3.0)), data.acceleration)
    }

    @Test fun sourceWindowsNeverLoopShortRecordingsOrInventTemperature() {
        val data = repositoryPatient16Data()
        val first = data.analysisWindow(REPLAY_SEND_INTERVAL_MS)
        val last = data.analysisWindow(REPLAY_HOUR_MS)
        assertEquals(300, first.sensors.getValue("hr").readings.size)
        assertEquals(499, last.sensors.getValue("hr").readings.size)
        assertEquals(first.sensors.getValue("eda"), last.sensors.getValue("eda"))
        assertEquals(first.sensors.getValue("acc"), last.sensors.getValue("acc"))
        assertTrue(last.sensors.getValue("temp").readings.isEmpty())
        assertEquals("source_local_unspecified", last.timeBasis)
        assertTrue(last.sensors.values.all { series -> series.readings.distinctBy { it.timestamp }.size == series.readings.size })
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingModalityFailsInsteadOfSubstitutingFakeReadings() {
        CsvPatient16DataSource { StringReader("datetime,hr\na,NaN\n") }.load()
    }
}

internal fun repositoryPatient16Data() = CsvPatient16DataSource { name ->
    requireNotNull(Patient16DataSourceTest::class.java.getResourceAsStream("/patient-16-data/$name"))
        .reader()
}.load()
