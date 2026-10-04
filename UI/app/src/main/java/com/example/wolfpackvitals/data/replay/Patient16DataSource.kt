package com.example.wolfpackvitals.data.replay

import java.io.Reader

data class Acceleration(val x: Double, val y: Double, val z: Double)

data class Patient16Data(
    val heartbeat: List<Double>,
    val glucose: List<Double>,
    val interbeatInterval: List<Double>,
    val acceleration: List<Acceleration>,
    val eda: List<Double> = emptyList(),
    val sourceSeries: Map<String, SourceSeries> = emptyMap(),
    val meals: List<SourceMeal> = emptyList()
) {
    init {
        require(heartbeat.isNotEmpty() && glucose.isNotEmpty() &&
            interbeatInterval.isNotEmpty() && acceleration.isNotEmpty()) {
            "Patient 16 requires valid rows in all four CSV files"
        }
    }
}

fun interface Patient16DataSource {
    fun load(): Patient16Data
}

/** Keeps original timestamps alongside legacy replay vectors. Inference uses sourceSeries only. */
class CsvPatient16DataSource(private val open: (String) -> Reader) : Patient16DataSource {
    override fun load(): Patient16Data {
        val hr = scalar("HR_016.csv", "hr", "bpm")
        val glucoseRows = rows("Dexcom_016.csv", listOf("Glucose Value", "Event Type"))
        val glucose = scalarRows(glucoseRows.filter { it["Event Type"] == "EGV" }, "Glucose Value", "mg/dL", "Timestamp")
        val ibi = scalar("IBI_016.csv", "ibi", "s")
        val eda = scalar("EDA_016.csv", "eda", "uS", allowZero = true, optional = true)
        val temp = scalar("TEMP_016.csv", "temp", "degC", allowZero = true, optional = true)
        val accRows = rows("ACC_016.csv", listOf("acc_x", "acc_y", "acc_z"))
        val acc = accRows.mapNotNull { row ->
            val values = listOf("acc_x", "acc_y", "acc_z").map { finiteNumber(row[it]) }
            if (values.any { it == null }) null else SourceReading(row["datetime"].orEmpty(), values.filterNotNull())
        }
        fun timestamped(unit: String, readings: List<SourceReading>) = SourceSeries(unit,
            readings.filter { sourceTime(it.timestamp) != null }.sortedBy { sourceTime(it.timestamp) }.distinctBy { sourceTime(it.timestamp) })
        return Patient16Data(
            hr.readings.map { it.values.single() }, glucose.readings.map { it.values.single() },
            ibi.readings.map { it.values.single() }, acc.map { Acceleration(it.values[0], it.values[1], it.values[2]) },
            eda.readings.map { it.values.single() },
            mapOf("hr" to timestamped(hr.unit, hr.readings), "glucose" to timestamped(glucose.unit, glucose.readings),
                "ibi" to timestamped(ibi.unit, ibi.readings), "eda" to timestamped(eda.unit, eda.readings),
                "temp" to timestamped(temp.unit, temp.readings), "acc" to timestamped("device_counts", acc)),
            glucoseRows.filter { it["Event Type"] in listOf("Carbs", "Meal") }.mapNotNull { row ->
                row["Timestamp"]?.takeIf { sourceTime(it) != null }?.let { SourceMeal(it, finiteNumber(row["Carb Value"])?.takeIf { v -> v >= 0 }) }
            }
        )
    }

    private fun scalar(file: String, column: String, unit: String, allowZero: Boolean = false, optional: Boolean = false) =
        scalarRows(rows(file, listOf(column), optional), column, unit, "datetime", allowZero)

    private fun scalarRows(rows: List<Map<String, String>>, column: String, unit: String,
                           timestamp: String, allowZero: Boolean = false) = SourceSeries(unit, rows.mapNotNull { row ->
        finiteNumber(row[column])?.takeIf { if (allowZero) it >= 0 else it > 0 }?.let {
            SourceReading(row[timestamp].orEmpty(), listOf(it))
        }
    })

    private fun rows(file: String, required: List<String>, optional: Boolean = false): List<Map<String, String>> {
        val source = try { open(file) } catch (error: Exception) {
            if (optional && (error is java.io.IOException || error is NoSuchElementException || error is IllegalArgumentException)) return emptyList()
            throw error
        }
        return source.buffered().use { reader ->
            val header = csvFields(requireNotNull(reader.readLine()) { "$file is empty" })
                .map { it.trim().removePrefix("\uFEFF") }
            require(header.containsAll(required)) { "$file is missing columns: $required" }
            reader.lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = csvFields(line)
                header.mapIndexed { index, name -> name to fields.getOrElse(index) { "" }.trim() }.toMap()
            }.toList()
        }
    }

    private fun finiteNumber(value: String?): Double? = value?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // Handles quoted commas, empty fields, escaped quotes, and CRLF in these CSV exports.
    private fun csvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    fields += field.toString()
                    field.setLength(0)
                }
                else -> field.append(character)
            }
            index++
        }
        fields += field.toString()
        return fields
    }
}
