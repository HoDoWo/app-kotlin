package com.hodowo.data

import android.net.Uri
import android.util.Log
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.HoDoWoAiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * [HoDoWoAiService] backed by the HoDoWo backend (FastAPI on Vercel).
 *
 * Every call goes to `POST {backendUrl}/api/chat`, which answers `{"reply": "..."}`:
 * - questions are sent as JSON: `{"message": "..."}`;
 * - photos are sent as multipart/form-data with the fields `message` and `image`.
 *
 * @param backendUrl base URL of the backend (from `backend.url` in local.properties).
 * @param loadImage reads and compresses the photo at the given [Uri] into JPEG bytes.
 */
class RemoteHoDoWoAiService(
    private val backendUrl: String,
    private val loadImage: suspend (Uri) -> ByteArray,
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : HoDoWoAiService {

    override suspend fun identifyAppliance(uri: Uri): Appliance {
        val image = loadImage(uri)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("message", IDENTIFY_PROMPT)
            .addFormDataPart("image", "appliance.jpg", image.toRequestBody(JPEG))
            .build()

        return parseAppliance(post(body))
    }

    override suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String {
        val body = JSONObject()
            .put("message", buildQuestion(question, appliance))
            .toString()
            .toRequestBody(JSON)

        return post(body)
    }

    override suspend fun sendFeedback(report: FeedbackReport) {
        // TODO(#40): the backend has no endpoint to store ratings yet.
        // Until it exists the rating is only logged, so it is not retried forever.
        Log.d(TAG, "Rating of message ${report.messageId} not stored: no backend endpoint yet")
    }

    /** Sends [body] to /api/chat and returns the "reply" field of the answer. */
    private suspend fun post(body: RequestBody): String = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}/api/chat")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException("Server error ${response.code}")
            }

            try {
                JSONObject(text).getString("reply")
            } catch (e: JSONException) {
                throw IOException("Invalid server response", e)
            }
        }
    }

    companion object {
        private const val TAG = "RemoteHoDoWoAiService"
        private const val CONNECT_TIMEOUT_SECONDS = 15L

        // The LangChain agent can take a while, so reads get a longer timeout.
        private const val READ_TIMEOUT_SECONDS = 60L

        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()

        internal const val UNKNOWN_APPLIANCE = "Unknown appliance"

        /** Asks the backend for a machine-readable identification result. */
        internal const val IDENTIFY_PROMPT =
            "Identify the household appliance in the photo. " +
                    "Answer only with a JSON object: {\"name\": string, " +
                    "\"brand\": string or null, \"model\": string or null, " +
                    "\"confidence\": number between 0 and 1}."

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /**
         * The backend does not keep a conversation, so the identified appliance
         * is added to every question as context.
         */
        internal fun buildQuestion(question: String, appliance: Appliance?): String {
            if (appliance == null) {
                return question
            }

            val details = listOfNotNull(appliance.name, appliance.brand, appliance.model)
                .joinToString(" ")

            return "Appliance: $details\nQuestion: $question"
        }

        /**
         * Reads the JSON object contained in the AI reply.
         * Any reply that is not valid JSON gives an "Unknown appliance" with 0 confidence.
         */
        internal fun parseAppliance(reply: String): Appliance {
            val start = reply.indexOf('{')
            val end = reply.lastIndexOf('}')

            if (start == -1 || end <= start) {
                return unknownAppliance()
            }

            return try {
                val json = JSONObject(reply.substring(start, end + 1))

                Appliance(
                    name = json.textOrNull("name") ?: UNKNOWN_APPLIANCE,
                    brand = json.textOrNull("brand"),
                    model = json.textOrNull("model"),
                    confidence = json.optDouble("confidence", 0.0)
                        .toFloat()
                        .takeUnless { it.isNaN() }
                        ?.coerceIn(0f, 1f)
                        ?: 0f
                )
            } catch (_: JSONException) {
                unknownAppliance()
            }
        }

        private fun unknownAppliance() = Appliance(
            name = UNKNOWN_APPLIANCE,
            brand = null,
            model = null,
            confidence = 0f
        )

        /** Returns the field as text, or null when it is missing, null or blank. */
        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}