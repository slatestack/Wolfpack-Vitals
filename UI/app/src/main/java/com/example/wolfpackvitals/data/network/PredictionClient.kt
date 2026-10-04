package com.example.wolfpackvitals.data.network

import com.example.wolfpackvitals.data.replay.HourAverages
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    fun readiness(onResult: (Result<AnalysisReadiness>) -> Unit): PredictionRequest
    fun send(payload: PredictionPayload, onResult: (Result<String>) -> Unit): PredictionRequest
    fun analyze(payload: DashboardPayload, onResult: (Result<DashboardResponse>) -> Unit): PredictionRequest
}

/** Asynchronous HTTP keeps replay aggregation advancing while a request is in flight. */
class OkHttpPredictionClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(130, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) : PredictionClient {
    override fun readiness(onResult: (Result<AnalysisReadiness>) -> Unit): PredictionRequest {
        val call = client.newBuilder().callTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS).build().newCall(Request.Builder().url(baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("analysis_readiness").build()).get().build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult(runCatching {
                        check(response.isSuccessful)
                        AnalysisReadiness.parse(requireNotNull(response.body).string())
                    })
                }
            }
        })
        return PredictionRequest { call.cancel() }
    }

    fun predictionUrl(payload: PredictionPayload): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("make_prediction")
        .addQueryParameter("heartbeat", payload.heartbeat)
        .addQueryParameter("glucose", payload.glucose)
        .addQueryParameter("Interbeat_interval", payload.interbeatInterval)
        .addQueryParameter("ACC", payload.acc)
        .build()

    override fun analyze(payload: DashboardPayload, onResult: (Result<DashboardResponse>) -> Unit): PredictionRequest {
        val request = Request.Builder().url(baseUrl.toHttpUrl().newBuilder().addPathSegment("make_prediction").build())
            .post(payload.json().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult(runCatching {
                        check(response.isSuccessful) { "Analysis service could not complete this request (HTTP ${response.code})." }
                        val result = DashboardResponse.parse(requireNotNull(response.body).string())
                        require(result.patientId == payload.patientId && result.sessionId == payload.sessionId &&
                            result.interval == payload.interval && result.windowId == payload.windowId) { "Analysis response does not match this window." }
                        result
                    })
                }
            }
        })
        return PredictionRequest { call.cancel() }
    }

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
