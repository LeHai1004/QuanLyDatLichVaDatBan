package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Review

class ReviewRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Review>(firestore.collection("reviews"), Review::class.java) {

    companion object {
        private const val TAG = "ReviewRepository"

        fun calculateAverageRating(reviews: List<Review>): Double {
            if (reviews.isEmpty()) return 0.0
            val sum = reviews.sumOf { it.rating }
            val avg = sum.toDouble() / reviews.size
            return Math.round(avg * 10.0) / 10.0
        }

        fun filterReviewsByStar(reviews: List<Review>, star: Int?): List<Review> {
            if (star == null || star !in 1..5) return reviews
            return reviews.filter { it.rating.toInt() == star }
        }
    }

    /**
     * Gửi đánh giá cho đơn đặt bàn:
     * - Chỉ áp dụng cho đơn có trạng thái PAID
     * - Lưu bản ghi review mới vào collection reviews
     * - Cập nhật trạng thái đơn đặt bàn thành COMPLETED
     * - Tự động đồng bộ và cập nhật điểm rating trung bình của nhà hàng
     */
    fun createReview(
        review: Review,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (review.bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }
        if (review.rating !in 1..5) {
            onResult(Result.failure(IllegalArgumentException("Số sao đánh giá phải từ 1 đến 5")))
            return
        }

        val reviewId = review.id.ifEmpty { "REV_${review.bookingId}" }
        val reviewRef = collection.document(reviewId)
        val bookingRef = firestore.collection("bookings").document(review.bookingId)

        var resolvedRestaurantId = review.restaurantId

        firestore.runTransaction { transaction ->
            val bookingSnap = transaction.get(bookingRef)
            if (!bookingSnap.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #${review.bookingId}")
            }

            val currentStatus = bookingSnap.getString("status") ?: ""
            if (currentStatus == BookingStatus.COMPLETED.name) {
                throw IllegalStateException("ALREADY_COMPLETED")
            }
            if (currentStatus != BookingStatus.PAID.name) {
                throw IllegalStateException("NOT_PAID")
            }

            val reviewSnap = transaction.get(reviewRef)
            if (reviewSnap.exists()) {
                throw IllegalStateException("ALREADY_REVIEWED")
            }

            val now = System.currentTimeMillis()
            if (resolvedRestaurantId.isBlank()) {
                resolvedRestaurantId = bookingSnap.getString("restaurantId").orEmpty()
            }

            val finalReview = review.copy(
                id = reviewId,
                restaurantId = resolvedRestaurantId,
                createdAt = if (review.createdAt > 0) review.createdAt else now
            )

            transaction.set(reviewRef, finalReview)
            transaction.update(bookingRef, "status", BookingStatus.COMPLETED.name)
            null
        }.addOnSuccessListener {
            Log.i(TAG, "Đã lưu đánh giá cho đơn #${review.bookingId}")
            if (resolvedRestaurantId.isNotBlank()) {
                syncRestaurantRating(resolvedRestaurantId)
            }
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi tạo đánh giá: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Lắng nghe realtime danh sách đánh giá của quán, sắp xếp mới nhất lên đầu
     */
    fun listenReviewsForRestaurant(
        restaurantId: String,
        onReviewsChanged: (Result<List<Review>>) -> Unit
    ): ListenerRegistration {
        if (restaurantId.isBlank()) {
            onReviewsChanged(Result.success(emptyList()))
            return ListenerRegistration { }
        }

        return collection.whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi lắng nghe đánh giá quán $restaurantId: ${error.message}", error)
                    onReviewsChanged(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val reviews = snapshot.toObjects(Review::class.java)
                    val sorted = reviews.sortedByDescending { it.createdAt }
                    onReviewsChanged(Result.success(sorted))
                } else {
                    onReviewsChanged(Result.success(emptyList()))
                }
            }
    }

    /**
     * Tính toán và đồng bộ lại trường rating của nhà hàng trên Firestore
     */
    fun syncRestaurantRating(
        restaurantId: String,
        onResult: ((Result<Double>) -> Unit)? = null
    ) {
        if (restaurantId.isBlank()) {
            onResult?.invoke(Result.failure(IllegalArgumentException("restaurantId không được để trống")))
            return
        }

        collection.whereEqualTo("restaurantId", restaurantId).get()
            .addOnSuccessListener { snap ->
                val reviews = snap.toObjects(Review::class.java)
                val avg = calculateAverageRating(reviews)
                firestore.collection("restaurants").document(restaurantId)
                    .update("rating", avg)
                    .addOnSuccessListener {
                        Log.i(TAG, "Đã đồng bộ rating cho quán $restaurantId: $avg")
                        onResult?.invoke(Result.success(avg))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi cập nhật rating quán $restaurantId: ${error.message}", error)
                        onResult?.invoke(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi lấy danh sách review để tính rating: ${error.message}", error)
                onResult?.invoke(Result.failure(error))
            }
    }

    fun getReviewForBooking(
        bookingId: String,
        onResult: (Result<Review?>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        val reviewId = "REV_$bookingId"
        collection.document(reviewId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onResult(Result.success(snap.toObject(Review::class.java)))
                } else {
                    collection.whereEqualTo("bookingId", bookingId).limit(1).get()
                        .addOnSuccessListener { querySnap ->
                            val r = querySnap.documents.firstOrNull()?.toObject(Review::class.java)
                            onResult(Result.success(r))
                        }
                        .addOnFailureListener { error ->
                            onResult(Result.failure(error))
                        }
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
