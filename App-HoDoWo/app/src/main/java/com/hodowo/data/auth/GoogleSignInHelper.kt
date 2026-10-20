package com.hodowo.data.auth

import android.content.Context
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

/**
 * Opens the Google account picker (Credential Manager) and returns the ID
 * token that [AuthRepository.signInWithGoogle] exchanges for a Firebase user.
 *
 * [serverClientId] is the OAuth *Web* client ID of the Firebase project
 * (`google.web.client.id` in local.properties).
 */
class GoogleSignInHelper(private val serverClientId: String) {

    val isConfigured: Boolean get() = serverClientId.isNotBlank()

    /** @param activityContext must be an Activity context to show the picker. */
    suspend fun getIdToken(activityContext: Context): String {
        if (!isConfigured) throw AuthException(AuthError.GOOGLE_NOT_CONFIGURED)

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val credential = try {
            CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
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
