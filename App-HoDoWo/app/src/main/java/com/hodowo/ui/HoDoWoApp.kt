package com.hodowo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hodowo.data.AiServiceProvider
import com.hodowo.viewmodel.ChatViewModel

/**
 * Root composable of the app: switches between the chat and the camera.
 *
 * It also takes care of the CAMERA runtime permission: the camera opens only
 * when the permission is granted, and it is requested the first time the
 * user taps the camera button.
 *
 * Usage (in MainActivity): setContent { HoDoWoTheme { HoDoWoApp() } }
 */
@Composable
fun HoDoWoApp(
    // The service (real backend or demo) is chosen by AiServiceProvider.
    chatViewModel: ChatViewModel = viewModel(
        factory = AiServiceProvider.chatViewModelFactory(
            LocalContext.current.applicationContext
        )
    )
) {

    val context = LocalContext.current

    val state by chatViewModel.uiState.collectAsState()

    // True while the camera is shown instead of the chat.
    var showCamera by remember {
        mutableStateOf(false)
    }

    // True while the control panel overlay (S1-15) is shown.
    var showOverlay by remember {
        mutableStateOf(false)
    }

    // Asks the CAMERA permission and opens the camera if the user accepts.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        if (granted) {

            showCamera = true

        } else {

            Toast.makeText(
                context,
                "Camera permission denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Opens the camera right away if the permission is already granted.
    fun openCamera() {

        val hasPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {

            showCamera = true

        } else {

            permissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    if (showOverlay) {

        // The system back button closes the overlay instead of the app.
        BackHandler {
            showOverlay = false
        }

        PanelOverlayScreen(
            imageUri = state.panelImageUri,
            controls = state.panelControls,
            isLoading = state.isAnalyzing,
            errorMessage = state.error,
            onClose = {
                showOverlay = false
            }
        )

    } else if (showCamera) {

        // The system back button closes the camera instead of the app.
        BackHandler {
            showCamera = false
        }

        CameraScreen(
            onImageCaptured = { uri ->

                // Back to the chat and send the photo for analysis.
                showCamera = false

                chatViewModel.analyzeImage(uri)
            },
            onClose = {
                showCamera = false
            }
        )

    } else {

        ChatScreen(
            viewModel = chatViewModel,
            onOpenCamera = ::openCamera,
            onDetectPanel = {

                // Detect the controls and show the overlay only if a photo exists.
                if (chatViewModel.analyzePanel()) {
                    showOverlay = true
                }
            }
        )
    }
}