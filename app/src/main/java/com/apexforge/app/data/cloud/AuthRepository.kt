package com.apexforge.app.data.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    /** No google-services.json was added at build time, so every cloud feature is switched off. */
    data object NotConfigured : AuthState
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String, val role: String) : AuthState {
        val isAdmin: Boolean get() = role == "admin"
    }
}

/**
 * Email/password accounts. The role is read from the ID token's custom claims, which only the Cloud
 * Functions can set. The UI uses it to decide what to show, but the real enforcement is server-side
 * (Firestore rules and the callable functions).
 */
@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {
    val isConfigured: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AuthState>(if (isConfigured) AuthState.Loading else AuthState.NotConfigured)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        if (isConfigured) {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                scope.launch { publish(auth.currentUser, forceRefresh = false) }
            }
        }
    }

    private suspend fun publish(user: FirebaseUser?, forceRefresh: Boolean) {
        if (user == null) {
            _state.value = AuthState.SignedOut
            return
        }
        val role = runCatching { user.getIdToken(forceRefresh).await().claims["role"] as? String }.getOrNull() ?: "user"
        _state.value = AuthState.SignedIn(user.uid, user.email.orEmpty(), role)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> = runCatching {
        val result = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Account was created but no user was returned.")
        // A profile document is a convenience; failing to write it must not undo the sign-up.
        runCatching {
            FirebaseFirestore.getInstance().document("users/${user.uid}").set(
                mapOf(
                    "email" to email.trim(),
                    "displayName" to displayName.trim().take(40),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        Unit
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    fun signOut() {
        if (isConfigured) FirebaseAuth.getInstance().signOut()
    }

    /** Re-reads the token so a role change made by an admin takes effect without signing out. */
    suspend fun refreshClaims() {
        if (isConfigured) publish(FirebaseAuth.getInstance().currentUser, forceRefresh = true)
    }

    /** Deletes the cloud data and the account itself through the server, then signs out locally. */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        FirebaseFunctions.getInstance().getHttpsCallable("deleteUserData").call(emptyMap<String, Any>()).await()
        FirebaseAuth.getInstance().signOut()
    }
}
