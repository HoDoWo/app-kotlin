package com.hodowo.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps the profile photo on the phone.
 *
 * Used when Firebase Storage is not configured or the upload fails, so the
 * user can still change their photo. The image is shown through a `file://`
 * URL that Coil can load like any other image.
 */
class ProfilePhotoStore(
    private val context: Context,
    private val file: File = File(context.filesDir, "profile_photo.jpg")
) {
    /** URL of the photo saved on this phone, or null when there is none. */
    fun currentUrl(): String? = file.takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }

    /** Copies [uri] into the app's private storage and returns its `file://` URL. */
    fun save(uri: Uri): String? = try {
        file.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        currentUrl()
    } catch (e: Exception) {
        null
    }
}
