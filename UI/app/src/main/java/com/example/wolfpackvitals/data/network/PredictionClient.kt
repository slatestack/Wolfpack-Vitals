package com.example.wolfpackvitals.data.network

import com.example.wolfpackvitals.data.replay.HourAverages
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PredictionPayload(
    val heartbeat: String,
    val glucose: String,
    val interbeatInterval: String,
    val acc: String
) {
    companion object {
        fun from(averages: HourAverages) = PredictionPayload(
            String.format(Locale.US, "%.2f", averages.heartbeat),
            String.format(Locale.US, "%.2f", averages.glucose),
            String.format(Locale.US, "%.4f", averages.interbeatInterval),
            String.format(Locale.US, "x=%.2f,y=%.2f,z=%.2f",
                averages.acceleration.x, averages.acceleration.y, averages.acceleration.z)
        )
    }
}

fun interface PredictionRequest { fun cancel() }

interface PredictionClient {
    fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest
}

/** Asynchronous HTTP keeps replay aggregation advancing while a request is in flight. */
class OkHttpPredictionClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) : PredictionClient {
    fun predictionUrl(payload: PredictionPayload): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("make_prediction")
        .addQueryParameter("heartbeat", payload.heartbeat)
        .addQueryParameter("glucose", payload.glucose)
        .addQueryParameter("Interbeat_interval", payload.interbeatInterval)
        .addQueryParameter("ACC", payload.acc)
        .build()

    override fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest {
        val call = client.newCall(Request.Builder().url(predictionUrl(payload)).get().build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(Result.failure(e))

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        check(response.isSuccessful) { "Prediction API returned HTTP ${response.code}" }
                        response.body?.string().orEmpty()
                    }
                    onResult(result)
                }
            }
        })
        return PredictionRequest { call.cancel() }
    }
}
