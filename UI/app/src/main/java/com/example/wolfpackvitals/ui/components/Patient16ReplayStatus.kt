package com.example.wolfpackvitals.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wolfpackvitals.data.network.PredictionPayload
import com.example.wolfpackvitals.data.replay.Patient16ReplayState
import com.example.wolfpackvitals.data.replay.ReplayPhase
import com.example.wolfpackvitals.ui.theme.NCStateRed
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Patient16ReplayStatus(replay: Patient16ReplayState) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (replay.phase != ReplayPhase.INACTIVE) {
            Text(
                "Active ${duration(replay.activeElapsedMs)} • ${duration(replay.remainingHourMs)} left",
                fontSize = 11.sp, color = Color(0xFF6B7280)
            )
            Text(
                "Next send ${duration(replay.untilNextTransmissionMs)} • " +
                    "Requests ${replay.transmissionsCompleted}/12 • Successful ${replay.successfulTransmissions}",
                fontSize = 11.sp, color = Color(0xFF6B7280)
            )
            replay.averages?.let { averages ->
                val values = PredictionPayload.from(averages)
                Text(
                    "Hour avg: glucose ${values.glucose} • IBI ${values.interbeatInterval}\nACC ${values.acc}",
                    fontSize = 11.sp, color = Color(0xFF6B7280)
                )
            }
            replay.lastSuccessfulSendEpochMs?.let { sentAt ->
                Text("Last successful send ${DateFormat.getTimeInstance().format(Date(sentAt))}",
                    fontSize = 11.sp, color = Color(0xFF6B7280))
            }
        }
        replay.lastApiError?.let { Text(it, fontSize = 11.sp, color = NCStateRed) }
        replay.dataError?.let { Text(it, fontSize = 11.sp, color = NCStateRed) }
    }
}

private fun duration(millis: Long): String {
    val seconds = millis / 1000
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
