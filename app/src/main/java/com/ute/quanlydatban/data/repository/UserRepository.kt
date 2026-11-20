package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.User

data class UserStats(
    val bookingCount: Long = 0,
    val favoriteCount: Long = 0,
    val reviewCount: Long = 0
)

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<User>(firestore.collection("users"), User::class.java) {

    companion object {
        private const val TAG = "UserRepository"
    }

    fun getUserProfile(userId: String, onResult: (Result<User?>) -> Unit) {
        if (userId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        get(userId, onResult)
    }

    fun updateUserProfile(
        userId: String,
        name: String,
        phone: String,
        avatarUrl: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Chưa đăng nhập")))
            return
        }
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "phone" to phone
        )
        if (avatarUrl.isNotBlank()) {
            updates["avatarUrl"] = avatarUrl
        }
        update(userId, updates, onResult)
    }

    fun getUserStats(userId: String, onResult: (Result<UserStats>) -> Unit) {
        if (userId.isBlank()) {
            onResult(Result.success(UserStats()))
            return
        }

        val completedStatuses = listOf(
            BookingStatus.COMPLETED.name,
            BookingStatus.PAID.name,
            BookingStatus.CONFIRMED.name,
            BookingStatus.WAITING.name
        )

        firestore.collection("bookings")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { bSnap ->
                val bookingCount = bSnap.documents.count { doc ->
                    val st = doc.getString("status").orEmpty()
                    completedStatuses.contains(st)
                }.toLong()

                firestore.collection("favorites")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { fSnap ->
                        val favoriteCount = fSnap.size().toLong()

                        firestore.collection("reviews")
                            .whereEqualTo("userId", userId)
                            .get()
                            .addOnSuccessListener { rSnap ->
                                val reviewCount = rSnap.size().toLong()
                                onResult(Result.success(UserStats(bookingCount, favoriteCount, reviewCount)))
                            }
                            .addOnFailureListener {
                                onResult(Result.success(UserStats(bookingCount, favoriteCount, 0L)))
                            }
                    }
                    .addOnFailureListener {
                        onResult(Result.success(UserStats(bookingCount, 0L, 0L)))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi getUserStats: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }
}
