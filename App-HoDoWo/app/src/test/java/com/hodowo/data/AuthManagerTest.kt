package com.hodowo.data

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthManagerTest {

    private val mockUser: FirebaseUser = mockk(relaxed = true)

    private fun <T> createSuccessTask(result: T): Task<T> {
        val task = mockk<Task<T>>(relaxed = true)
        val successSlot = slot<OnSuccessListener<T>>()
        every { task.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(result)
            task
        }
        return task
    }

    @Test
    fun currentUserDelegatesToFirebaseAuth() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns mockUser
        val authManager = AuthManager(firebaseAuth)

        val user = authManager.currentUser.value

        assertNotNull(user)
    }

    @Test
    fun currentUserReturnsNullWhenLoggedOut() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val authManager = AuthManager(firebaseAuth)

        val user = authManager.currentUser.value

        assertNull(user)
    }

    @Test
    fun authStateListenerUpdatesCurrentUser() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { firebaseAuth.addAuthStateListener(capture(listenerSlot)) } answers { }
        every { firebaseAuth.currentUser } returns null

        val authManager = AuthManager(firebaseAuth)
        assertNull(authManager.currentUser.value)

        every { firebaseAuth.currentUser } returns mockUser
        listenerSlot.captured.onAuthStateChanged(firebaseAuth)

        assertEquals(mockUser, authManager.currentUser.value)
    }

    @Test
    fun signInSucceedsAndUpdatesCurrentUser() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns mockUser
        every { firebaseAuth.signInWithEmailAndPassword("test@example.com", "password") } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)
        val user = authManager.signIn(" test@example.com ", "password")

        assertEquals(mockUser, user)
        assertEquals(mockUser, authManager.currentUser.value)
    }

    @Test
    fun signInThrowsWhenUserIsNull() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns null
        every { firebaseAuth.signInWithEmailAndPassword("test@example.com", "password") } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)

        assertThrows(IllegalStateException::class.java) {
            runTest { authManager.signIn("test@example.com", "password") }
        }
    }

    @Test
    fun signUpSucceedsAndUpdatesCurrentUser() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns mockUser
        every { firebaseAuth.createUserWithEmailAndPassword("test@example.com", "password") } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)
        val user = authManager.signUp(" test@example.com ", "password")

        assertEquals(mockUser, user)
        assertEquals(mockUser, authManager.currentUser.value)
    }

    @Test
    fun signUpThrowsWhenUserIsNull() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns null
        every { firebaseAuth.createUserWithEmailAndPassword("test@example.com", "password") } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)

        assertThrows(IllegalStateException::class.java) {
            runTest { authManager.signUp("test@example.com", "password") }
        }
    }

    @Test
    fun signInAnonymouslyReturnsExistingUserIfAlreadyLoggedIn() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns mockUser

        val authManager = AuthManager(firebaseAuth)
        val user = authManager.signInAnonymously()

        assertEquals(mockUser, user)
    }

    @Test
    fun signInAnonymouslySucceedsWhenLoggedOut() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns mockUser
        every { firebaseAuth.signInAnonymously() } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)
        val user = authManager.signInAnonymously()

        assertEquals(mockUser, user)
        assertEquals(mockUser, authManager.currentUser.value)
    }

    @Test
    fun signInAnonymouslyThrowsWhenUserIsNull() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val authResult: AuthResult = mockk(relaxed = true)
        every { authResult.user } returns null
        every { firebaseAuth.signInAnonymously() } returns createSuccessTask(authResult)

        val authManager = AuthManager(firebaseAuth)

        assertThrows(IllegalStateException::class.java) {
            runTest { authManager.signInAnonymously() }
        }
    }

    @Test
    fun signOutCallsFirebaseAuthSignOutAndClearsUser() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns mockUser
        val authManager = AuthManager(firebaseAuth)

        authManager.signOut()

        verify { firebaseAuth.signOut() }
        assertNull(authManager.currentUser.value)
    }

    @Test
    fun currentUserIdReturnsUidOrAnonymous() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { mockUser.uid } returns "user-123"
        every { firebaseAuth.currentUser } returns mockUser

        val authManager = AuthManager(firebaseAuth)
        assertEquals("user-123", authManager.currentUserId)

        every { firebaseAuth.currentUser } returns null
        val loggedOutAuthManager = AuthManager(firebaseAuth)
        assertEquals("anonymous", loggedOutAuthManager.currentUserId)
    }

    @Test
    fun isAnonymousDelegatesToFirebaseUser() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { mockUser.isAnonymous } returns false
        every { firebaseAuth.currentUser } returns mockUser

        val authManager = AuthManager(firebaseAuth)
        assertFalse(authManager.isAnonymous)

        every { firebaseAuth.currentUser } returns null
        val loggedOutAuthManager = AuthManager(firebaseAuth)
        assertTrue(loggedOutAuthManager.isAnonymous)
    }

    @Test
    fun updateProfileReturnsNullWhenNoUserIsSignedIn() = runTest {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        every { firebaseAuth.currentUser } returns null
        val authManager = AuthManager(firebaseAuth)

        assertNull(authManager.updateProfile("Anna"))
    }

    @Test
    fun updateProfileSendsTrimmedNameAndRefreshesCurrentUser() = runTest {
        val request: UserProfileChangeRequest = mockk()
        mockkConstructor(UserProfileChangeRequest.Builder::class)
        try {
            val nameSlot = slot<String>()
            every {
                anyConstructed<UserProfileChangeRequest.Builder>().setDisplayName(capture(nameSlot))
            } answers { self as UserProfileChangeRequest.Builder }
            every { anyConstructed<UserProfileChangeRequest.Builder>().build() } returns request

            val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
            every { firebaseAuth.currentUser } returns mockUser
            every { mockUser.updateProfile(request) } returns createSuccessTask<Void?>(null)
            val authManager = AuthManager(firebaseAuth)

            val updated = authManager.updateProfile("  Anna  ")

            assertEquals("Anna", nameSlot.captured)
            assertEquals(mockUser, updated)
            assertEquals(mockUser, authManager.currentUser.value)
            verify { mockUser.updateProfile(request) }
        } finally {
            unmockkConstructor(UserProfileChangeRequest.Builder::class)
        }
    }
}
