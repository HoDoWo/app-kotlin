package com.hodowo.data

import android.net.Uri
import android.util.Log
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.ControlBox
import com.hodowo.viewmodel.ControlType
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.HoDoWoAiService
import com.hodowo.viewmodel.PanelBox
import com.hodowo.viewmodel.PanelControl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
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
 *   and answers `{"controls": [...]}` (button and dial detection, S1-14);
 * - `POST {backendUrl}/api/panel/locate` receives the full photo and answers
 *   `{"box": {...}}` or `{"box": null}` (panel location, S1-16).
 *
 * Questions also carry the identified appliance as structured data
 * (`"appliance": {"name", "brand", "model"}`): the backend uses it to add the
 * rated guides of the same appliance to the prompt (#40).
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
    internal val tokenProvider: suspend () -> String? = { null },
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
        val json = JSONObject()
            .put("message", buildQuestion(question, appliance))
        applianceJson(appliance)?.let { json.put("appliance", it) }

        return replyOf(post(CHAT_PATH, json.toString().toRequestBody(JSON)))
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

    /**
     * Locates the control panel inside the full photo (S1-16).
     *
     * The caller crops the photo with the returned box, so the detection only
     * sees the panel. A photo without a panel gives null.
     */
    override suspend fun locatePanel(uri: Uri): PanelBox? {
        val image = loadImage(uri)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg", image.toRequestBody(JPEG))
            .build()

        return parsePanelBox(jsonOf(post(LOCATE_PATH, body)))
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

    override fun streamAnswerEvents(
        question: String,
        appliance: Appliance?,
        imageUri: Uri?,
        history: List<com.hodowo.viewmodel.ChatMessageHistory>
    ): kotlinx.coroutines.flow.Flow<com.hodowo.viewmodel.ChatStreamEvent> = kotlinx.coroutines.flow.flow {
        val imageBytes = imageUri?.let { runCatching { loadImage(it) }.getOrNull() }
        val token = tokenProvider()

        val request = try {
            val builder = Request.Builder()
                .url("${backendUrl.trimEnd('/')}$STREAM_CHAT_PATH")

            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            if (imageBytes != null) {
                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("message", buildQuestion(question, appliance))
                    .addFormDataPart("image", "appliance.jpg", imageBytes.toRequestBody(JPEG))

                applianceJson(appliance)?.let { bodyBuilder.addFormDataPart("appliance", it.toString()) }

                if (history.isNotEmpty()) {
                    val historyArray = org.json.JSONArray()
                    for (item in history) {
                        historyArray.put(JSONObject().put("role", item.role).put("content", item.content))
                    }
                    bodyBuilder.addFormDataPart("history", historyArray.toString())
                }

                builder.post(bodyBuilder.build())
            } else {
                val jsonBody = JSONObject()
                    .put("message", buildQuestion(question, appliance))
                applianceJson(appliance)?.let { jsonBody.put("appliance", it) }

                if (history.isNotEmpty()) {
                    val historyArray = org.json.JSONArray()
                    for (item in history) {
                        historyArray.put(JSONObject().put("role", item.role).put("content", item.content))
                    }
                    jsonBody.put("history", historyArray)
                }

                builder.post(jsonBody.toString().toRequestBody(JSON))
            }
            builder.build()
        } catch (e: Exception) {
            null
        }

        if (request == null) {
            val ans = askQuestion(question, appliance)
            emit(com.hodowo.viewmodel.ChatStreamEvent.Token(ans))
            emit(com.hodowo.viewmodel.ChatStreamEvent.Response(ans))
            return@flow
        }

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server error ${response.code}")
                }
                val source = response.body?.source() ?: throw IOException("Empty response")
                var currentEvent: String? = null

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("event:")) {
                        currentEvent = line.removePrefix("event:").trim()
                    } else if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        when (currentEvent) {
                            "step" -> {
                                val json = JSONObject(data)
                                val title = json.optString("title", "Processing...")
                                val detail = json.optString("detail", "")
                                emit(com.hodowo.viewmodel.ChatStreamEvent.Step(title, detail))
                            }
                            "token" -> {
                                val json = JSONObject(data)
                                val tokenText = json.optString("token", "")
                                emit(com.hodowo.viewmodel.ChatStreamEvent.Token(tokenText))
                            }
                            "response" -> {
                                val json = JSONObject(data)
                                val reply = json.optString("reply", "")
                                val imageUrl = json.optString("image").takeIf { it.isNotBlank() }
                                val isManual = json.optBoolean("is_manual", false)
                                val manualId = json.optString("manual_id").takeIf { it.isNotBlank() }

                                val manualGuide = if (isManual) {
                                    val stepsArray = json.optJSONArray("steps")
                                    val steps = mutableListOf<com.hodowo.viewmodel.ManualStep>()
                                    if (stepsArray != null) {
                                        for (i in 0 until stepsArray.length()) {
                                            val s = stepsArray.optJSONObject(i)
                                            if (s != null) {
                                                steps.add(
                                                    com.hodowo.viewmodel.ManualStep(
                                                        number = s.optInt("number", i + 1),
                                                        title = s.optString("title", "Step ${i + 1}"),
                                                        description = s.optString("description", "")
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    com.hodowo.viewmodel.ManualGuide(
                                        applianceName = json.optString("appliance_name", appliance?.name ?: "Appliance"),
                                        issueTitle = json.optString("issue_title", "Repair Guide"),
                                        overview = json.optString("overview", reply),
                                        steps = steps,
                                        imageUrl = imageUrl,
                                        manualId = manualId
                                    )
                                } else null

                                emit(com.hodowo.viewmodel.ChatStreamEvent.Response(reply, manualGuide, imageUrl))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Stream failed: ${e.localizedMessage}", e)
        }
    }.flowOn(ioDispatcher)

    override suspend fun setBookmark(manualId: String, isBookmarked: Boolean): Boolean {
        val token = tokenProvider()
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}/api/manuals/$manualId/bookmark")
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .patch(
                JSONObject().put("is_bookmarked", isBookmarked).toString().toRequestBody(JSON)
            )
            .build()

        return withContext(ioDispatcher) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            JSONObject(body).optBoolean("is_bookmarked", isBookmarked)
                        } else isBookmarked
                    } else {
                        isBookmarked
                    }
                }
            } catch (e: Exception) {
                isBookmarked
            }
        }
    }

    override suspend fun rateManual(manualId: String, rating: Int): Int {
        val token = tokenProvider()
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}/api/manuals/$manualId/rate")
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .post(
                JSONObject().put("rating", rating).toString().toRequestBody(JSON)
            )
            .build()

        return withContext(ioDispatcher) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            JSONObject(body).optInt("current_rating", rating)
                        } else rating
                    } else {
                        rating
                    }
                }
            } catch (e: Exception) {
                rating
            }
        }
    }

    companion object {
        private const val TAG = "RemoteHoDoWoAiService"
        private const val HTTP_UNAUTHORIZED = 401
        private const val CONNECT_TIMEOUT_SECONDS = 15L

        // The LangChain agent can take a while, so reads get a longer timeout.
        private const val READ_TIMEOUT_SECONDS = 560L

        private val JSON = "application/json".toMediaType()
        private val JPEG = "image/jpeg".toMediaType()

        internal const val CHAT_PATH = "/api/chat"
        internal const val STREAM_CHAT_PATH = "/api/chat/stream"
        internal const val FEEDBACK_PATH = "/api/feedback"
        internal const val PANEL_PATH = "/api/panel/analyze"
        internal const val LOCATE_PATH = "/api/panel/locate"

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
         * The appliance as JSON: {"name", "brand", "model"} (null fields when unknown), for #40.
         * Returns null when there is no appliance or it was not recognised, so the
         * backend does not group the guides of unrelated appliances.
         */
        internal fun applianceJson(appliance: Appliance?): JSONObject? {
            if (appliance == null || appliance.name == UNKNOWN_APPLIANCE) {
                return null
            }

            return JSONObject()
                .put("name", appliance.name)
                .put("brand", appliance.brand ?: JSONObject.NULL)
                .put("model", appliance.model ?: JSONObject.NULL)
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

        /**
         * Turns the /api/panel/locate answer into the panel box (S1-16).
         *
         * Returns null when the backend found no panel, or sent no usable box,
         * so the caller then keeps the full photo.
         */
        internal fun parsePanelBox(payload: JSONObject): PanelBox? {
            val box = payload.optJSONObject("box") ?: return null

            val width = box.unitValue("w")
            val height = box.unitValue("h")
            if (width <= 0f || height <= 0f) {
                return null
            }

            return PanelBox(
                x = box.unitValue("x"),
                y = box.unitValue("y"),
                w = width,
                h = height
            )
        }
    }
}