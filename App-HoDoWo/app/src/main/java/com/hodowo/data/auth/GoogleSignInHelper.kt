package com.hodowo.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Thrown when the user closes the Google account picker. Not an error. */
class GoogleSignInCancelledException : Exception()

/** Walks up the [ContextWrapper] chain until the Activity that can show the picker. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Opens the Google account picker (Credential Manager) and returns the ID
 * token that [AuthRepository.signInWithGoogle] exchanges for a Firebase user.
 *
 * [serverClientId] is the OAuth *Web* client ID of the Firebase project
 * (`google.web.client.id` in local.properties, or the `client_type: 3` entry
 * of google-services.json). Google also checks the SHA-1 of the signing
 * certificate, so the provider has to be enabled in the Firebase console.
 */
class GoogleSignInHelper(private val serverClientId: String) {

    val isConfigured: Boolean get() = serverClientId.isNotBlank()

    /**
     * @param context any context of the Activity that shows the picker; the
     * Activity is found through its [ContextWrapper] chain, because Credential
     * Manager refuses a plain application context.
     */
    suspend fun getIdToken(context: Context): String {
        if (!isConfigured) throw AuthException(AuthError.GOOGLE_NOT_CONFIGURED)

        val activity = context.findActivity()
            ?: throw AuthException(AuthError.GOOGLE_FAILED)

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val credential = try {
            CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInCancelledException()
        } catch (error: NoCredentialException) {
            throw AuthException(AuthError.GOOGLE_NO_ACCOUNT, error)
        } catch (error: GetCredentialException) {
            throw AuthException(AuthError.GOOGLE_FAILED, error)
        }

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                return GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (error: GoogleIdTokenParsingException) {
                throw AuthException(AuthError.GOOGLE_FAILED, error)
            }
        }
        throw AuthException(AuthError.GOOGLE_FAILED)
    }
}
