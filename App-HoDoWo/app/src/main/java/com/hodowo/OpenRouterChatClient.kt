package com.hodowo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenRouterChatClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val backendUrl: String = BuildConfig.BACKEND_URL,
) : ChatClient {
    override suspend fun send(message: String): String {
        if (backendUrl.isBlank()) {
            return "Error: configure backend.url in local.properties"
        }

        val body = JSONObject()
            .put("message", message)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${backendUrl.trimEnd('/')}/api/chat")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                "Error ${response.code}: $responseBody"
            } else {
                JSONObject(responseBody).getString("reply")
            }
        }
    }
}
