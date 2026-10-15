package com.apexforge.app.data.cloud

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AdminUser(
    val uid: String,
    val email: String?,
    val disabled: Boolean,
    val role: String,
    val createdAt: String?,
    val lastSignIn: String?,
) {
    val isAdmin: Boolean get() = role == "admin"
}

data class AdminUserPage(val users: List<AdminUser>, val nextPageToken: String?)

/**
 * Thin client for the admin Cloud Functions in backend/functions. Every call is re-checked on the server
 * (the caller's token must carry role=admin), so hiding the screen in the UI is only a convenience.
 * The functions are deployed to the default region (us-central1).
 */
@Singleton
class AdminRepository @Inject constructor() {
    private fun callable(name: String) = FirebaseFunctions.getInstance().getHttpsCallable(name)

    suspend fun listUsers(pageToken: String? = null): Result<AdminUserPage> = runCatching {
        val payload = if (pageToken == null) emptyMap<String, Any?>() else mapOf("pageToken" to pageToken)
        val data = callable("listUsers").call(payload).await().getData() as? Map<*, *> ?: error("Unexpected response.")
        val users = (data["users"] as? List<*>).orEmpty().mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val uid = m["uid"] as? String ?: return@mapNotNull null
            AdminUser(
                uid = uid,
                email = m["email"] as? String,
                disabled = m["disabled"] as? Boolean ?: false,
                role = m["role"] as? String ?: "user",
                createdAt = m["createdAt"] as? String,
                lastSignIn = m["lastSignIn"] as? String,
            )
        }
        AdminUserPage(users, data["nextPageToken"] as? String)
    }

    suspend fun setSuspended(uid: String, suspended: Boolean, reason: String = ""): Result<Unit> = runCatching {
        val payload = mapOf("uid" to uid, "suspended" to suspended, "reason" to reason)
        callable("setSuspended").call(payload).await()
        Unit
    }

    suspend fun setAdmin(uid: String, admin: Boolean): Result<Unit> = runCatching {
        val payload = mapOf("uid" to uid, "admin" to admin)
        callable("setAdminRole").call(payload).await()
        Unit
    }

    suspend fun deleteUser(uid: String): Result<Unit> = runCatching {
        val payload = mapOf("uid" to uid)
        callable("deleteUserData").call(payload).await()
        Unit
    }
}
