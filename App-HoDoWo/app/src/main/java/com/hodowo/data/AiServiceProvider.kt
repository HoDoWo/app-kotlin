package com.hodowo.data

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.BuildConfig
import com.hodowo.viewmodel.ChatViewModel
import com.hodowo.viewmodel.DemoHoDoWoAiService
import com.hodowo.viewmodel.HoDoWoAiService

/**
 * Chooses which AI service the app uses and provides singletons for Auth and Firebase.
 */
object AiServiceProvider {

    val authManager by lazy { AuthManager() }
    val firebaseRepository by lazy { FirebaseRepository() }

    fun create(context: Context): HoDoWoAiService {
        //val backendUrl = BuildConfig.BACKEND_URL
        val backendUrl = "http://localhost:8000"

        if (backendUrl.isBlank()) {
            return DemoHoDoWoAiService()
        }

        val compressor = ImageCompressor(context.applicationContext)
        val tokens = FirebaseTokenProvider()

        return RemoteHoDoWoAiService(
            backendUrl = backendUrl,
            loadImage = { uri -> compressor.compress(uri) },
            tokenProvider = { tokens.token() }
        )
    }

    /** Factory that builds [ChatViewModel] with the service and Firebase repos. */
    fun chatViewModelFactory(context: Context): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ChatViewModel(
                    aiService = create(context),
                    firebaseRepository = firebaseRepository,
                    authManager = authManager
                )
            }
        }
}