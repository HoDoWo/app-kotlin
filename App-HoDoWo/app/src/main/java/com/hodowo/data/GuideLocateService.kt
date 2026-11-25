package com.hodowo.data

import com.hodowo.viewmodel.ManualStep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the AI found a step's control in the camera frame, 0..1 from its left and top edges. */
data class LocatedPoint(val step: Int, val x: Float, val y: Float, val confidence: Float)

/** One AR auto-alignment request: the upright camera frame and the guide. */
class GuideLocateRequest(
    val frameJpeg: ByteArray,
    val steps: List<ManualStep>,
    /** Step numbers to find. */
    val targets: List<Int>,
    /** The guide photo the pins are on; helps the AI match the same control. */
    val referenceJpeg: ByteArray? = null,
    val appliance: String? = null
)

/** Finds guide controls in a live camera frame (`POST /api/guide/locate`, AR auto-alignment). */
fun interface GuideLocateService {
    /** The points found (possibly none); throws [IOException] on network or server errors. */
    suspend fun locate(request: GuideLocateRequest): List<LocatedPoint>
}

/** [GuideLocateService] backed by the HoDoWo backend. */
class RemoteGuideLocateService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GuideLocateService {

    override suspend fun locate(request: GuideLocateRequest): List<LocatedPoint> {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "frame.jpg", request.frameJpeg.toRequestBody(JPEG))
            .addFormDataPart("steps", stepsJson(request.steps))
            .addFormDataPart("targets", request.targets.joinToString(","))
        request.referenceJpeg?.let { builder.addFormDataPart("reference", "photo.jpg", it.toRequestBody(JPEG)) }
        request.appliance?.takeIf { it.isNotBlank() }?.let { builder.addFormDataPart("appliance", it) }

        val token = tokenProvider()
        val httpRequest = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$PATH")
            .post(builder.build())
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

        val text = withContext(ioDispatcher) {
            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server error ${response.code}")
                response.body?.string().orEmpty()
            }
        }
        return parsePoints(text)
    }

    companion object {
        const val PATH = "/api/guide/locate"
        private val JPEG = "image/jpeg".toMediaType()
        private const val CONNECT_TIMEOUT_S = 15L

        /** The vision model can take a while on two images. */
        private const val READ_TIMEOUT_S = 60L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /** The `steps` field: number, title, description and, when the step has one, its pin on the photo. */
        internal fun stepsJson(steps: List<ManualStep>): String {
            val array = JSONArray()
            steps.forEach { step ->
                val item = JSONObject()
                    .put("step", step.number)
                    .put("title", step.title)
                    .put("description", step.description)
                step.pin?.let { item.put("pin_x", it.x.toDouble()).put("pin_y", it.y.toDouble()) }
                array.put(item)
            }
            return array.toString()
        }

        /** Reads `{"points": [...]}`; entries without a step or coordinates are skipped. */
        internal fun parsePoints(text: String): List<LocatedPoint> {
            val array = try {
                JSONObject(text).optJSONArray("points") ?: return emptyList()
            } catch (e: JSONException) {
                throw IOException("Invalid server response", e)
            }
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                if (!item.has("step") || !item.has("x") || !item.has("y")) return@mapNotNull null
                LocatedPoint(
                    step = item.optInt("step"),
                    x = item.optDouble("x").toFloat(),
                    y = item.optDouble("y").toFloat(),
                    confidence = item.optDouble("confidence", 1.0).toFloat()
                )
            }
        }
    }
}
