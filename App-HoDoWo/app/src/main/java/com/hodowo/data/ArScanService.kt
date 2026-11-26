package com.hodowo.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One main control the AI saw, with its centre in the frame (0..1 from the left and top edges). */
data class ScannedControl(val label: String, val description: String, val x: Float, val y: Float)

/** What the AI saw in the frame: the device (null when there is none) and its main controls. */
data class ArScanResult(val deviceName: String?, val controls: List<ScannedControl>)

/** Names the device in a camera frame and its main controls (`POST /api/ar/scan`, AR tab). */
fun interface ArScanService {
    /** Throws [IOException] on network or server errors. */
    suspend fun scan(frameJpeg: ByteArray): ArScanResult
}

/** [ArScanService] backed by the HoDoWo backend. */
class RemoteArScanService(
    private val backendUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val language: String = Locale.getDefault().language,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ArScanService {

    override suspend fun scan(frameJpeg: ByteArray): ArScanResult {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "frame.jpg", frameJpeg.toRequestBody(JPEG))
            .addFormDataPart("language", language)
            .build()
        val token = tokenProvider()
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$PATH")
            .post(body)
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        val text = withContext(ioDispatcher) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server error ${response.code}")
                response.body?.string().orEmpty()
            }
        }
        return parseScan(text)
    }

    companion object {
        const val PATH = "/api/ar/scan"
        private val JPEG = "image/jpeg".toMediaType()
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /**
         * Reads `{"device": {...} | null, "controls": [...]}`. The name shows the brand
         * and model when known ("Washing machine · Bosch WAN28"); incomplete controls are skipped.
         */
        internal fun parseScan(text: String): ArScanResult {
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("Invalid server response", e)
            }
            val device = json.optJSONObject("device")
            val name = device?.let { deviceName(it) }
            val array = json.optJSONArray("controls")
            val controls = if (name == null || array == null) {
                emptyList()
            } else {
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val label = item.optString("label").trim()
                    if (label.isEmpty() || !item.has("x") || !item.has("y")) return@mapNotNull null
                    ScannedControl(
                        label = label,
                        description = item.optString("description").trim(),
                        x = item.optDouble("x").toFloat(),
                        y = item.optDouble("y").toFloat()
                    )
                }
            }
            return ArScanResult(name, controls)
        }

        private fun deviceName(device: JSONObject): String? {
            val name = device.textOrNull("name") ?: return null
            val details = listOf("brand", "model").mapNotNull { device.textOrNull(it) }.joinToString(" ")
            return if (details.isEmpty()) name else "$name · $details"
        }

        /** org.json gives "null" for a JSON null: treat it, and blanks, as missing. */
        private fun JSONObject.textOrNull(key: String): String? =
            optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    }
}
