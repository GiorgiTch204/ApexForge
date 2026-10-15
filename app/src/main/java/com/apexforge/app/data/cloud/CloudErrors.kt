package com.apexforge.app.data.cloud

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException

/** Turns Firebase exceptions into short messages a user can act on. */
fun friendlyCloudError(e: Throwable): String = when (e) {
    is FirebaseNetworkException -> "No internet connection. Check your network and try again."
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Wrong email or password."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try signing in."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password (at least 8 characters)."
        "ERROR_USER_DISABLED" -> "This account has been suspended. Contact the administrator."
        else -> "Sign-in failed (${e.errorCode})."
    }
    is FirebaseFunctionsException -> when (e.code) {
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> "You don't have permission to do that."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
        FirebaseFunctionsException.Code.NOT_FOUND -> "The server function isn't deployed yet. See backend/SETUP_FIREBASE.md."
        FirebaseFunctionsException.Code.FAILED_PRECONDITION, FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
            e.message ?: "That action isn't allowed."
        else -> "Server error (${e.code}). Try again later."
    }
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "The server refused the request. Your account may be suspended, or the security rules aren't deployed."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Can't reach the server right now. Try again later."
        else -> "Cloud error (${e.code})."
    }
    else -> e.message ?: "Something went wrong."
}
