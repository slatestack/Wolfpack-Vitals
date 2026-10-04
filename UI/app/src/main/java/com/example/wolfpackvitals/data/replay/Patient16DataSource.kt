package com.example.wolfpackvitals.data.replay

import java.io.Reader

data class Acceleration(val x: Double, val y: Double, val z: Double)

data class Patient16Data(
    val heartbeat: List<Double>,
    val glucose: List<Double>,
    val interbeatInterval: List<Double>,
    val acceleration: List<Acceleration>
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

/** Reads each modality independently. Timestamps are deliberately never joined. */
class CsvPatient16DataSource(private val open: (String) -> Reader) : Patient16DataSource {
    override fun load() = Patient16Data(
        heartbeat = numericColumn("HR_016.csv", "hr"),
        glucose = numericColumn("Dexcom_016.csv", "Glucose Value", egvOnly = true),
        interbeatInterval = numericColumn("IBI_016.csv", "ibi"),
        acceleration = rows("ACC_016.csv", listOf("acc_x", "acc_y", "acc_z"))
            .mapNotNull { row ->
                val x = finiteNumber(row["acc_x"])
                val y = finiteNumber(row["acc_y"])
                val z = finiteNumber(row["acc_z"])
                if (x != null && y != null && z != null) Acceleration(x, y, z) else null
            }
    )

    private fun numericColumn(file: String, column: String, egvOnly: Boolean = false): List<Double> =
        rows(file, if (egvOnly) listOf(column, "Event Type") else listOf(column))
            .filter { !egvOnly || it["Event Type"] == "EGV" }
            .mapNotNull { finiteNumber(it[column]) }
            .filter { it > 0.0 }

    private fun rows(file: String, required: List<String>): List<Map<String, String>> =
        open(file).buffered().use { reader ->
            val header = csvFields(requireNotNull(reader.readLine()) { "$file is empty" })
                .map { it.trim().removePrefix("\uFEFF") }
            require(header.containsAll(required)) { "$file is missing columns: $required" }
            reader.lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = csvFields(line)
                header.mapIndexed { index, name -> name to fields.getOrElse(index) { "" }.trim() }
                    .toMap()
            }.toList()
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
