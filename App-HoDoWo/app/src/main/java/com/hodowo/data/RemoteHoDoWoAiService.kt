package com.hodowo.data

import android.net.Uri
import android.util.Log
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.ControlBox
import com.hodowo.viewmodel.ControlType
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.HoDoWoAiService
import com.hodowo.viewmodel.PanelControl
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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * [HoDoWoAiService] backed by the HoDoWo backend (FastAPI on Vercel).
 *
 * Every call goes to the backend:
 * - questions and photos go to `POST {backendUrl}/api/chat`, which answers `{"reply": "..."}`;
 * - `POST {backendUrl}/api/panel/analyze` receives a panel photo as multipart/form-data
 *   and answers `{"controls": [...]}` (button and dial detection, S1-14).
 *
 * @param backendUrl base URL of the backend (from `backend.url` in local.properties).
 * @param loadImage reads and compresses the photo at the given [Uri] into JPEG bytes.
 * @param tokenProvider returns the Firebase ID token of the signed-in user, or null.
 *   The backend rejects requests without a valid token (HTTP 401).
 * @param language BCP-47 code of the app language, used for the control labels.
 */
class RemoteHoDoWoAiService(
    private val backendUrl: String,
    private val loadImage: suspend (Uri) -> ByteArray,
    private val tokenProvider: suspend () -> String? = { null },
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val language: String = Locale.getDefault().language
) : HoDoWoAiService {

    override suspend fun identifyAppliance(uri: Uri): Appliance {
        val image = loadImage(uri)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("message", IDENTIFY_PROMPT)
            .addFormDataPart("image", "appliance.jpg", image.toRequestBody(JPEG))
            .build()

        return parseAppliance(replyOf(post(CHAT_PATH, body)))
    }

    override suspend fun askQuestion(
        question: String,
        appliance: Appliance?
    ): String {
        val body = JSONObject()
            .put("message", buildQuestion(question, appliance))
            .toString()
            .toRequestBody(JSON)

        return replyOf(post(CHAT_PATH, body))
    }

    override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> {
        val image = loadImage(uri)

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "panel.jpg", image.toRequestBody(JPEG))
            .addFormDataPart("language", language)

        // The identified appliance is sent as context for the vision model.
        appliance?.brand?.let { builder.addFormDataPart("brand", it) }
        appliance?.model?.let { builder.addFormDataPart("model", it) }

        return parseControls(jsonOf(post(PANEL_PATH, builder.build())))
    }

    override suspend fun sendFeedback(report: FeedbackReport) {
        // TODO(#40): the backend has no endpoint to store ratings yet.
        // Until it exists the rating is only logged, so it is not retried forever.
        Log.d(TAG, "Rating of message ${report.messageId} not stored: no backend endpoint yet")
    }

    /** Sends [body] to [path] and returns the raw response body. */
    private suspend fun post(path: String, body: RequestBody): String {
        // Read the token before switching thread: it may need a network call.
        val token = tokenProvider()

        return withContext(ioDispatcher) {
            execute(buildRequest(path, body, token))
        }
    }

    private fun buildRequest(path: String, body: RequestBody, token: String?): Request {
        val builder = Request.Builder()
            .url("${backendUrl.trimEnd('/')}$path")
            .post(body)

        // The backend expects "Authorization: Bearer <Firebase ID token>".
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return builder.build()
    }

    /** Runs the request and returns the raw response body, mapping HTTP errors to IOException. */
    private fun execute(request: Request): String =
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (response.code == HTTP_UNAUTHORIZED) {
                throw IOException("Not authorized: please sign in again")
            }

            if (!response.isSuccessful) {
                throw IOException("Server error ${response.code}")
            }

            text
        }

    /** Reads the "reply" field of a /api/chat answer. */
    private fun replyOf(text: String): String = try {
        JSONObject(text).getString("reply")
    } catch (e: JSONException) {
        throw IOException("Invalid server response", e)
    }

    /** Parses a JSON object answer, such as the one from /api/panel/analyze. */
    private fun jsonOf(text: String): JSONObject = try {
        JSONObject(text)
    } catch (e: JSONException) {
        throw IOException("Invalid server response", e)
    }

    companion object {
        private const val TAG = "RemoteHoDoWoAiService"
        private const val HTTP_UNAUTHORIZED = 401
        private const val CONNECT_TIMEOUT_SECONDS = 15L

        // The LangChain agent can take a while, so reads get a longer timeout.
        private const val READ_TIMEOUT_SECONDS = 60L

        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()

        internal const val CHAT_PATH = "/api/chat"
        internal const val PANEL_PATH = "/api/panel/analyze"

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

        /**
         * Turns the /api/panel/analyze answer into the list of controls (S1-14).
         *
         * Entries without a label or a box are dropped, and every coordinate is
         * clamped to 0..1 so the overlay (S1-15) always gets usable boxes.
         */
        internal fun parseControls(payload: JSONObject): List<PanelControl> {
            val array = payload.optJSONArray("controls") ?: return emptyList()
            val controls = ArrayList<PanelControl>(array.length())

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label").trim()
                val box = item.optJSONObject("box") ?: continue
                if (label.isEmpty()) continue

                controls.add(
                    PanelControl(
                        id = item.optString("id").ifBlank { "c${index + 1}" },
                        label = label,
                        type = ControlType.fromApi(item.optString("type")),
                        description = item.optString("description").trim(),
                        box = ControlBox(
                            x = box.unitValue("x"),
                            y = box.unitValue("y"),
                            w = box.unitValue("w"),
                            h = box.unitValue("h")
                        )
                    )
                )
            }

            return controls
        }

        /** Reads a coordinate and clamps it to 0..1 (NaN becomes 0). */
        private fun JSONObject.unitValue(key: String): Float {
            val value = optDouble(key, 0.0).toFloat()
            return if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
        }
    }
}