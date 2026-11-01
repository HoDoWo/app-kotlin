package com.hodowo.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps the profile photo of each user on the phone.
 *
 * Used when Firebase Storage is not configured or the upload fails, so the
 * user can still change their photo. Every user has their own file, so after
 * signing out the guest (or another user) never sees someone else's photo.
 * The image is shown through a `file://` URL that Coil can load like any other image.
 */
class ProfilePhotoStore(
    private val context: Context,
    private val dir: File = context.filesDir
) {
    /** URL of the photo saved on this phone for [userId], or null when there is none. */
    fun currentUrl(userId: String): String? =
        fileFor(userId).takeIf { it.isFile }?.let { Uri.fromFile(it).toString() }

    /** Copies [uri] into the app's private storage for [userId] and returns its `file://` URL. */
    fun save(userId: String, uri: Uri): String? = try {
        val file = fileFor(userId)
        file.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        currentUrl(userId)
    } catch (e: Exception) {
        null
    }

    private fun fileFor(userId: String): File {
        val safeId = userId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return File(dir, "profile_photo_$safeId.jpg")
    }
}
