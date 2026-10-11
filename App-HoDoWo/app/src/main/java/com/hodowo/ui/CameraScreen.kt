package com.hodowo.ui

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Full-screen camera built with CameraX.
 *
 * Shows a live preview and a button that takes a photo, saves it in the
 * app cache directory and hands its [Uri] to the caller.
 *
 * @param onImageCaptured called with the saved photo once the capture succeeds.
 * @param onClose called when the user taps the close button.
 */
@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // View that renders the live camera preview.
    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE uses a TextureView, which works well inside Compose.
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Use case that takes the photo, tuned for the fastest capture.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .build()
    }

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }

    // Bind the preview and the capture use case to the screen lifecycle.
    LaunchedEffect(Unit) {

        val cameraProvider =
            cameraProviderFuture.get()

        val preview =
            Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }

        try {

            // Release any previous binding before binding again.
            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

        } catch (e: Exception) {

            Log.e("CameraScreen", "Could not start the camera", e)
            Toast.makeText(
                context,
                "Could not start the camera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Release the camera when the screen leaves the composition.
    DisposableEffect(Unit) {

        onDispose {

            try {

                cameraProviderFuture.get().unbindAll()

            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // Live camera preview.
        AndroidView(
            factory = {
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Close button (top-left corner, below the status bar).
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {

            Text("Close")
        }

        // Capture button.
        Button(
            onClick = {

                // The photo is saved in the app cache with a unique name.
                val file = File(
                    context.cacheDir,
                    "hodowo_${System.currentTimeMillis()}.jpg"
                )

                val outputOptions =
                    ImageCapture.OutputFileOptions
                        .Builder(file)
                        .build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object :
                        ImageCapture.OnImageSavedCallback {

                        override fun onImageSaved(
                            outputFileResults:
                            ImageCapture.OutputFileResults
                        ) {

                            // Photo saved: hand it to the caller.
                            onImageCaptured(
                                Uri.fromFile(file)
                            )
                        }

                        override fun onError(
                            exception: ImageCaptureException
                        ) {

                            Log.e("CameraScreen", "Photo capture failed", exception)
                            Toast.makeText(
                                context,
                                "Capture failed, please try again",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {

            Text("Capture and Analyze")
        }
    }
}