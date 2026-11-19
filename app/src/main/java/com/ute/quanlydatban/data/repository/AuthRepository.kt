package com.ute.quanlydatban.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.User

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val users: CollectionReference =
        FirebaseFirestore.getInstance().collection(USERS_COLLECTION)
) {
    fun signIn(email: String, password: String, onResult: (Result<String>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid
                if (userId == null) {
                    onResult(Result.failure(AuthProfileException()))
                } else {
                    readRole(userId, onResult)
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        onResult: (Result<String>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val userId = result.user?.uid
                if (userId == null) {
                    onResult(Result.failure(AuthProfileException()))
                    return@addOnSuccessListener
                }

                val profile = User(
                    id = userId,
                    name = name,
                    email = email,
                    phone = phone,
                    role = role
                )
                users.document(userId).set(profile)
                    .addOnSuccessListener { onResult(Result.success(role)) }
                    .addOnFailureListener {
                        auth.signOut()
                        onResult(Result.failure(it))
                    }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun restoreSession(onResult: (Result<String?>) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onResult(Result.success(null))
            return
        }

        users.document(userId).get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getString(FIELD_ROLE)
                if (role == ROLE_CUSTOMER || role == ROLE_OWNER) {
                    onResult(Result.success(role))
                } else {
                    auth.signOut()
                    onResult(Result.failure(AuthProfileException()))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    private fun readRole(userId: String, onResult: (Result<String>) -> Unit) {
        users.document(userId).get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getString(FIELD_ROLE)
                if (role == ROLE_CUSTOMER || role == ROLE_OWNER) {
                    onResult(Result.success(role))
                } else {
                    auth.signOut()
                    onResult(Result.failure(AuthProfileException()))
                }
            }
            .addOnFailureListener {
                if (it !is FirebaseNetworkException) {
                    auth.signOut()
                }
                onResult(Result.failure(it))
            }
    }

    private class AuthProfileException : Exception()

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val FIELD_ROLE = "role"
        private const val ROLE_CUSTOMER = "customer"
        private const val ROLE_OWNER = "owner"
    }
}
