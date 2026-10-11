package com.hodowo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hodowo.ui.theme.HoDoWoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SkeletonViewModel : ViewModel() {
    private val client = OkHttpClient()

    var reply by mutableStateOf("")
        private set

    fun send(message: String) {
        if (message.isBlank()) {
            reply = "Escribe un mensaje."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            reply = try {
                if (BuildConfig.BACKEND_URL.isBlank()) {
                    "Error: configura backend.url en local.properties"
                } else {
                    val body = JSONObject()
                        .put("message", message)
                        .toString()
                        .toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("${BuildConfig.BACKEND_URL.trimEnd('/')}/api/chat")
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            "Error ${response.code}: $responseBody"
                        } else {
                            JSONObject(responseBody).getString("reply")
                        }
                    }
                }
            } catch (exception: Exception) {
                "Error: ${exception.message ?: "No se pudo enviar el mensaje"}"
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HoDoWoTheme {
                val skeletonViewModel: SkeletonViewModel = viewModel()
                SkeletonScreen(skeletonViewModel)
            }
        }
    }
}

@Composable
fun SkeletonScreen(viewModel: SkeletonViewModel, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mensaje") }
            )
            Button(onClick = { viewModel.send(message) }) {
                Text("Enviar")
            }
            Text(text = viewModel.reply)
        }
    }
}
