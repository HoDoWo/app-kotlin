package com.hodowo.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthManagerTest {

    private val mockUser: FirebaseUser = mockk(relaxed = true)

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
    fun signOutCallsFirebaseAuthSignOut() {
        val firebaseAuth: FirebaseAuth = mockk(relaxed = true)
        val authManager = AuthManager(firebaseAuth)
        authManager.signOut()

        verify { firebaseAuth.signOut() }
    }
}